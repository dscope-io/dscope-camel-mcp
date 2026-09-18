package io.dscope.camel.mcp.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.service.McpUiSessionRegistry;

class McpSecurityProcessorTest {

    @Test
    void rejectsUntrustedOriginAndUnsupportedProtocolVersion() throws Exception {
        McpHttpValidatorProcessor processor = new McpHttpValidatorProcessor("https://trusted.example");
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultExchange exchange = requestExchange(context);
            exchange.getIn().setHeader("Origin", "https://untrusted.example");
            assertThrows(IllegalArgumentException.class, () -> processor.process(exchange));

            DefaultExchange invalidVersion = requestExchange(context);
            invalidVersion.getIn().setHeader("Origin", "https://trusted.example");
            invalidVersion.getIn().setHeader("MCP-Protocol-Version", "invalid");
            assertThrows(IllegalArgumentException.class, () -> processor.process(invalidVersion));
        }
    }

    @Test
    void keepsArgumentsOutOfCamelHeaders() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultExchange exchange = new DefaultExchange(context);
            exchange.getIn().setBody(Map.of("CamelHttpUri", "http://attacker.invalid", "name", "value"));

            new McpHeadersRequestProcessor().process(exchange);

            assertFalse(exchange.getIn().getHeaders().containsKey("CamelHttpUri"));
            assertEquals(Map.of("CamelHttpUri", "http://attacker.invalid", "name", "value"),
                    exchange.getProperty(McpHeadersRequestProcessor.EXCHANGE_PROPERTY_ARGUMENTS));
        }
    }

    @Test
    void invalidUiSessionStopsRouteBeforeToolExecution() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiToolsCallProcessor processor = new McpUiToolsCallProcessor(registry);
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            DefaultExchange exchange = new DefaultExchange(context);
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, "unknown");
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TOOL_NAME, "echo");
            exchange.getIn().setBody(Map.of("text", "should not execute"));

            processor.process(exchange);

            assertTrue(exchange.isRouteStop());
        }
    }

    private DefaultExchange requestExchange(DefaultCamelContext context) {
        DefaultExchange exchange = new DefaultExchange(context);
        exchange.getIn().setHeader("Accept", "application/json, text/event-stream");
        exchange.getIn().setHeader("Content-Type", "application/json");
        return exchange;
    }
}
