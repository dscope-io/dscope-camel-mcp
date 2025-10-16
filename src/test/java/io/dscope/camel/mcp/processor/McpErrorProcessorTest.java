package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class McpErrorProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    @SuppressWarnings("unchecked")
    void wrapsConfiguredErrorDetails() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "err-1");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
        exchange.setProperty(McpErrorProcessor.PROPERTY_ERROR_CODE, 999);
        exchange.setProperty(McpErrorProcessor.PROPERTY_ERROR_MESSAGE, "Something failed");
        exchange.setProperty(McpErrorProcessor.PROPERTY_ERROR_DATA, Map.of("detail", "bad input"));

        McpErrorProcessor processor = new McpErrorProcessor();
        processor.process(exchange);

        Map<String, Object> envelope = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("2.0", envelope.get("jsonrpc"));
        assertEquals("err-1", envelope.get("id"));

        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertEquals(999, error.get("code"));
        assertEquals("Something failed", error.get("message"));
        assertEquals(Map.of("detail", "bad input"), error.get("data"));

        assertEquals("2025-06-18", exchange.getIn().getHeader("MCP-Protocol-Version"));
        assertEquals("application/json", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("no-store", exchange.getIn().getHeader("Cache-Control"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToDefaultsWhenMissing() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);

        McpErrorProcessor processor = new McpErrorProcessor();
        processor.process(exchange);

        Map<String, Object> envelope = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("2.0", envelope.get("jsonrpc"));
        assertNull(envelope.get("id"));

        Map<String, Object> error = (Map<String, Object>) envelope.get("error");
        assertEquals(-32603, error.get("code"));
        assertEquals("An unexpected error occurred", error.get("message"));
        assertNull(error.get("data"));
        assertEquals(McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION, exchange.getIn().getHeader("MCP-Protocol-Version"));
    }
}
