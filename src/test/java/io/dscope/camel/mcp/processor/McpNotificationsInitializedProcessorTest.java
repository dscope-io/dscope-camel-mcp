package io.dscope.camel.mcp.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class McpNotificationsInitializedProcessorTest {

    @Test
    void shouldReturnNoContentWhenAcknowledging() throws Exception {
        McpNotificationsInitializedProcessor processor = new McpNotificationsInitializedProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.getIn().setBody("payload");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");

            processor.process(exchange);

            assertNull(exchange.getIn().getBody());
            assertEquals(204, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("2025-06-18", exchange.getIn().getHeader("MCP-Protocol-Version"));
            assertEquals("no-store", exchange.getIn().getHeader("Cache-Control"));
        }
    }

    @Test
    void shouldDefaultProtocolVersionWhenMissing() throws Exception {
        McpNotificationsInitializedProcessor processor = new McpNotificationsInitializedProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);

            processor.process(exchange);

            assertEquals(McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION,
                    exchange.getIn().getHeader("MCP-Protocol-Version"));
        }
    }

    @Test
    void shouldRejectNullExchange() {
        McpNotificationsInitializedProcessor processor = new McpNotificationsInitializedProcessor();
        assertThrows(IllegalArgumentException.class, () -> processor.process(null));
    }
}
