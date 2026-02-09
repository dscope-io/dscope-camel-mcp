package io.dscope.camel.mcp;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for McpConsumer functionality including HTTP and WebSocket endpoints.
 */
class McpConsumerTest {

    private CamelContext context;
    private ProducerTemplate template;

    @BeforeEach
    void setUp() throws Exception {
        context = new DefaultCamelContext();
        template = context.createProducerTemplate();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void testHttpConsumerStartsAndResponds() throws Exception {
        // Setup a simple echo processor
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9876/test")
                    .process(exchange -> {
                        // Simple echo - just set a response
                        Map<String, Object> response = Map.of(
                            "jsonrpc", "2.0",
                            "id", exchange.getProperty("mcp.jsonrpc.id", String.class),
                            "result", Map.of("echo", "pong")
                        );
                        exchange.getMessage().setBody(response);
                    });
            }
        });

        context.start();
        
        // Give the consumer time to start
        TimeUnit.MILLISECONDS.sleep(500);

        // Send a ping request
        String request = """
            {
                "jsonrpc": "2.0",
                "id": "test-1",
                "method": "ping"
            }
            """;

        String response = template.requestBody(
            "http://localhost:9876/test",
            request,
            String.class
        );

        assertNotNull(response);
        assertTrue(response.contains("\"jsonrpc\":\"2.0\"") || response.contains("\"jsonrpc\": \"2.0\""));
    }

    @Test
    void testWebSocketConsumerConfiguration() throws Exception {
        // Test that WebSocket consumer can be configured
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9877/ws?websocket=true")
                    .process(exchange -> {
                        Map<String, Object> response = Map.of(
                            "jsonrpc", "2.0",
                            "result", Map.of("status", "ok")
                        );
                        exchange.getMessage().setBody(response);
                    });
            }
        });

        context.start();
        
        // Just verify the route starts without errors
        TimeUnit.MILLISECONDS.sleep(500);
        
        assertTrue(context.getRouteController().getRouteStatus("mcp-consumer-" + 
            "mcp:http://localhost:9877/ws?websocket=true".hashCode()).isStarted());
    }

    @Test
    void testConsumerWithJsonRpcParsing() throws Exception {
        // Test that JSON-RPC envelope is properly parsed
        final String[] capturedMethod = new String[1];
        
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9878/mcp")
                    .process(exchange -> {
                        // Capture the parsed method
                        capturedMethod[0] = exchange.getProperty("mcp.jsonrpc.method", String.class);
                        
                        Map<String, Object> response = Map.of(
                            "jsonrpc", "2.0",
                            "id", exchange.getProperty("mcp.jsonrpc.id", String.class),
                            "result", Map.of()
                        );
                        exchange.getMessage().setBody(response);
                    });
            }
        });

        context.start();
        TimeUnit.MILLISECONDS.sleep(500);

        String request = """
            {
                "jsonrpc": "2.0",
                "id": "test-2",
                "method": "tools/list"
            }
            """;

        template.requestBody("http://localhost:9878/mcp", request, String.class);

        assertEquals("tools/list", capturedMethod[0]);
    }

    @Test
    void testConsumerStopsCleanly() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9879/mcp")
                    .process(exchange -> {
                        // No-op processor
                    });
            }
        });

        context.start();
        TimeUnit.MILLISECONDS.sleep(300);
        
        // Stop should not throw
        assertDoesNotThrow(() -> context.stop());
    }
}
