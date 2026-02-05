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

class McpUiMessageProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldAcknowledgeMessage() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        McpUiMessageProcessor processor = new McpUiMessageProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "msg-1");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("message", "Hello from app");
            params.put("type", "info");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertNotNull(result);
            assertEquals(true, result.get("acknowledged"));
            
            // Verify message was stored on exchange
            assertEquals("Hello from app", exchange.getProperty(McpUiMessageProcessor.EXCHANGE_PROPERTY_UI_MESSAGE));
            assertEquals("info", exchange.getProperty(McpUiMessageProcessor.EXCHANGE_PROPERTY_UI_MESSAGE_TYPE));
        }
    }

    @Test
    void shouldRejectMissingMessage() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        McpUiMessageProcessor processor = new McpUiMessageProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "msg-2");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());
            exchange.getIn().setBody(Map.of()); // No message

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            // Should have error
            assertNotNull(envelope.get("error"));
            assertEquals(400, exchange.getIn().getHeader(Exchange.HTTP_RESPONSE_CODE));
        }
    }

    @Test
    void shouldRejectInvalidSession() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiMessageProcessor processor = new McpUiMessageProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "msg-3");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, "invalid-session");
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("message", "Hello");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            assertNotNull(envelope.get("error"));
        }
    }

    @Test
    void shouldAcceptSessionIdFromHeader() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiSession session = registry.register("ui://test.com/app");
        McpUiMessageProcessor processor = new McpUiMessageProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "msg-4");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            // Set session ID via header instead of property
            exchange.getIn().setHeader("X-MCP-Session-Id", session.getSessionId());
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("message", "Hello via header");
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertNotNull(result);
            assertEquals(true, result.get("acknowledged"));
        }
    }
}
