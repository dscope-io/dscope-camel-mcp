package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class McpNotificationProcessorTest {

    private final McpNotificationProcessor processor = new McpNotificationProcessor();

    @Test
    void shouldPopulateNotificationMetadata() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, "notifications/log");
            Map<String, Object> params = Map.of("level", "info", "message", "hello world");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            assertEquals("log", exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_NOTIFICATION_TYPE));
            assertSame(params, exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_NOTIFICATION_PARAMS,
                    Map.class));
            assertSame(params, exchange.getIn().getBody(Map.class));
        }
    }

    @Test
    void shouldCreateEmptyParamsWhenBodyMissing() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, "notifications/error");

            processor.process(exchange);

            Map<?, ?> params = exchange
                    .getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_NOTIFICATION_PARAMS, Map.class);
            assertEquals(Map.of(), params);
            assertEquals(Map.of(), exchange.getIn().getBody(Map.class));
        }
    }

    @Test
    void shouldRejectNonNotificationMethod() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, "initialize");

            assertThrows(IllegalArgumentException.class, () -> processor.process(exchange));
        }
    }

    @Test
    void shouldRejectNullExchange() {
        assertThrows(IllegalArgumentException.class, () -> processor.process(null));
    }
}
