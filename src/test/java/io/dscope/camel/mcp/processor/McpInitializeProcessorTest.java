package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class McpInitializeProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldProduceHandshakeResponse() throws Exception {
        McpInitializeProcessor processor = new McpInitializeProcessor();
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "init-1");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("clientInfo", Map.of("name", "client", "version", "1.0"));
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            assertEquals("2.0", envelope.get("jsonrpc"));
            assertEquals("init-1", envelope.get("id"));
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertNotNull(result);
            assertEquals("2025-06-18", result.get("protocolVersion"));
            @SuppressWarnings("unchecked")
            Map<String, Object> serverInfo = (Map<String, Object>) result.get("serverInfo");
            assertNotNull(serverInfo);
            assertEquals("camel-mcp-component", serverInfo.get("name"));
            assertTrue(serverInfo.containsKey("version"));
            assertEquals("2025-06-18", exchange.getIn().getHeader("MCP-Protocol-Version"));
            assertEquals("no-store", exchange.getIn().getHeader("Cache-Control"));
            assertEquals("application/json", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
            assertEquals(200, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        }
    }
}
