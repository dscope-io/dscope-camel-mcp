package io.dscope.camel.mcp.processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class McpRateLimitProcessorTest {

    private void clearProperties() {
        System.clearProperty("mcp.rate.enabled");
        System.clearProperty("mcp.rate.bucketCapacity");
        System.clearProperty("mcp.rate.refillPerSecond");
    }

    @Test
    void enforcesTokenBucketLimit() throws Exception {
        System.setProperty("mcp.rate.enabled", "true");
        System.setProperty("mcp.rate.bucketCapacity", "2");
        System.setProperty("mcp.rate.refillPerSecond", "0");

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        McpRateLimitProcessor processor = new McpRateLimitProcessor();

        try {
            processor.process(exchange);
            assertEquals(1, exchange.getProperty("mcp.rate.tokensRemaining"));

            Exchange second = new DefaultExchange(ctx);
            processor.process(second);
            assertEquals(0, second.getProperty("mcp.rate.tokensRemaining"));

            Exchange third = new DefaultExchange(ctx);
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> processor.process(third));
            assertEquals("Rate limit exceeded: no tokens available (capacity 2)", ex.getMessage());
        } finally {
            clearProperties();
        }
    }

    @Test
    void skipsWhenDisabled() throws Exception {
        System.setProperty("mcp.rate.enabled", "false");

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        McpRateLimitProcessor processor = new McpRateLimitProcessor();

        try {
            processor.process(exchange);
            assertEquals(null, exchange.getProperty("mcp.rate.tokensRemaining"));
        } finally {
            clearProperties();
        }
    }
}
