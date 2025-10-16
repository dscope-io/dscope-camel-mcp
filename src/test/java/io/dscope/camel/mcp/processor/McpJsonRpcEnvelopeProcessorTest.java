package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class McpJsonRpcEnvelopeProcessorTest {

    @Test
    void shouldHandleInitializeRequest() throws Exception {
        McpJsonRpcEnvelopeProcessor processor = new McpJsonRpcEnvelopeProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            Map<String, Object> params = Map.of("clientInfo", Map.of("name", "test", "version", "1.0"));
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "id", "init-1",
                    "method", "initialize",
                    "params", params));

            processor.process(exchange);

            assertEquals("REQUEST", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE));
            assertEquals("initialize", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD));
            assertEquals("init-1", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID));
            assertSame(params, exchange.getIn().getBody());
        }
    }

    @Test
    void shouldDefaultPingParamsToEmptyMap() throws Exception {
        McpJsonRpcEnvelopeProcessor processor = new McpJsonRpcEnvelopeProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "method", "ping"));

            processor.process(exchange);

            assertEquals("NOTIFICATION", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE));
            assertEquals("ping", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD));
            assertTrue(exchange.getIn().getBody() instanceof Map);
            assertTrue(((Map<?, ?>) exchange.getIn().getBody()).isEmpty());
        }
    }

    @Test
    void shouldRejectUnknownMethod() throws Exception {
        McpJsonRpcEnvelopeProcessor processor = new McpJsonRpcEnvelopeProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "method", "invalid"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> processor.process(exchange));
            assertTrue(ex.getMessage().contains("Unsupported MCP method"));
        }
    }

    @Test
    void shouldHandleNotificationsInitialized() throws Exception {
        McpJsonRpcEnvelopeProcessor processor = new McpJsonRpcEnvelopeProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            Map<String, Object> params = Map.of("sessionId", "abc123");
            exchange.getIn().setBody(Map.of(
                    "jsonrpc", "2.0",
                    "method", "notifications/initialized",
                    "params", params));

            processor.process(exchange);

            assertEquals("NOTIFICATION", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE));
            assertEquals("notifications/initialized", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD));
            assertSame(params, exchange.getIn().getBody());
        }
    }
}
