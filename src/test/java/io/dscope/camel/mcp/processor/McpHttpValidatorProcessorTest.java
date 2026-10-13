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
    void validatesLocalOriginsAsUrisAndPreservesLocalPortSupport() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "server/discover", Map.of()));
            setRequiredHeaders(exchange, "2026-07-28", "server/discover");
            McpHttpValidatorProcessor validator = new McpHttpValidatorProcessor("http://localhost");
            exchange.getIn().setHeader("Origin", "http://localhost:8080");
            validator.process(exchange);
            for (String origin : new String[] { "http://localhost:8080@attacker.example",
                    "http://localhost:8080/path", "http://localhost:8080?query", "http://localhost.evil" }) {
                exchange.getIn().setHeader("Origin", origin);
                assertEquals(403, assertThrows(McpProtocolException.class,
                        () -> validator.process(exchange)).getHttpStatus());
            }
        }
    }

    @Test
    void preservesWhitespaceInPromptNames() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            String name = " summary ";
            exchange.getIn().setBody(modernRequest("2026-07-28", "prompts/get", Map.of("name", name)));
            setRequiredHeaders(exchange, "2026-07-28", "prompts/get");
            exchange.getIn().setHeader("Mcp-Name", McpHttpValidatorProcessor.encodeHeaderValue(name));
            new McpHttpValidatorProcessor().process(exchange);
            exchange.getIn().setHeader("Mcp-Name", "summary");
            assertThrows(McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));
        }
    }

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
    void acceptsConfiguredHttpOrigin() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "server/discover", Map.of()));
            setRequiredHeaders(exchange, "2026-07-28", "server/discover");
            exchange.getIn().setHeader("Origin", "https://agent.example:443");

            new McpHttpValidatorProcessor("https://agent.example").process(exchange);

            assertEquals("2026-07-28", exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION));
        }
    }

    @Test
    void validatesPromptNameAgainstMcpNameHeader() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "prompts/get", Map.of("name", "summary")));
            setRequiredHeaders(exchange, "2026-07-28", "prompts/get");
            exchange.getIn().setHeader("Mcp-Name", "summary");

            new McpHttpValidatorProcessor().process(exchange);

            assertEquals("2026-07-28", exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION));
        }
    }

    @Test
    void rejectsUnconfiguredHttpOrigin() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(modernRequest("2026-07-28", "server/discover", Map.of()));
            setRequiredHeaders(exchange, "2026-07-28", "server/discover");
            exchange.getIn().setHeader("Origin", "https://attacker.example");

            McpProtocolException exception = assertThrows(
                    McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));

            assertEquals(403, exception.getHttpStatus());
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
    void requiresMcpNameForNameBearingMethods() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "id", "request-1",
                    "method", "tools/call",
                    "params", Map.of("_meta", Map.of(
                            McpHttpValidatorProcessor.META_PROTOCOL_VERSION, "2026-07-28",
                            McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES, Map.of()))));
            setRequiredHeaders(exchange, "2026-07-28", "tools/call");

            McpProtocolException exception = assertThrows(
                    McpProtocolException.class, () -> new McpHttpValidatorProcessor().process(exchange));

            assertEquals(-32602, exception.getCode());
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
