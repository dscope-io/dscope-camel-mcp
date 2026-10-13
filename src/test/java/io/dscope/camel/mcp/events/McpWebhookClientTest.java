package io.dscope.camel.mcp.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpWebhookClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String URL = "https://receiver.example/callback?token=opaque";
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    private McpWebhookClient client(McpWebhookClient.Transport transport) {
        return new McpWebhookClient(transport, host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")}, CLOCK);
    }

    private Map<String, Object> event() {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventId", "evt_123");
        event.put("name", "comment.created");
        event.put("timestamp", "2026-10-12T11:59:00Z");
        event.put("data", Map.of("text", "Unicode café ☃"));
        event.put("cursor", null);
        return event;
    }

    @Test void signsExactWireBytesAndBothRotationKeys() throws Exception {
        String previous = "whsec_" + Base64.getEncoder().encodeToString("previous-signing-key-24bytes".getBytes(StandardCharsets.UTF_8));
        var client = client((url, addresses, headers, body) -> {
            assertEquals(URL, url.toString());
            assertEquals("8.8.8.8", addresses[0].getHostAddress());
            assertArrayEquals(JSON.writeValueAsBytes(event()), body);
            assertEquals("evt_123", headers.get("webhook-id"));
            assertEquals("application/json", headers.get("Content-Type"));
            assertEquals("sub_1", headers.get("X-MCP-Subscription-Id"));
            String time = Long.toString(CLOCK.instant().getEpochSecond());
            assertEquals(time, headers.get("webhook-timestamp"));
            String expected = independentSignature(SECRET, "evt_123", time, body) + " "
                    + independentSignature(previous, "evt_123", time, body);
            assertEquals(expected, headers.get("webhook-signature"));
            return new McpWebhookClient.Response(202, null);
        });
        assertEquals(202, client.send(URL, "sub_1", SECRET, previous, event()));
    }

    private static String independentSignature(String secret, String id, String timestamp, byte[] body) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(Base64.getDecoder().decode(secret.substring(6)), "HmacSHA256"));
        hmac.update((id + "." + timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "v1," + Base64.getEncoder().encodeToString(hmac.doFinal(body));
    }

    @Test void signedVerificationUsesFreshChallengeAndRequiresEcho() throws Exception {
        java.util.Set<String> challenges = new java.util.HashSet<>();
        var client = client((url, addresses, headers, body) -> {
            var request = JSON.readTree(body);
            assertEquals("verification", request.path("type").asText());
            assertTrue(challenges.add(request.path("challenge").asText()));
            assertTrue(headers.get("webhook-id").startsWith("msg_verification_"));
            assertEquals(independentSignature(SECRET, headers.get("webhook-id"), headers.get("webhook-timestamp"), body),
                    headers.get("webhook-signature"));
            return new McpWebhookClient.Response(200, JSON.writeValueAsBytes(Map.of("challenge", request.path("challenge").asText())));
        });
        client.verify(URL, "sub_1", SECRET);
        client.verify(URL, "sub_1", SECRET);
        assertEquals(2, challenges.size());
        for (String response : new String[]{"{}", "null", "bad JSON", "{\"challenge\":\"wrong\"}", "{\"challenge\":1}"}) {
            assertReason("challenge_failed", () -> client((u, a, h, b) ->
                    new McpWebhookClient.Response(200, response.getBytes(StandardCharsets.UTF_8))).verify(URL, "sub_1", SECRET));
        }
    }

    @Test void blocksNonPublicIpv4AndIpv6BeforeTransport() throws Exception {
        for (String ip : new String[]{"0.0.0.0", "10.1.2.3", "127.0.0.1", "169.254.169.254", "172.31.1.1",
                "192.168.1.1", "100.64.0.1", "192.0.2.1", "198.18.0.1", "198.51.100.1", "203.0.113.1", "224.0.0.1", "240.0.0.1",
                "::", "::1", "fc00::1", "fe80::1", "ff02::1", "::ffff:127.0.0.1", "64:ff9b::7f00:1",
                "2001:db8::1", "2002:7f00:1::1", "3fff::1"}) {
            InetAddress address = InetAddress.getByName(ip);
            assertFalse(McpWebhookClient.isPublicAddress(address), ip);
            var client = new McpWebhookClient((u, a, h, b) -> { fail("Transport called for " + ip); return null; },
                    host -> new InetAddress[]{address}, CLOCK);
            assertReason("address_blocked", () -> client.verify(URL, "sub", SECRET));
            assertReason("address_blocked", () -> client.send(URL, "sub", SECRET, null, event()));
        }
        assertTrue(McpWebhookClient.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
        assertTrue(McpWebhookClient.isPublicAddress(InetAddress.getByName("8.8.8.8")));
    }

    @Test void resolvesEachAttemptAndRejectsMixedAnswersOrRebinding() {
        AtomicInteger resolves = new AtomicInteger(), posts = new AtomicInteger();
        var client = new McpWebhookClient((u, addresses, h, b) -> {
            assertEquals("8.8.8.8", addresses[0].getHostAddress());
            posts.incrementAndGet();
            return new McpWebhookClient.Response(204, null);
        }, host -> new InetAddress[]{InetAddress.getByName(resolves.incrementAndGet() == 1 ? "8.8.8.8" : "127.0.0.1")}, CLOCK);
        assertEquals(204, client.send(URL, "sub", SECRET, null, event()));
        assertReason("address_blocked", () -> client.send(URL, "sub", SECRET, null, event()));
        assertEquals(1, posts.get());
        var mixed = new McpWebhookClient((u,a,h,b) -> { fail(); return null; },
                host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("::1")}, CLOCK);
        assertReason("address_blocked", () -> mixed.verify(URL, "sub", SECRET));
    }

    @Test void publicIpv6LiteralIsResolvedWithoutUriBrackets() {
        String ipv6 = "2606:4700:4700::1111";
        var client = new McpWebhookClient((url, addresses, headers, body) -> {
            assertTrue(McpWebhookClient.isPublicAddress(addresses[0]));
            assertEquals("[" + ipv6 + "]", url.getHost());
            return new McpWebhookClient.Response(204, null);
        }, host -> {
            assertEquals(ipv6, host);
            return new InetAddress[]{InetAddress.getByName(host)};
        }, CLOCK);
        assertEquals(204, client.send("https://[" + ipv6 + "]/callback", "sub", SECRET, null, event()));
    }

    @Test void categoriesAreSafeAndHttpStatusesReachRetryPolicy() {
        assertReason("redirect", () -> client((u,a,h,b) -> new McpWebhookClient.Response(302, null)).verify(URL, "sub", SECRET));
        assertReason("http_error", () -> client((u,a,h,b) -> new McpWebhookClient.Response(500, null)).verify(URL, "sub", SECRET));
        assertReason("transport_error", () -> client((u,a,h,b) -> { throw new java.io.IOException("secret URL"); }).verify(URL, "sub", SECRET));
        assertReason("timeout", () -> client((u,a,h,b) -> { throw new java.net.SocketTimeoutException(); }).verify(URL, "sub", SECRET));
        assertReason("dns_error", () -> new McpWebhookClient((u,a,h,b) -> null,
                host -> { throw new java.net.UnknownHostException(); }, CLOCK).verify(URL, "sub", SECRET));
        for (int status : new int[]{204, 410, 413, 429, 500}) {
            assertEquals(status, client((u,a,h,b) -> new McpWebhookClient.Response(status, null)).send(URL, "sub", SECRET, null, event()));
        }
    }

    @Test void enforcesTenSecondOverallDeadline() {
        long started = System.nanoTime();
        assertReason("timeout", () -> client((u,a,h,b) -> {
            Thread.sleep(30_000);
            return new McpWebhookClient.Response(204, null);
        }).verify(URL, "sub", SECRET));
        long elapsed = System.nanoTime() - started;
        assertTrue(elapsed >= java.time.Duration.ofSeconds(9).toNanos());
        assertTrue(elapsed < java.time.Duration.ofSeconds(15).toNanos());
    }

    @Test void validatesUrlSecretsAndPayloadLimits() {
        for (String url : new String[]{"http://receiver.example", "https://user:pass@receiver.example", "https://receiver.example/#fragment", "https://receiver.example:0/", "https://receiver.example:99999/", "/relative"}) {
            assertReason("invalid_url", () -> client((u,a,h,b) -> { fail(); return null; }).verify(url, "sub", SECRET));
        }
        for (int size : new int[]{0, 23, 65}) {
            assertThrows(IllegalArgumentException.class, () -> McpWebhookClient.decodeSecret("whsec_" + Base64.getEncoder().encodeToString(new byte[size])));
        }
        for (int size : new int[]{24, 64}) {
            assertEquals(size, McpWebhookClient.decodeSecret("whsec_" + Base64.getEncoder().encodeToString(new byte[size])).length);
        }
        assertThrows(IllegalArgumentException.class, () -> McpWebhookClient.decodeSecret("whsec_not-base64!"));
        var oversized = event();
        oversized.put("data", Map.of("text", "x".repeat(McpWebhookClient.MAX_BODY_BYTES)));
        assertReason("payload_too_large", () -> client((u,a,h,b) -> { fail(); return null; }).send(URL, "sub", SECRET, null, oversized));
        assertReason("payload_too_large", () -> client((u,a,h,b) -> new McpWebhookClient.Response(200,
                new byte[McpWebhookClient.MAX_BODY_BYTES + 1])).verify(URL, "sub", SECRET));
    }

    @Test void permitsExactly256KiBOfSerializedUtf8() throws Exception {
        var event = event();
        event.put("data", Map.of("text", ""));
        int overhead = JSON.writeValueAsBytes(event).length;
        event.put("data", Map.of("text", "x".repeat(McpWebhookClient.MAX_BODY_BYTES - overhead)));
        var client = client((u,a,h,body) -> {
            assertEquals(McpWebhookClient.MAX_BODY_BYTES, body.length);
            return new McpWebhookClient.Response(204, null);
        });
        assertEquals(204, client.send(URL, "sub", SECRET, null, event));
        event.put("data", Map.of("text", "x".repeat(McpWebhookClient.MAX_BODY_BYTES - overhead) + "é"));
        assertReason("payload_too_large", () -> client.send(URL, "sub", SECRET, null, event));
    }

    private static void assertReason(String reason, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(McpCallbackException.class, action);
        assertEquals(reason, error.getReason());
        assertEquals(-32015, error.getCode());
        assertEquals(Map.of("reason", reason), error.getData());
        assertEquals("CallbackEndpointError", error.getMessage());
    }
}
