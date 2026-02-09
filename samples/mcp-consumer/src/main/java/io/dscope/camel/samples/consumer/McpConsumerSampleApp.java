package io.dscope.camel.samples.consumer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.main.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates the MCP Consumer component — a fully functional MCP server
 * built with a single {@code from("mcp:...")} route.
 *
 * <p>The consumer component automatically handles:
 * <ul>
 *   <li>JSON-RPC 2.0 envelope parsing</li>
 *   <li>HTTP header validation (Accept, Content-Type)</li>
 *   <li>Request size guard</li>
 *   <li>Rate limiting</li>
 * </ul>
 *
 * <p>The user processor receives an exchange with these properties already set:
 * <ul>
 *   <li>{@code mcp.jsonrpc.method} — the JSON-RPC method (e.g. "initialize", "tools/call")</li>
 *   <li>{@code mcp.jsonrpc.id} — the request id</li>
 *   <li>{@code mcp.jsonrpc.type} — REQUEST or NOTIFICATION</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 * cd samples/mcp-consumer
 * mvn compile exec:java
 * </pre>
 *
 * <p>Then test with:
 * <pre>
 * curl -X POST http://localhost:3000/mcp \
 *   -H "Content-Type: application/json" \
 *   -H "Accept: application/json, text/event-stream" \
 *   -d '{"jsonrpc":"2.0","id":"1","method":"ping"}'
 * </pre>
 */
public class McpConsumerSampleApp {

    private static final Logger LOG = LoggerFactory.getLogger(McpConsumerSampleApp.class);

    public static void main(String[] args) throws Exception {
        Main main = new Main();

        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // HTTP MCP server on port 3000
                from("mcp:http://0.0.0.0:3000/mcp")
                    .routeId("mcp-consumer-http")
                    .process(McpConsumerSampleApp::dispatch);

                // WebSocket MCP server on port 3001
                from("mcp:http://0.0.0.0:3001/mcp?websocket=true")
                    .routeId("mcp-consumer-ws")
                    .process(McpConsumerSampleApp::dispatch);
            }
        });

        LOG.info("Starting MCP Consumer Sample — HTTP on :3000, WebSocket on :3001");
        main.run(args);
    }

    // ---- request dispatcher ----

    /**
     * Routes incoming MCP requests to the appropriate handler based on
     * the {@code mcp.jsonrpc.method} property set by the consumer.
     */
    static void dispatch(Exchange exchange) {
        String method = exchange.getProperty("mcp.jsonrpc.method", String.class);
        String type   = exchange.getProperty("mcp.jsonrpc.type", String.class);

        if ("NOTIFICATION".equals(type)) {
            handleNotification(exchange, method);
            return;
        }

        if (method == null) {
            writeError(exchange, -32600, "Missing method");
            return;
        }

        switch (method) {
            case "initialize"    -> handleInitialize(exchange);
            case "ping"          -> handlePing(exchange);
            case "tools/list"    -> handleToolsList(exchange);
            case "tools/call"    -> handleToolsCall(exchange);
            case "resources/list"-> handleResourcesList(exchange);
            default              -> writeError(exchange, -32601,
                                       "Method not supported: " + method);
        }
    }

    // ---- MCP method handlers ----

    private static void handleInitialize(Exchange exchange) {
        writeResult(exchange, Map.of(
            "protocolVersion", "2025-06-18",
            "serverInfo", Map.of(
                "name", "mcp-consumer-sample",
                "version", "1.3.0"
            ),
            "capabilities", Map.of(
                "tools/list", true,
                "tools/call", true,
                "ping", true,
                "resources/list", true
            )
        ));
    }

    private static void handlePing(Exchange exchange) {
        writeResult(exchange, Map.of("ok", true));
    }

    @SuppressWarnings("unchecked")
    private static void handleToolsList(Exchange exchange) {
        List<Map<String, Object>> tools = List.of(
            Map.of(
                "name", "echo",
                "description", "Returns the provided text unchanged.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "text", Map.of("type", "string", "description", "Text to echo back")
                    ),
                    "required", List.of("text")
                )
            ),
            Map.of(
                "name", "add",
                "description", "Adds two numbers together.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "a", Map.of("type", "number", "description", "First operand"),
                        "b", Map.of("type", "number", "description", "Second operand")
                    ),
                    "required", List.of("a", "b")
                )
            ),
            Map.of(
                "name", "greet",
                "description", "Generates a personalised greeting.",
                "inputSchema", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "name", Map.of("type", "string", "description", "Name to greet")
                    ),
                    "required", List.of("name")
                )
            )
        );
        writeResult(exchange, Map.of("tools", tools));
    }

    @SuppressWarnings("unchecked")
    private static void handleToolsCall(Exchange exchange) {
        // The envelope processor extracts the tool name into mcp.tool.name
        // and sets the body to just the arguments map.
        String toolName = exchange.getProperty("mcp.tool.name", String.class);
        Map<String, Object> args = exchange.getIn().getBody(Map.class);
        if (args == null) args = Map.of();

        if (toolName == null) {
            writeError(exchange, -32602, "Missing required parameter: name");
            return;
        }

        switch (toolName) {
            case "echo" -> {
                String text = Objects.toString(args.getOrDefault("text", ""), "");
                writeResult(exchange, Map.of("content",
                    List.of(Map.of("type", "text", "text", text))));
            }
            case "add" -> {
                double a = toDouble(args.get("a"));
                double b = toDouble(args.get("b"));
                writeResult(exchange, Map.of("content",
                    List.of(Map.of("type", "text", "text", String.valueOf(a + b)))));
            }
            case "greet" -> {
                String name = Objects.toString(args.getOrDefault("name", "World"), "World");
                writeResult(exchange, Map.of("content",
                    List.of(Map.of("type", "text", "text", "Hello, " + name + "!"))));
            }
            default -> writeError(exchange, -32602, "Unknown tool: " + toolName);
        }
    }

    private static void handleResourcesList(Exchange exchange) {
        writeResult(exchange, Map.of("resources", List.of(
            Map.of(
                "uri", "resource://info/about",
                "name", "about",
                "description", "Information about this MCP consumer sample.",
                "mimeType", "text/plain"
            )
        )));
    }

    private static void handleNotification(Exchange exchange, String method) {
        // Notifications don't get a response body — return 204
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 204);
        exchange.getIn().setBody(null);
        LOG.info("Received notification: {}", method);
    }

    // ---- helpers ----

    private static void writeResult(Exchange exchange, Map<String, Object> result) {
        Object id = exchange.getProperty("mcp.jsonrpc.id");
        exchange.getIn().setBody(Map.of(
            "jsonrpc", "2.0",
            "id", id != null ? id : "null",
            "result", result
        ));
    }

    private static void writeError(Exchange exchange, int code, String message) {
        Object id = exchange.getProperty("mcp.jsonrpc.id");
        exchange.getIn().setBody(Map.of(
            "jsonrpc", "2.0",
            "id", id != null ? id : "null",
            "error", Map.of("code", code, "message", message)
        ));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(Objects.toString(value, "0")); }
        catch (NumberFormatException e) { return 0; }
    }
}
