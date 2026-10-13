package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpProtocolSupportTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        context.close();
    }

    @Test
    void decoratesModernCacheableCompleteResultWithPrivateNoCacheDefaults() {
        Exchange exchange = modernExchange("resources/read");

        Map<String, Object> result = McpProtocolSupport.decorateResult(exchange, Map.of("contents", java.util.List.of()));

        assertEquals("complete", result.get("resultType"));
        assertEquals(0, result.get("ttlMs"));
        assertEquals("private", result.get("cacheScope"));
        assertEquals(McpProtocolSupport.serverInfo(), result.get("_meta")
                instanceof Map<?, ?> meta ? meta.get("io.modelcontextprotocol/serverInfo") : null);
    }

    @Test
    void preservesExplicitCacheHints() {
        Exchange exchange = modernExchange("tools/list");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tools", java.util.List.of());
        response.put("ttlMs", 300_000);
        response.put("cacheScope", "public");

        Map<String, Object> result = McpProtocolSupport.decorateResult(exchange, response);

        assertEquals(300_000, result.get("ttlMs"));
        assertEquals("public", result.get("cacheScope"));
    }

    @Test
    void doesNotAddCacheHintsToInputRequiredResults() {
        Exchange exchange = modernExchange("tools/call");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_REQUEST_META, Map.of(
                McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES, Map.of("elicitation", Map.of())));

        Map<String, Object> result = McpProtocolSupport.decorateResult(exchange, Map.of(
                "resultType", "input_required",
                "inputRequests", Map.of("approval", Map.of(
                        "method", "elicitation/create",
                        "params", Map.of("message", "Approve the operation"))),
                "ttlMs", 5000,
                "cacheScope", "public"));

        assertEquals("input_required", result.get("resultType"));
        assertFalse(result.containsKey("ttlMs"));
        assertFalse(result.containsKey("cacheScope"));
    }

    @Test
    void rejectsInputRequestWhenClientDidNotDeclareCapability() {
        Exchange exchange = modernExchange("tools/call");

        assertThrows(IllegalStateException.class,
                () -> McpProtocolSupport.decorateResult(exchange, Map.of(
                        "resultType", "input_required",
                        "inputRequests", Map.of("approval", Map.of(
                                "method", "elicitation/create",
                                "params", Map.of("message", "Approve the operation"))))));
    }

    @Test
    void rejectsInputRequestForUnsupportedClientElicitationMode() {
        Exchange exchange = modernExchange("tools/call");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_REQUEST_META, Map.of(
                McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES,
                Map.of("elicitation", Map.of("form", Map.of()))));

        assertThrows(IllegalStateException.class,
                () -> McpProtocolSupport.decorateResult(exchange, Map.of(
                        "resultType", "input_required",
                        "inputRequests", Map.of("authorize", Map.of(
                                "method", "elicitation/create",
                                "params", Map.of("mode", "url", "message", "Authorize access"))))));
    }

    @Test
    void rejectsInputRequiredResultWithoutInputRequestOrRequestState() {
        Exchange exchange = modernExchange("tools/call");

        assertThrows(IllegalStateException.class,
                () -> McpProtocolSupport.decorateResult(exchange, Map.of("resultType", "input_required")));
    }

    @Test
    void rejectsInputRequiredResultForUnsupportedMethod() {
        Exchange exchange = modernExchange("resources/list");

        assertThrows(IllegalArgumentException.class,
                () -> McpProtocolSupport.decorateResult(exchange, Map.of(
                        "resultType", "input_required",
                        "requestState", "opaque")));
    }

    @Test
    void rejectsInvalidCacheHints() {
        Exchange exchange = modernExchange("tools/list");

        assertThrows(IllegalArgumentException.class,
                () -> McpProtocolSupport.decorateResult(exchange, Map.of(
                        "tools", java.util.List.of(),
                        "ttlMs", -1)));
    }

    @Test
    void doesNotDecorateLegacyResults() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(
                McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION,
                McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION);

        Map<String, Object> result = McpProtocolSupport.decorateResult(exchange, Map.of("tools", java.util.List.of()));

        assertFalse(result.containsKey("resultType"));
        assertFalse(result.containsKey("ttlMs"));
        assertFalse(result.containsKey("_meta"));
    }

    @Test
    void preservesLegacyJsonRpcResponsesWithNonObjectResults() {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(
                McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION,
                McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION);

        Map<String, Object> response = McpProtocolSupport.decorateJsonRpcResponse(
                exchange, Map.of("jsonrpc", "2.0", "id", "legacy-1", "result", "legacy"));

        assertEquals("legacy", response.get("result"));
    }

    @Test
    void omitsNullErrorFieldFromModernSuccessfulResponse() {
        Exchange exchange = modernExchange("tools/list");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", "modern-1");
        response.put("result", Map.of("tools", java.util.List.of()));
        response.put("error", null);

        Map<String, Object> decorated = McpProtocolSupport.decorateJsonRpcResponse(exchange, response);

        assertFalse(decorated.containsKey("error"));
        assertEquals("complete", ((Map<?, ?>) decorated.get("result")).get("resultType"));
    }

    private Exchange modernExchange(String method) {
        Exchange exchange = new DefaultExchange(context);
        exchange.setProperty(
                McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION,
                McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, method);
        return exchange;
    }
}
