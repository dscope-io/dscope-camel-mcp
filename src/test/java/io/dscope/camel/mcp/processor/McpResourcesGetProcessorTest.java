package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class McpResourcesGetProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    void returnsEmptyResultByDefault() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        // Params are passed as body (like other MCP processors expect)
        exchange.getIn().setBody(Map.of("resource", "test-resource"));

        McpResourcesGetProcessor processor = new McpResourcesGetProcessor();
        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        // writeResult wraps in JSON-RPC envelope with "result" key
        assertTrue(body.containsKey("result"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertNotNull(result);
        assertTrue(result.isEmpty()); // default provider returns empty map
        assertEquals("test-resource", exchange.getProperty(McpResourcesGetProcessor.PROPERTY_RESOURCE_NAME));
    }

    @Test
    void usesCustomResourceProvider() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setBody(Map.of("resource", "my-doc"));

        McpResourcesGetProcessor processor = new McpResourcesGetProcessor();
        processor.setResourceProvider(name -> Map.of("name", name, "content", "loaded from " + name));
        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertNotNull(result);
        assertEquals("my-doc", result.get("name"));
        assertEquals("loaded from my-doc", result.get("content"));
    }

    @Test
    void returnsErrorWhenResourceParamMissing() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setBody(Map.of()); // no resource param

        McpResourcesGetProcessor processor = new McpResourcesGetProcessor();
        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32602, error.get("code"));
    }

    @Test
    void returnsErrorWhenResourceParamBlank() throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setBody(Map.of("resource", "   "));

        McpResourcesGetProcessor processor = new McpResourcesGetProcessor();
        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32602, error.get("code"));
    }
}
