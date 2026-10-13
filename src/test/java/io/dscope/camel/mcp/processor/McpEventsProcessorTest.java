package io.dscope.camel.mcp.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.events.McpEventsProvider;
import io.dscope.camel.mcp.events.McpCallbackException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpEventsProcessorTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SECRET = "whsec_" + Base64.getEncoder().encodeToString(new byte[32]);

    private static class Provider implements McpEventsProvider {
        Map<String, Object> captured;
        Exchange capturedExchange;
        int unsubscribed;
        public Map<String, Object> list(Exchange e, Map<String,Object> p) {
            captured = p;
            capturedExchange = e;
            return Map.of("events", List.of(Map.of("name", "comment.created", "delivery", List.of("webhook"),
                    "inputSchema", Map.of("type", "object"), "payloadSchema", Map.of("type", "object"))), "nextCursor", "page2");
        }
        public Map<String, Object> subscribe(Exchange e, Map<String,Object> p) {
            captured = p;
            capturedExchange = e;
            Map<String,Object> result = new LinkedHashMap<>();
            result.put("id", "sub_1");
            result.put("refreshBefore", "2026-10-13T12:00:00Z");
            result.put("cursor", null);
            result.put("truncated", false);
            return result;
        }
        public void unsubscribe(Exchange e, Map<String,Object> p) { captured = p; unsubscribed++; }
    }

    private Map<String,Object> subscription(boolean secret) {
        var delivery = new LinkedHashMap<String,Object>();
        delivery.put("mode", "webhook");
        delivery.put("url", "https://receiver.example/callback");
        if (secret) delivery.put("secret", SECRET);
        return new LinkedHashMap<>(Map.of("name", "comment.created", "arguments", Map.of("document_id", "doc_1"), "delivery", delivery));
    }

    private Exchange request(DefaultCamelContext context, String method, Map<String,Object> params) throws Exception {
        var e = new DefaultExchange(context);
        e.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2026-07-28");
        e.getIn().setHeader("Authorization", "Bearer application-owned");
        e.getIn().setBody(Map.of("jsonrpc", "2.0", "id", "request-1", "method", method, "params", params));
        new McpJsonRpcEnvelopeProcessor().process(e);
        assertSame(params, e.getIn().getBody());
        return e;
    }

    @Test void parsesAndDispatchesFullLifecycleWithModernMetadata() throws Exception {
        try (var ctx = new DefaultCamelContext()) {
            var provider = new Provider();
            var processor = new McpEventsProcessor(provider);
            var listParams = Map.<String,Object>of("cursor", "page1");
            var list = request(ctx, "events/list", listParams);
            processor.process(list);
            assertSame(listParams, provider.captured);
            assertSame(list, provider.capturedExchange);
            assertEquals("Bearer application-owned", provider.capturedExchange.getIn().getHeader("Authorization"));
            var result = JSON.readTree(list.getIn().getBody(String.class)).path("result");
            assertEquals("page2", result.path("nextCursor").asText());
            assertEquals("complete", result.path("resultType").asText());
            assertEquals(0, result.path("ttlMs").asInt());
            assertEquals("private", result.path("cacheScope").asText());
            for (boolean ttlPresent : new boolean[]{false, true}) {
                var params = subscription(true);
                params.put("cursor", null);
                if (ttlPresent) params.put("ttlMs", null);
                var subscribe = request(ctx, "events/subscribe", params);
                processor.process(subscribe);
                assertSame(params, provider.captured);
                assertEquals(ttlPresent, provider.captured.containsKey("ttlMs"));
                var envelope = JSON.readTree(subscribe.getIn().getBody(String.class));
                assertEquals("request-1", envelope.path("id").asText());
                assertEquals("sub_1", envelope.path("result").path("id").asText());
                assertTrue(envelope.path("result").path("cursor").isNull());
                assertFalse(envelope.path("result").has("ttlMs"));
            }
            for (int i = 0; i < 2; i++) {
                var unsubscribe = request(ctx, "events/unsubscribe", subscription(false));
                processor.process(unsubscribe);
                assertEquals("complete", JSON.readTree(unsubscribe.getIn().getBody(String.class)).path("result").path("resultType").asText());
            }
            assertEquals(2, provider.unsubscribed);
        }
    }

    @Test void invalidRequestsNeverReachProvider() throws Exception {
        try (var ctx = new DefaultCamelContext()) {
            var provider = new Provider();
            var processor = new McpEventsProcessor(provider);
            for (var invalid : List.of(
                    Map.of("name", 42), Map.of("arguments", "bad"), Map.of("ttlMs", -1),
                    Map.of("ttlMs", 1.5), Map.of("cursor", 42), Map.of("delivery", Map.of("mode", "poll")),
                    Map.of("delivery", Map.of("mode", "webhook", "url", "http://receiver.example", "secret", SECRET)),
                    Map.of("delivery", Map.of("mode", "webhook", "url", "https://receiver.example", "secret", "bad")))) {
                var params = subscription(true);
                params.putAll(invalid);
                var exchange = request(ctx, "events/subscribe", params);
                var error = assertThrows(McpProtocolException.class, () -> processor.process(exchange));
                assertEquals(-32602, error.getCode());
                assertNull(provider.captured);
            }
        }
    }

    @Test void requiresModernProtocolAndPropagatesProviderFailures() throws Exception {
        try (var ctx = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(ctx);
            exchange.getIn().setBody(Map.of("jsonrpc", "2.0", "id", 1, "method", "events/list"));
            assertEquals(-32601, assertThrows(McpProtocolException.class,
                    () -> new McpJsonRpcEnvelopeProcessor().process(exchange)).getCode());
            var modern = request(ctx, "events/subscribe", subscription(true));
            var provider = new Provider() {
                @Override public Map<String,Object> subscribe(Exchange e, Map<String,Object> p) {
                    throw new McpCallbackException("challenge_failed");
                }
            };
            assertEquals("challenge_failed", assertThrows(McpCallbackException.class,
                    () -> new McpEventsProcessor(provider).process(modern)).getReason());
        }
    }

    @Test void discoveryCanBeEnabledInAnyApplicationRoute() throws Exception {
        try (var ctx = new DefaultCamelContext()) {
            for (boolean enabled : new boolean[]{false, true}) {
                var e = request(ctx, "server/discover", Map.of());
                new McpServerDiscoverProcessor(enabled).process(e);
                assertEquals(enabled, JSON.readTree(e.getIn().getBody(String.class)).path("result").path("capabilities").has("events"));
            }
        }
    }
}
