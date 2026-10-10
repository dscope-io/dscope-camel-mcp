package io.dscope.camel.mcp;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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

        // Send a ping request with proper MCP headers
        String request = """
            {
                "jsonrpc": "2.0",
                "id": "test-1",
                "method": "ping"
            }
            """;

        String response = template.requestBodyAndHeaders(
            "http://localhost:9876/test",
            request,
            Map.of(
                "Content-Type", "application/json",
                "Accept", "application/json, text/event-stream"
            ),
            String.class
        );

        assertNotNull(response);
        assertTrue(response.contains("\"jsonrpc\":\"2.0\"") || response.contains("\"jsonrpc\": \"2.0\""),
            "Response should contain JSON-RPC 2.0 but was: " + response);
    }

    @Test
    void testModernServerDiscoverIsHandledByComponent() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9881/mcp")
                    .process(exchange -> fail("server/discover must not be delegated to the user route"));
            }
        });

        context.start();
        TimeUnit.MILLISECONDS.sleep(500);

        String request = """
            {
              "jsonrpc": "2.0",
              "id": "discover-1",
              "method": "server/discover",
              "params": {
                "_meta": {
                  "io.modelcontextprotocol/protocolVersion": "2026-07-28",
                  "io.modelcontextprotocol/clientCapabilities": {}
                }
              }
            }
            """;
        String response = template.requestBodyAndHeaders(
                "http://localhost:9881/mcp",
                request,
                Map.of(
                        "CamelHttpMethod", "POST",
                        "Content-Type", "application/json",
                        "Accept", "application/json, text/event-stream",
                        "MCP-Protocol-Version", "2026-07-28",
                        "Mcp-Method", "server/discover"),
                String.class);

        assertNotNull(response);
        assertTrue(response.contains("\"resultType\":\"complete\""), response);
        assertTrue(response.contains("\"2026-07-28\""), response);
        assertTrue(response.contains("\"supportedVersions\""), response);
        assertTrue(response.contains("\"io.modelcontextprotocol/serverInfo\""), response);
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
        
        // Verify the context started successfully
        assertTrue(context.getStatus().isStarted());
        assertEquals(1, context.getRoutes().size());
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

        template.requestBodyAndHeaders(
            "http://localhost:9878/mcp",
            request,
            Map.of(
                "Content-Type", "application/json",
                "Accept", "application/json, text/event-stream"
            ),
            String.class
        );

        assertEquals("tools/list", capturedMethod[0]);
    }

    @Test
    void testInvalidHttpHeadersReturnJsonRpcError() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("mcp:http://localhost:9880/mcp")
                    .process(exchange -> exchange.getMessage().setBody(Map.of("ok", Boolean.TRUE)));
            }
        });

        context.start();
        TimeUnit.MILLISECONDS.sleep(500);

        String request = """
            {
                "jsonrpc": "2.0",
                "id": "bad-headers",
                "method": "initialize",
                "params": {}
            }
            """;

        Exchange exchange = template.request("http://localhost:9880/mcp?throwExceptionOnFailure=false", incoming -> {
            incoming.getMessage().setBody(request);
            incoming.getMessage().setHeader("Content-Type", "application/json");
        });

        String response = exchange.getMessage().getBody(String.class);
        Integer statusCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);

        assertEquals(400, statusCode);
        assertNotNull(response);
        assertTrue(response.contains("\"jsonrpc\":\"2.0\""), response);
        assertTrue(response.contains("\"error\""), response);
        assertTrue(response.contains("Accept header must include application/json and text/event-stream"), response);
    }

    @Test
    void testMalformedRequestsConsumeRateLimitTokens() throws Exception {
        Map<String, String> originalProperties = new java.util.HashMap<>();
        for (String key : new String[] {
                "mcp.rate.enabled", "mcp.rate.bucketCapacity", "mcp.rate.refillPerSecond"}) {
            originalProperties.put(key, System.getProperty(key));
        }
        try {
            System.setProperty("mcp.rate.enabled", "true");
            System.setProperty("mcp.rate.bucketCapacity", "2");
            System.setProperty("mcp.rate.refillPerSecond", "0");
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("mcp:http://localhost:9882/mcp")
                        .process(exchange -> fail("Malformed or throttled requests must not reach the user route"));
                }
            });
            context.start();

            String[] requests = {
                "{\"jsonrpc\":\"1.0\",\"id\":\"bad-envelope\",\"method\":\"ping\"}",
                "{invalid-json",
                "{invalid-json",
                "{\"jsonrpc\":\"2.0\",\"id\":\"valid\",\"method\":\"ping\"}"
            };
            for (int i = 0; i < requests.length; i++) {
                String request = requests[i];
                Exchange response = template.request(
                        "http://localhost:9882/mcp?throwExceptionOnFailure=false", exchange -> {
                            exchange.getMessage().setBody(request);
                            exchange.getMessage().setHeader("Content-Type", "application/json");
                            exchange.getMessage().setHeader("Accept", "application/json, text/event-stream");
                        });
                assertEquals(400, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class));
                String body = response.getMessage().getBody(String.class);
                assertNotNull(body);
                if (i == 0) {
                    assertTrue(body.contains("Unsupported jsonrpc version: 1.0"), body);
                } else if (i == 1) {
                    assertTrue(body.contains("Unable to parse JSON-RPC request for HTTP validation"), body);
                } else {
                    assertTrue(body.contains("Rate limit exceeded"), body);
                }
            }
        } finally {
            originalProperties.forEach((key, value) -> {
                if (value == null) {
                    System.clearProperty(key);
                } else {
                    System.setProperty(key, value);
                }
            });
        }
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
