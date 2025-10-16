package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class McpHealthStatusProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    void emitsBasicStatusWhenNoRateLimit() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);

        McpHealthStatusProcessor processor = new McpHealthStatusProcessor();
        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("OK", body.get("status"));
        assertEquals(null, body.get("rateLimiter"));
        assertEquals("application/json", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void includesRateLimiterSnapshotWhenAvailable() throws Exception {
        System.setProperty("mcp.rate.bucketCapacity", "5");
        System.setProperty("mcp.rate.refillPerSecond", "5");
        McpRateLimitProcessor rateLimit = new McpRateLimitProcessor();

        try {
            DefaultCamelContext ctx = new DefaultCamelContext();
            Exchange exchange = new DefaultExchange(ctx);

            McpHealthStatusProcessor processor = new McpHealthStatusProcessor(rateLimit);
            processor.process(exchange);

            Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
            Map<String, Object> snapshot = (Map<String, Object>) body.get("rateLimiter");
            assertEquals("OK", body.get("status"));
            assertEquals(5, snapshot.get("capacity"));
            assertEquals(5, snapshot.get("availableTokens"));
            assertEquals(5.0d, snapshot.get("refillPerSecond"));
            assertTrue(((Number) snapshot.get("lastRefillEpochMillis")).longValue() > 0L);
        } finally {
            System.clearProperty("mcp.rate.bucketCapacity");
            System.clearProperty("mcp.rate.refillPerSecond");
        }
    }
}
