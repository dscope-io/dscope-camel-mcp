package io.dscope.camel.mcp.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class McpStreamProcessorTest {

    @Test
    void returnsServerSentEventsHandshake() {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);

        McpStreamProcessor processor = new McpStreamProcessor();
        processor.process(exchange);

        assertEquals(200, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("text/event-stream", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("no-store", exchange.getIn().getHeader("Cache-Control"));
        assertEquals("keep-alive", exchange.getIn().getHeader("Connection"));
        assertEquals(":ok\n\n", exchange.getIn().getBody(String.class));
    }
}
