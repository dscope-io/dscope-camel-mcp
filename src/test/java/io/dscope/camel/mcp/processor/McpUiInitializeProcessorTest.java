package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.service.McpUiSessionRegistry;

class McpUiInitializeProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldCreateSessionOnInitialize() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiInitializeProcessor processor = new McpUiInitializeProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ui-init-1");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("resourceUri", "ui://example.com/app");
            params.put("clientInfo", Map.of("name", "test-app", "version", "1.0"));
            exchange.getIn().setBody(params);

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            assertNotNull(body);

            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            assertEquals("2.0", envelope.get("jsonrpc"));
            assertEquals("ui-init-1", envelope.get("id"));
            
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            assertNotNull(result);
            
            // Verify session ID is returned
            String sessionId = (String) result.get("sessionId");
            assertNotNull(sessionId);
            assertFalse(sessionId.isBlank());
            
            // Verify session is registered
            assertTrue(registry.exists(sessionId));
            
            // Verify host info
            @SuppressWarnings("unchecked")
            Map<String, Object> hostInfo = (Map<String, Object>) result.get("hostInfo");
            assertNotNull(hostInfo);
            assertEquals("camel-mcp", hostInfo.get("name"));
            assertEquals("1.3.0", hostInfo.get("version"));
            
            // Verify capabilities are returned
            assertNotNull(result.get("capabilities"));
        }
    }

    @Test
    void shouldReturnCapabilities() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiInitializeProcessor processor = new McpUiInitializeProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ui-init-2");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.getIn().setBody(Map.of());

            processor.process(exchange);

            String body = exchange.getIn().getBody(String.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = MAPPER.readValue(body, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            
            @SuppressWarnings("unchecked")
            java.util.List<String> capabilities = (java.util.List<String>) result.get("capabilities");
            assertNotNull(capabilities);
            assertTrue(capabilities.contains("tools/call"));
            assertTrue(capabilities.contains("ui/message"));
            assertTrue(capabilities.contains("ui/update-model-context"));
            assertTrue(capabilities.contains("ui/notifications/tool-input"));
            assertTrue(capabilities.contains("ui/notifications/tool-result"));
        }
    }

    @Test
    void shouldStoreSessionIdOnExchange() throws Exception {
        McpUiSessionRegistry registry = new McpUiSessionRegistry();
        McpUiInitializeProcessor processor = new McpUiInitializeProcessor(registry);
        
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            Exchange exchange = new DefaultExchange(ctx);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "ui-init-3");
            exchange.setProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, "2025-06-18");
            exchange.getIn().setBody(Map.of());

            processor.process(exchange);

            String sessionId = exchange.getProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, String.class);
            assertNotNull(sessionId);
            assertTrue(registry.exists(sessionId));
        }
    }
}
