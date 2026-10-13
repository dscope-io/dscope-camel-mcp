package io.dscope.camel.mcp.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Signed, bounded HTTPS callbacks. Each attempt resolves once and pins validated addresses. */
public final class McpWebhookClient {
    public static final int MAX_BODY_BYTES = 256 * 1024;
    public static final int TIMEOUT_SECONDS = 10;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Transport transport;
    private final Resolver resolver;
    private final Clock clock;

    /** Test transports must connect only to pinned addresses and never follow redirects. */
    @FunctionalInterface
    public interface Transport {
        Response post(URI url, InetAddress[] pinnedAddresses, Map<String, String> headers, byte[] body) throws Exception;
    }

    @FunctionalInterface
    public interface Resolver {
        InetAddress[] resolve(String hostname) throws Exception;
    }

    public record Response(int status, byte[] body) {
        public Response {
            body = body == null ? new byte[0] : body.clone();
        }
        @Override public byte[] body() { return body.clone(); }
    }

    public McpWebhookClient() {
        this(new McpPinnedHttpsTransport(), InetAddress::getAllByName, Clock.systemUTC());
    }

    public McpWebhookClient(Transport transport, Resolver resolver, Clock clock) {
        this.transport = Objects.requireNonNull(transport);
        this.resolver = Objects.requireNonNull(resolver);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Fresh single-use challenge; caller may cache success by principal and URL for a bounded period. */
    public void verify(String url, String subscriptionId, String secret) {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        byte[] body = serialize(Map.of("type", "verification", "challenge", challenge));
        Response response = post(url, subscriptionId, secret, null, "msg_verification_" + UUID.randomUUID(), body);
        if (response.status() < 200 || response.status() >= 300) {
            throw new McpCallbackException("http_error");
        }
        try {
            var json = JSON.readTree(response.body());
            Object echoed = json != null && json.path("challenge").isTextual()
                    ? json.path("challenge").textValue() : null;
            if (!(echoed instanceof String value) || !MessageDigest.isEqual(
                    challenge.getBytes(StandardCharsets.UTF_8), value.getBytes(StandardCharsets.UTF_8))) {
                throw new McpCallbackException("challenge_failed");
            }
        } catch (McpCallbackException e) {
            throw e;
        } catch (Exception e) {
            throw new McpCallbackException("challenge_failed");
        }
    }

    /**
     * Send one event; preserve eventId across retries. An optional previous secret
     * adds a second Standard Webhooks signature during the application's rotation
     * window. Returns HTTP status (including 410/413); application owns retry policy.
     */
    public int send(String url, String subscriptionId, String secret, String previousSecret, Map<String, Object> event) {
        Objects.requireNonNull(event, "event");
        String id = requiredString(event.get("eventId"));
        requiredString(event.get("name"));
        String timestamp = requiredString(event.get("timestamp"));
        try {
            OffsetDateTime.parse(timestamp);
        } catch (Exception e) {
            throw new IllegalArgumentException("Event timestamp must be ISO 8601 with a timezone");
        }
        if (!(event.get("data") instanceof Map<?, ?>) || event.containsKey("type")) {
            throw new IllegalArgumentException("Event requires object data and must not contain control type");
        }
        return post(url, subscriptionId, secret, previousSecret, id, serialize(event)).status();
    }

    private Response post(String url, String subscriptionId, String secret, String previousSecret, String id, byte[] body) {
        URI uri;
        try {
            uri = URI.create(url);
            validateUrl(uri);
        } catch (Exception e) {
            throw new McpCallbackException("invalid_url");
        }
        requiredString(subscriptionId);
        requiredString(id);
        if (body.length > MAX_BODY_BYTES) throw new McpCallbackException("payload_too_large");
        String timestamp = Long.toString(clock.instant().getEpochSecond());
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("webhook-id", id);
        headers.put("webhook-timestamp", timestamp);
        String signature = sign(secret, id, timestamp, body);
        if (previousSecret != null) signature += " " + sign(previousSecret, id, timestamp, body);
        headers.put("webhook-signature", signature);
        headers.put("X-MCP-Subscription-Id", subscriptionId);
        FutureTask<Response> task = new FutureTask<>(() -> {
            InetAddress[] addresses;
            try {
                addresses = resolver.resolve(hostname(uri));
            } catch (Exception e) {
                throw new McpCallbackException("dns_error");
            }
            if (addresses == null || addresses.length == 0) throw new McpCallbackException("dns_error");
            addresses = addresses.clone();
            for (InetAddress address : addresses) {
                if (address == null || !isPublicAddress(address)) throw new McpCallbackException("address_blocked");
            }
            if (Thread.currentThread().isInterrupted()) throw new McpCallbackException("timeout");
            return transport.post(uri, addresses, Map.copyOf(headers), body);
        });
        Thread.ofVirtual().start(task);
        try {
            Response response = task.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (response.body().length > MAX_BODY_BYTES) throw new McpCallbackException("payload_too_large");
            if (response.status() >= 300 && response.status() < 400) throw new McpCallbackException("redirect");
            return response;
        } catch (TimeoutException e) {
            task.cancel(true);
            throw new McpCallbackException("timeout");
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new McpCallbackException("timeout");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof McpCallbackException callback) throw callback;
            if (e.getCause() instanceof java.net.SocketTimeoutException) throw new McpCallbackException("timeout");
            throw new McpCallbackException("transport_error");
        }
    }

    public static void validateUrl(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getHost().contains("%")) {
            throw new IllegalArgumentException("An absolute HTTPS URL without credentials/fragment is required");
        }
    }

    static String hostname(URI uri) {
        String host = uri.getHost();
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    public static byte[] decodeSecret(String secret) {
        if (secret == null || !secret.startsWith("whsec_")) throw new IllegalArgumentException("Invalid signing secret");
        byte[] key;
        try { key = Base64.getDecoder().decode(secret.substring(6)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid signing secret"); }
        if (key.length < 24 || key.length > 64) throw new IllegalArgumentException("Invalid signing secret length");
        return key;
    }

    public static String sign(String secret, String eventId, String timestamp, byte[] exactBody) {
        byte[] key = decodeSecret(secret);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            mac.update((requiredString(eventId) + "." + requiredString(timestamp) + ".").getBytes(StandardCharsets.UTF_8));
            return "v1," + Base64.getEncoder().encodeToString(mac.doFinal(exactBody));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC SHA256 unavailable", e);
        }
    }

    /** Conservative global-unicast allowlist, including IPv4 embedded in IPv6. */
    public static boolean isPublicAddress(InetAddress address) {
        byte[] b = address.getAddress();
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        if (b.length == 4) {
            int a = b[0] & 255, c = b[1] & 255, d = b[2] & 255;
            return a != 0 && a != 10 && a != 127 && a < 224
                    && !(a == 100 && c >= 64 && c <= 127)
                    && !(a == 169 && c == 254) && !(a == 172 && c >= 16 && c <= 31)
                    && !(a == 192 && (c == 168 || c == 0 || (c == 88 && d == 99)))
                    && !(a == 198 && (c == 18 || c == 19 || (c == 51 && d == 100)))
                    && !(a == 203 && c == 0 && d == 113);
        }
        // Only 2000::/3; exclude special-purpose 2001::/23, documentation,
        // 6to4 (embeds IPv4), and the 3fff::/20 documentation range.
        return b.length == 16 && (b[0] & 0xe0) == 0x20
                && !((b[0] & 255) == 0x20 && (b[1] & 255) == 1 && (b[2] & 255) < 2)
                && !((b[0] & 255) == 0x20 && (b[1] & 255) == 1 && (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)
                && !((b[0] & 255) == 0x20 && (b[1] & 255) == 2)
                && !((b[0] & 255) == 0x3f && (b[1] & 255) == 0xff && (b[2] & 0xf0) == 0);
    }

    private static String requiredString(Object value) {
        if (!(value instanceof String text) || text.isBlank() || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("A non-empty string without line breaks is required");
        }
        return text;
    }

    private static byte[] serialize(Object value) {
        try { return JSON.writeValueAsBytes(value); }
        catch (Exception e) { throw new IllegalArgumentException("Unable to serialize webhook body"); }
    }
}
