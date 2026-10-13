package io.dscope.camel.mcp.events;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

/** Fresh direct connection per attempt; original hostname remains the TLS peer/SNI. */
final class McpPinnedHttpsTransport implements McpWebhookClient.Transport {
    @Override
    public McpWebhookClient.Response post(URI url, InetAddress[] pinned, Map<String, String> headers, byte[] body)
            throws Exception {
        DnsResolver resolver = new DnsResolver() {
            public InetAddress[] resolve(String host) throws UnknownHostException {
                String normalized = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
                if (!normalized.equalsIgnoreCase(McpWebhookClient.hostname(url))) throw new UnknownHostException("Unexpected callback host");
                return pinned.clone();
            }
            public String resolveCanonicalHostname(String host) { return host; }
        };
        Timeout timeout = Timeout.ofSeconds(McpWebhookClient.TIMEOUT_SECONDS);
        try (var manager = PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(resolver)
                .setDefaultConnectionConfig(ConnectionConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build())
                .build();
             var client = HttpClients.custom().setConnectionManager(manager)
                .disableRedirectHandling().disableAutomaticRetries().disableContentCompression().disableCookieManagement()
                .setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(timeout)
                        .setConnectionRequestTimeout(timeout).build()).build()) {
            HttpPost post = new HttpPost(url);
            headers.forEach(post::setHeader);
            post.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
            // Response-handler execute() automatically consumes the entity after
            // returning. Own the open response so terminal statuses never drain it.
            var execution = new java.util.concurrent.FutureTask<McpWebhookClient.Response>(() ->
                    readResponse(post, CloseableHttpResponse.adapt(client.executeOpen(null, post, null))));
            Thread.ofVirtual().start(execution);
            try {
                return execution.get();
            } catch (InterruptedException e) {
                // The client's overall deadline includes DNS. Abort the socket,
                // rather than letting connection fallback continue after timeout.
                post.cancel();
                execution.cancel(true);
                Thread.currentThread().interrupt();
                throw new McpCallbackException("timeout");
            } catch (java.util.concurrent.ExecutionException e) {
                if (e.getCause() instanceof Exception cause) throw cause;
                throw new java.io.IOException("Callback transport failed");
            }
        }
    }

    static McpWebhookClient.Response readResponse(HttpPost post, CloseableHttpResponse response)
            throws java.io.IOException {
        try {
            int status = response.getCode();
            if (status == 410 || status == 413) {
                return new McpWebhookClient.Response(status, null);
            }
            byte[] responseBody = new byte[0];
            if (response.getEntity() != null) {
                try (var input = response.getEntity().getContent()) {
                    responseBody = input.readNBytes(McpWebhookClient.MAX_BODY_BYTES + 1);
                    if (responseBody.length > McpWebhookClient.MAX_BODY_BYTES) {
                        // Abort before close can drain an unbounded entity.
                        post.cancel();
                        throw new McpCallbackException("payload_too_large");
                    }
                }
            }
            return new McpWebhookClient.Response(status, responseBody);
        } finally {
            // Discard the fresh connection without draining or allowing a close
            // failure to replace a terminal HTTP status with a transport error.
            response.close(CloseMode.IMMEDIATE);
        }
    }
}
