package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class McpPingProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @SuppressWarnings("unchecked")
    void shouldRespondWithOkAndEcho() throws Exception {
        McpPingProcessor processor = new McpPingProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ping-1");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.getIn().setBody(Map.of("sequence", 42));

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);

            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            assertEquals("2.0", envelope.get("jsonrpc"));
            assertEquals("ping-1", envelope.get("id"));
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertTrue((Boolean) result.get("ok"));
            assertTrue(result.containsKey("timestamp"));
            assertEquals(Map.of("sequence", 42), result.get("echo"));
            assertEquals("2025-06-18", exchange.getIn().getHeader("MCP-Protocol-Version"));
        }
    }
}
