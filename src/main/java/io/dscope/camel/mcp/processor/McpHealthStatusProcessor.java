package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Emits a simple health status JSON payload for MCP deployments, including rate limiter snapshot.
 */
@BindToRegistry("mcpHealthStatus")
public class McpHealthStatusProcessor implements Processor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpRateLimitProcessor rateLimit;

    public McpHealthStatusProcessor() {
        this(null);
    }

    public McpHealthStatusProcessor(McpRateLimitProcessor rateLimit) {
        this.rateLimit = rateLimit;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "OK");
        if (rateLimit != null) {
            body.put("rateLimiter", rateLimit.snapshot());
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(body);
            exchange.getIn().setBody(json);
        } catch (JsonProcessingException e) {
            exchange.getIn().setBody("{\"status\":\"DEGRADED\"}");
        }
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "application/json");
    }
}
