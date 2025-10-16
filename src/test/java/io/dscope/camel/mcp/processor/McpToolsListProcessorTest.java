package io.dscope.camel.mcp.processor;

import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.catalog.McpMethodCatalog;
import io.dscope.camel.mcp.catalog.McpMethodDefinition;

class McpToolsListProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Test
    @SuppressWarnings("unchecked")
    void rendersToolCatalogResponse() throws Exception {
        McpMethodDefinition definition = new McpMethodDefinition();
        definition.setName("echo");
        definition.setTitle("Echo Tool");
        definition.setDescription("Returns whatever you pass in");
        definition.setInputSchema(Map.of("type", "object"));
        definition.setOutputSchema(Map.of("type", "string"));
        definition.setAnnotations(Map.of("category", "utility"));

        McpMethodCatalog catalog = new McpMethodCatalog(List.of(definition));
        McpToolsListProcessor processor = new McpToolsListProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "abc-123");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("2.0", body.get("jsonrpc"));
        assertEquals("abc-123", body.get("id"));

        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertEquals(1, tools.size());
        Map<String, Object> tool = tools.get(0);
        assertEquals("echo", tool.get("name"));
        assertEquals("Echo Tool", tool.get("title"));
        assertEquals("Returns whatever you pass in", tool.get("description"));
        assertEquals(Map.of("type", "object"), tool.get("inputSchema"));
        assertEquals(Map.of("type", "string"), tool.get("outputSchema"));
        assertEquals(Map.of("category", "utility"), tool.get("annotations"));

        assertEquals(200, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("application/json", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
        assertEquals("2025-06-18", exchange.getIn().getHeader("MCP-Protocol-Version"));
        assertEquals("no-store", exchange.getIn().getHeader("Cache-Control"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsProtocolVersionWhenMissing() throws Exception {
        McpMethodCatalog catalog = new McpMethodCatalog(List.of());
        McpToolsListProcessor processor = new McpToolsListProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "xyz");

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertTrue(tools.isEmpty());
        assertEquals(McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION, exchange.getIn().getHeader("MCP-Protocol-Version"));
    }
}
