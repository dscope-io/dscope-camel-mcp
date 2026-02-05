package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.model.McpUiSession;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

import static org.junit.jupiter.api.Assertions.*;

class McpUiUpdateModelContextProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldUpdateModelContext() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        McpUiUpdateModelContextProcessor processor = new McpUiUpdateModelContextProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ctx-1");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("context", Map.of("key1", "value1", "key2", 42));
            params.put("operation", "replace");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertNotNull(result);
            assertEquals(true, result.get("updated"));
            
            // Verify context was stored on session
            assertNotNull(session.getModelContext());
            @SuppressWarnings("unchecked")
            Map<String, Object> storedContext = (Map<String, Object>) session.getModelContext();
            assertEquals("value1", storedContext.get("key1"));
            assertEquals(42, storedContext.get("key2"));
        }
    }

    @Test
    void shouldMergeModelContext() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        // Pre-populate with existing context
        Map<String, Object> existingContext = new LinkedHashMap<>();
        existingContext.put("existing", "data");
        session.setModelContext(existingContext);
        
        McpUiUpdateModelContextProcessor processor = new McpUiUpdateModelContextProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ctx-2");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("context", Map.of("newKey", "newValue"));
            params.put("operation", "merge");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            // Verify context was merged
            @SuppressWarnings("unchecked")
            Map<String, Object> storedContext = (Map<String, Object>) session.getModelContext();
            assertEquals("data", storedContext.get("existing"));
            assertEquals("newValue", storedContext.get("newKey"));
        }
    }

    @Test
    void shouldRejectMissingContext() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        McpUiUpdateModelContextProcessor processor = new McpUiUpdateModelContextProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ctx-3");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());
            exchange.getIn().setBody(Map.of()); // No context

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            assertNotNull(envelope.get("error"));
            assertEquals(400, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        }
    }

    @Test
    void shouldRejectInvalidSession() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiUpdateModelContextProcessor processor = new McpUiUpdateModelContextProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ctx-4");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, "invalid-session");
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("context", Map.of("key", "value"));
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            assertNotNull(envelope.get("error"));
        }
    }
}
