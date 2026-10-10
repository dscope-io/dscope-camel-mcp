package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpHttpValidatorProcessorTest {

    @Test
    void acceptsModernRequestWithMatchingMetadataAndHeaders() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "tools/call", Map.of("name", "lookup")));
            exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
            exchange.getIn().setHeader("Accept", "application/json, text/event-stream");
            exchange.getIn().setHeader("Content-Type", "application/json");
            exchange.getIn().setHeader("MCP-Protocol-Version", "2026-07-28");
            exchange.getIn().setHeader("Mcp-Method", "tools/call");
            exchange.getIn().setHeader("Mcp-Name", "lookup");

            new McpHttpValidatorProcessor().process(exchange);

            assertEquals("2026-07-28", exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION));
            assertEquals(Boolean.TRUE, exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_IS_MODERN_PROTOCOL));
        }
    }

    @Test
    void decodesBase64EncodedMcpNameBeforeComparingWithRequestBody() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            String toolName = "検索";
            exchange.getIn().setBody(modernRequest("2026-07-28", "tools/call", Map.of("name", toolName)));
            setRequiredHeaders(exchange, "2026-07-28", "tools/call");
            exchange.getIn().setHeader("Mcp-Name", McpHttpValidatorProcessor.encodeHeaderValue(toolName));

            new McpHttpValidatorProcessor().process(exchange);

            assertEquals("2026-07-28", exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION));
        }
    }

    @Test
    void preservesWhitespaceInBase64EncodedMcpNameWhenComparingWithRequestBody() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            String toolName = " lookup ";
            exchange.getIn().setBody(modernRequest("2026-07-28", "tools/call", Map.of("name", toolName)));
            setRequiredHeaders(exchange, "2026-07-28", "tools/call");
            exchange.getIn().setHeader("Mcp-Name", McpHttpValidatorProcessor.encodeHeaderValue(toolName));

            new McpHttpValidatorProcessor().process(exchange);

            assertEquals("2026-07-28", exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION));
        }
    }

    @Test
    void rejectsProtocolVersionHeaderMismatch() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "server/discover", Map.of()));
            setRequiredHeaders(exchange, "2025-06-18", "server/discover");

            McpProtocolException exception = assertThrows(
                    McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));

            assertEquals(-32020, exception.getCode());
            assertEquals("2026-07-28", exception.getData().get("expected"));
            assertEquals("2025-06-18", exception.getData().get("actual"));
        }
    }

    @Test
    void reportsUnsupportedProtocolVersionWithSupportedAndRequestedVersions() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2099-01-01", "server/discover", Map.of()));
            setRequiredHeaders(exchange, "2099-01-01", "server/discover");

            McpProtocolException exception = assertThrows(
                    McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));

            assertEquals(-32022, exception.getCode());
            assertEquals("2099-01-01", exception.getData().get("requested"));
            assertEquals(java.util.List.of("2025-06-18", "2026-07-28"), exception.getData().get("supported"));
        }
    }

    @Test
    void requiresClientCapabilitiesInModernRequestMetadata() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "id", "discover-1",
                    "method", "server/discover",
                    "params", Map.of("_meta", Map.of(
                            McpHttpValidatorProcessor.META_PROTOCOL_VERSION, "2026-07-28"))));
            setRequiredHeaders(exchange, "2026-07-28", "server/discover");

            McpProtocolException exception = assertThrows(
                    McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));

            assertEquals(-32602, exception.getCode());
            assertEquals(400, exception.getHttpStatus());
        }
    }

    private Map<String, Object> modernRequest(String version, String method, Map<String, Object> params) {
        return Map.of(
                "jsonrpc", "2.0",
                "id", "request-1",
                "method", method,
                "params", Map.of(
                        "name", params.getOrDefault("name", "lookup"),
                        "_meta", Map.of(
                                McpHttpValidatorProcessor.META_PROTOCOL_VERSION, version,
                                McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES, Map.of())));
    }

    private void setRequiredHeaders(Exchange exchange, String version, String method) {
        exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
        exchange.getIn().setHeader("Accept", "application/json, text/event-stream");
        exchange.getIn().setHeader("Content-Type", "application/json");
        exchange.getIn().setHeader("MCP-Protocol-Version", version);
        exchange.getIn().setHeader("Mcp-Method", method);
    }
}
