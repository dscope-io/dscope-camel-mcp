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

class McpResourcesReadProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Test
    @SuppressWarnings("unchecked")
    void readsBuiltinUiResource() throws Exception {
        McpResourceDefinition def = new McpResourceDefinition();
        def.setUri("ui://app/main");
        def.setName("Main App");
        def.setMimeType("text/html");
        def.setSource("builtin:mcp-app");

        McpResourceCatalog catalog = new McpResourceCatalog(List.of(def));
        McpResourcesReadProcessor processor = new McpResourcesReadProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "read-123");
        exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
        exchange.getIn().setBody(Map.of("uri", "ui://app/main"));

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        assertEquals("2.0", body.get("jsonrpc"));
        assertEquals("read-123", body.get("id"));

        Map<String, Object> result = (Map<String, Object>) body.get("result");
        assertNotNull(result);
        List<Map<String, Object>> contents = (List<Map<String, Object>>) result.get("contents");
        assertEquals(1, contents.size());
        Map<String, Object> content = contents.get(0);
        assertEquals("ui://app/main", content.get("uri"));
        assertEquals("text/html", content.get("mimeType"));
        String text = (String) content.get("text");
        assertNotNull(text);
        assertTrue(text.contains("<!DOCTYPE html>"));
        assertTrue(text.contains("MCP App"));

        assertEquals(200, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorForMissingUri() throws Exception {
        McpResourceCatalog catalog = new McpResourceCatalog(List.of());
        McpResourcesReadProcessor processor = new McpResourcesReadProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "err-1");
        exchange.getIn().setBody(Map.of());

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32602, error.get("code"));
        assertTrue(((String) error.get("message")).contains("Missing required parameter"));

        assertEquals(400, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsErrorForNonExistentResource() throws Exception {
        McpResourceCatalog catalog = new McpResourceCatalog(List.of());
        McpResourcesReadProcessor processor = new McpResourcesReadProcessor(catalog);

        DefaultCamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "err-2");
        exchange.getIn().setBody(Map.of("uri", "ui://missing"));

        processor.process(exchange);

        Map<String, Object> body = MAPPER.readValue(exchange.getIn().getBody(String.class), MAP_TYPE);
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertNotNull(error);
        assertEquals(-32602, error.get("code"));
        assertTrue(((String) error.get("message")).contains("Resource not found"));

        assertEquals(404, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }
}
