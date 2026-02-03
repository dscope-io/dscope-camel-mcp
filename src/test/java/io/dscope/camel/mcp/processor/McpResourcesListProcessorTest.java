package io.dscope.camel.mcp.processor;

import java.util.List;
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

import io.dscope.camel.mcp.catalog.McpResourceCatalog;
import io.dscope.camel.mcp.catalog.McpResourceDefinition;

class McpResourcesListProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    @SuppressWarnings("unchecked")
    void rendersResourceCatalogResponse() throws Exception {
        McpResourceDefinition def = new McpResourceDefinition();
        def.setUri("ui://app/main");
        def.setName("Main App");
        def.setDescription("The main application");
        def.setMimeType("text/html");

        McpResourceCatalog catalog = new McpResourceCatalog(List.of(def));
        McpResourcesListProcessor processor = new McpResourcesListProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "abc-123");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("2.0", body.get("jsonrpc"));
        assertEquals("abc-123", body.get("id"));

        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertEquals(1, resources.size());
        Map<String, Object> resource = resources.get(0);
        assertEquals("ui://app/main", resource.get("uri"));
        assertEquals("Main App", resource.get("name"));
        assertEquals("The main application", resource.get("description"));
        assertEquals("text/html", resource.get("mimeType"));

        assertEquals(200, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("application/json", exchange.getIn().getHeader(Exchange.CONTENT_TYPE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyResourceCatalogReturnsEmptyList() throws Exception {
        McpResourceCatalog catalog = new McpResourceCatalog(List.of());
        McpResourcesListProcessor processor = new McpResourcesListProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "xyz");

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        Map<String, Object> result = (Map<String, Object>) body.get("result");
        List<Map<String, Object>> resources = (List<Map<String, Object>>) result.get("resources");
        assertTrue(resources.isEmpty());
    }
}
