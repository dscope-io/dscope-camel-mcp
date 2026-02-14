# 🛠 Developing MCP Services

This guide explains how to build MCP services using the Camel MCP component. You can define routes in YAML (recommended for simplicity) or Java (for complex logic).

## Project Setup

### Use from Maven Central

Artifact coordinates:

- `io.dscope.camel:camel-mcp:1.3.0`

Maven Central links:

- https://central.sonatype.com/artifact/io.dscope.camel/camel-mcp
- https://repo1.maven.org/maven2/io/dscope/camel/camel-mcp/

For Maven and Gradle builds, `mavenCentral()` is enough; no extra repository configuration is required.

### Maven Dependencies

```xml
<dependencies>
    <!-- Core MCP component -->
    <dependency>
        <groupId>io.dscope.camel</groupId>
        <artifactId>camel-mcp</artifactId>
        <version>1.3.0</version>
    </dependency>
    
    <!-- Camel runtime -->
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-main</artifactId>
        <version>4.15.0</version>
    </dependency>
    
    <!-- HTTP/WebSocket transport -->
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-undertow</artifactId>
        <version>4.15.0</version>
    </dependency>
    
    <!-- YAML route definitions -->
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-yaml-dsl</artifactId>
        <version>4.15.0</version>
    </dependency>
    
    <!-- JSON serialization -->
    <dependency>
        <groupId>org.apache.camel</groupId>
        <artifactId>camel-jackson</artifactId>
        <version>4.15.0</version>
    </dependency>
</dependencies>
```

### Project Structure

```
my-mcp-service/
├── pom.xml
└── src/main/
    ├── java/
    │   └── com/example/mcp/
    │       ├── MyMcpApplication.java      # Main entry point
    │       ├── MyToolProcessor.java       # Custom tool handler
    │       └── MyResourceProcessor.java   # Custom resource handler
    └── resources/
        ├── routes/
        │   └── mcp-service.camel.yaml     # Route definitions
        └── mcp/
            └── methods.yaml               # Tool catalog
```

## Creating MCP Servers

There are three approaches to building MCP servers, from simplest to most flexible:

### Approach 1: Direct Consumer Component (Simplest)

Use `from("mcp:...")` for the simplest possible MCP server. The consumer handles all protocol details automatically:

```java
import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;

public class MyMcpServer {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // HTTP server on port 3000
                from("mcp:http://0.0.0.0:3000/mcp")
                    .process(exchange -> {
                        String method = exchange.getProperty("mcp.jsonrpc.method", String.class);
                        String toolName = exchange.getProperty("mcp.tool.name", String.class);
                        
                        switch (method) {
                            case "initialize" -> exchange.getMessage().setBody(Map.of(
                                "protocolVersion", "2024-11-05",
                                "serverInfo", Map.of("name", "my-server", "version", "1.0.0"),
                                "capabilities", Map.of("tools", Map.of("listChanged", true))
                            ));
                            case "tools/list" -> exchange.getMessage().setBody(Map.of(
                                "tools", List.of(Map.of("name", "echo", "description", "Echoes input"))
                            ));
                            case "tools/call" -> exchange.getMessage().setBody(Map.of(
                                "content", List.of(Map.of("type", "text", "text", "Echo: " + toolName))
                            ));
                            default -> exchange.getMessage().setBody(Map.of());
                        }
                    });
                
                // WebSocket server on port 3001
                from("mcp:http://0.0.0.0:3001/mcp?websocket=true")
                    .process(exchange -> { /* same logic */ });
            }
        });
        main.run(args);
    }
}
```

The consumer automatically provides: request size validation, HTTP header validation, rate limiting, JSON-RPC envelope parsing, and JSON response serialization.

See `samples/mcp-consumer/` for a complete working example with three tools (echo, add, greet).

### Approach 2: YAML Routes with Kamelets

### Approach 3: Java RouteBuilder with Undertow

For full control over the processor pipeline, use Undertow directly with the built-in processors.

---

## Creating Routes in YAML

YAML routes are the recommended approach for most MCP services. They're declarative, easy to modify, and don't require recompilation.

### Basic HTTP MCP Service

Create `src/main/resources/routes/mcp-service.camel.yaml`:

```yaml
# Exception handler for validation errors
- onException:
    exception:
      - java.lang.IllegalArgumentException
    handled:
      constant: "true"
    steps:
      - setProperty:
          name: mcp.error.code
          constant: "-32600"
      - setProperty:
          name: mcp.error.message
          simple: "${exception.message}"
      - process:
          ref: mcpError

# Main MCP HTTP endpoint
- route:
    id: mcp-http-service
    from:
      uri: undertow:http://0.0.0.0:8080/mcp?httpMethodRestrict=POST
      steps:
        # Security and validation
        - process:
            ref: mcpRequestSizeGuard
        - process:
            ref: mcpRateLimit
        - process:
            ref: mcpJsonRpcEnvelope
        
        # Route to method handlers
        - choice:
            when:
              # Handle notifications (no response expected)
              - simple: "${exchangeProperty.mcp.jsonrpc.type} == 'NOTIFICATION'"
                steps:
                  - process:
                      ref: mcpNotification
                  - stop: {}
              
              # Initialize session
              - simple: "${exchangeProperty.mcp.jsonrpc.method} == 'initialize'"
                steps:
                  - process:
                      ref: mcpInitialize
              
              # Health check
              - simple: "${exchangeProperty.mcp.jsonrpc.method} == 'ping'"
                steps:
                  - process:
                      ref: mcpPing
              
              # List available tools
              - simple: \"${exchangeProperty.mcp.jsonrpc.method} == 'tools/list'\"
                steps:
                  - process:
                      ref: mcpToolsList
              
              # Execute a tool (use your custom processor)
              - simple: \"${exchangeProperty.mcp.jsonrpc.method} == 'tools/call'\"
                steps:
                  - process:
                      ref: myToolProcessor
              
              # List available resources
              - simple: \"${exchangeProperty.mcp.jsonrpc.method} == 'resources/list'\"
                steps:
                  - process:
                      ref: mcpResourcesList
              
              # Fetch a resource (use your custom processor)
              - simple: \"${exchangeProperty.mcp.jsonrpc.method} == 'resources/get'\"
                steps:
                  - process:
                      ref: myResourceProcessor
            
            # Unknown method
            otherwise:
              steps:
                - setProperty:
                    name: mcp.error.code
                    constant: "-32601"
                - setProperty:
                    name: mcp.error.message
                    simple: "Unsupported method: ${exchangeProperty.mcp.jsonrpc.method}"
                - process:
                    ref: mcpError
```

### Adding WebSocket Support

Add a second route for WebSocket in the same file:

```yaml
- route:
    id: mcp-ws-service
    from:
      uri: "undertow:ws://0.0.0.0:8090/mcp?sendToAll=false&allowedOrigins=*&exchangePattern=InOut"
      steps:
        - process:
            ref: mcpRequestSizeGuard
        - process:
            ref: mcpRateLimit
        - process:
            ref: mcpJsonRpcEnvelope
        
        - choice:
            # Same routing logic as HTTP...
            when:
              - simple: "${exchangeProperty.mcp.jsonrpc.method} == 'initialize'"
                steps:
                  - process:
                      ref: mcpInitialize
              # ... add other methods
```

## Creating Routes in Java

For complex routing logic or when you need programmatic control, use Java DSL.

### Java RouteBuilder

```java
package com.example.mcp;

import org.apache.camel.builder.RouteBuilder;

public class McpRoutes extends RouteBuilder {
    
    @Override
    public void configure() throws Exception {
        // Global exception handler
        onException(IllegalArgumentException.class)
            .handled(true)
            .setProperty("mcp.error.code", constant("-32600"))
            .setProperty("mcp.error.message", simple("${exception.message}"))
            .process("mcpError");
        
        // HTTP endpoint
        from("undertow:http://0.0.0.0:8080/mcp?httpMethodRestrict=POST")
            .routeId("mcp-http")
            .process("mcpRequestSizeGuard")
            .process("mcpRateLimit")
            .process("mcpJsonRpcEnvelope")
            .choice()
                .when(exchangeProperty("mcp.jsonrpc.type").isEqualTo("NOTIFICATION"))
                    .process("mcpNotification")
                    .stop()
                .when(exchangeProperty("mcp.jsonrpc.method").isEqualTo("initialize"))
                    .process("mcpInitialize")
                .when(exchangeProperty("mcp.jsonrpc.method").isEqualTo("ping"))
                    .process("mcpPing")
                .when(exchangeProperty("mcp.jsonrpc.method").isEqualTo("tools/list"))
                    .process("mcpToolsList")
                .when(exchangeProperty("mcp.jsonrpc.method").isEqualTo("tools/call"))
                    .process("myToolProcessor")
                .when(exchangeProperty("mcp.jsonrpc.method").isEqualTo("resources/get"))
                    .process("myResourceProcessor")
                .otherwise()
                    .setProperty("mcp.error.code", constant("-32601"))
                    .setProperty("mcp.error.message", simple("Unknown method"))
                    .process("mcpError");
        
        // WebSocket endpoint
        from("undertow:ws://0.0.0.0:8090/mcp?sendToAll=false&exchangePattern=InOut")
            .routeId("mcp-ws")
            // Same processing chain...
            .process("mcpJsonRpcEnvelope")
            .choice()
                // ... method routing
            .end();
    }
}
```

### Main Application

```java
package com.example.mcp;

import org.apache.camel.main.Main;

public class MyMcpApplication {
    
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        // Load YAML routes
        main.configure().withRoutesIncludePattern("classpath:routes/*.yaml");
        
        // Or add Java routes
        // main.configure().addRoutesBuilder(new McpRoutes());
        
        main.run(args);
    }
}
```

## Creating Custom Processors

### Tool Processor (tools/call)

Extend `AbstractMcpResponseProcessor` to handle tool execution:

```java
package com.example.mcp;

import java.util.List;
import java.util.Map;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor;

@BindToRegistry("myToolProcessor")
public class MyToolProcessor extends AbstractMcpResponseProcessor {
    
    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        // Get the tool name from the request
        String toolName = getToolName(exchange);
        
        // Get the tool arguments
        Map<String, Object> params = getRequestParameters(exchange);
        
        // Route to appropriate handler
        Map<String, Object> result = switch (toolName) {
            case "greet" -> handleGreet(params);
            case "calculate" -> handleCalculate(params);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
        
        // Write JSON-RPC response
        writeResult(exchange, result);
    }
    
    private Map<String, Object> handleGreet(Map<String, Object> args) {
        String name = (String) args.getOrDefault("name", "World");
        return Map.of(
            "content", List.of(
                Map.of("type", "text", "text", "Hello, " + name + "!")
            )
        );
    }
    
    private Map<String, Object> handleCalculate(Map<String, Object> args) {
        int a = ((Number) args.get("a")).intValue();
        int b = ((Number) args.get("b")).intValue();
        String op = (String) args.getOrDefault("operation", "add");
        
        int result = switch (op) {
            case "add" -> a + b;
            case "subtract" -> a - b;
            case "multiply" -> a * b;
            case "divide" -> a / b;
            default -> throw new IllegalArgumentException("Unknown operation: " + op);
        };
        
        return Map.of(
            "content", List.of(
                Map.of("type", "text", "text", String.valueOf(result))
            )
        );
    }
}
```

### Resource Processor (resources/get)

Extend `McpResourcesGetProcessor` for custom resource loading:

```java
package com.example.mcp;

import java.util.Map;
import org.apache.camel.BindToRegistry;
import io.dscope.camel.mcp.processor.McpResourcesGetProcessor;

@BindToRegistry("myResourceProcessor")
public class MyResourceProcessor extends McpResourcesGetProcessor {
    
    public MyResourceProcessor() {
        // Set the resource provider function
        setResourceProvider(this::loadResource);
    }
    
    private Map<String, Object> loadResource(String resourceName) {
        // Auto-detect content type using parent helpers
        if (isBinaryResource(resourceName)) {
            byte[] data = loadBinaryFromStorage(resourceName);
            String mimeType = getMimeType(resourceName);
            return blobResource("resource://" + resourceName, mimeType, data);
        }
        
        if (isTextResource(resourceName)) {
            String content = loadTextFromStorage(resourceName);
            String mimeType = getMimeType(resourceName);
            return textResource("resource://" + resourceName, mimeType, content);
        }
        
        // JSON or structured data
        Map<String, Object> data = loadJsonFromStorage(resourceName);
        if (data == null) {
            return errorResource("Resource not found: " + resourceName);
        }
        return data;
    }
    
    private byte[] loadBinaryFromStorage(String name) {
        // Load from database, S3, filesystem, etc.
        return new byte[0];
    }
    
    private String loadTextFromStorage(String name) {
        // Load text content
        return "";
    }
    
    private Map<String, Object> loadJsonFromStorage(String name) {
        // Load JSON data
        return Map.of("name", name, "description", "Example resource");
    }
}
```

## Defining Tools (methods.yaml)

Create `src/main/resources/mcp/methods.yaml` to define your tool catalog:

```yaml
methods:
  - name: greet
    title: Greeting Tool
    description: Returns a personalized greeting message.
    inputSchema:
      type: object
      additionalProperties: false
      properties:
        name:
          type: string
          description: Name of the person to greet.
      required:
        - name
    outputSchema:
      type: object
      properties:
        content:
          type: array
    annotations:
      categories:
        - utility

  - name: calculate
    title: Calculator Tool
    description: Performs basic arithmetic operations.
    inputSchema:
      type: object
      additionalProperties: false
      properties:
        a:
          type: integer
          description: First operand.
        b:
          type: integer
          description: Second operand.
        operation:
          type: string
          enum: [add, subtract, multiply, divide]
          description: Operation to perform.
          default: add
      required:
        - a
        - b
    outputSchema:
      type: object
      properties:
        content:
          type: array
    annotations:
      categories:
        - math
```

## Defining Resources (resources.yaml)

Create `src/main/resources/mcp/resources.yaml` to define your resource catalog:

```yaml
resources:
  - uri: "resource://data/config"
    name: config
    description: Application configuration settings.
    mimeType: application/json

  - uri: "resource://data/readme.md"
    name: readme.md
    description: Project documentation.
    mimeType: text/markdown

  - uri: "resource://data/logo.png"
    name: logo.png
    description: Application logo image.
    mimeType: image/png

  - uri: "resource://templates/email.html"
    name: email.html
    description: Email template.
    mimeType: text/html
```

Each resource entry includes:
- `uri` - Unique resource identifier
- `name` - Human-readable name (used in `resources/get` requests)
- `description` - Description for clients
- `mimeType` - Content type

## Built-in Processors Reference

The component provides these pre-registered processors:

| Processor | Registry Name | Purpose |
|-----------|---------------|---------|
| `McpJsonRpcEnvelopeProcessor` | `mcpJsonRpcEnvelope` | Parses JSON-RPC, extracts method/id |
| `McpHttpValidatorProcessor` | `mcpHttpValidator` | Validates Accept/Content-Type headers for MCP Streamable HTTP |
| `McpInitializeProcessor` | `mcpInitialize` | Handles `initialize` method |
| `McpPingProcessor` | `mcpPing` | Handles `ping` health check |
| `McpToolsListProcessor` | `mcpToolsList` | Returns tool catalog from `methods.yaml` |
| `McpResourcesListProcessor` | `mcpResourcesList` | Returns resource catalog from `resources.yaml` |
| `McpResourcesGetProcessor` | `mcpResourcesGet` | Base class for resource handling |
| `McpResourcesReadProcessor` | `mcpResourcesRead` | Reads resources by URI from catalog |
| `McpErrorProcessor` | `mcpError` | Formats JSON-RPC error responses |
| `McpNotificationProcessor` | `mcpNotification` | Handles notification messages |
| `McpNotificationAckProcessor` | `mcpNotificationAck` | Generic notification acknowledgement (204) |
| `McpRequestSizeGuardProcessor` | `mcpRequestSizeGuard` | Validates request size limits |
| `McpRateLimitProcessor` | `mcpRateLimit` | Applies rate limiting |
| `McpHealthStatusProcessor` | `mcpHealthStatus` | Returns health status with rate limiter snapshot |
| `McpStreamProcessor` | `mcpStream` | SSE handshake for streaming transport |
| `McpUiInitializeProcessor` | `mcpUiInitialize` | Creates UI sessions |
| `McpUiMessageProcessor` | `mcpUiMessage` | Handles UI messages |
| `McpUiUpdateModelContextProcessor` | `mcpUiUpdateModelContext` | Updates model context |
| `McpUiToolsCallProcessor` | `mcpUiToolsCall` | Validates session before tool execution |

## Base Processor Classes

### AbstractMcpProcessor

Base class providing common utilities:

```java
// Get JSON-RPC metadata
Object id = getJsonRpcId(exchange);
String method = getJsonRpcMethod(exchange);
String type = getJsonRpcType(exchange);
String toolName = getToolName(exchange);

// Get request parameters
Map<String, Object> params = getRequestParameters(exchange);

// Write JSON response
writeJson(exchange, Map.of("key", "value"));
applyJsonResponseHeaders(exchange, 200);
```

### AbstractMcpResponseProcessor

For handlers that produce responses:

```java
@Override
protected void handleResponse(Exchange exchange) throws Exception {
    Map<String, Object> result = processRequest(exchange);
    writeResult(exchange, result);  // Wraps in JSON-RPC envelope
}

// Or for errors:
writeError(exchange, Map.of(
    "code", -32603,
    "message", "Internal error"
), 500);
```

### McpResourcesGetProcessor

For resource handlers with content type detection:

```java
// Check content type
boolean isBinary = isBinaryResource("image.png");  // true
boolean isText = isTextResource("style.css");       // true
String mime = getMimeType("script.js");             // "application/javascript"

// Build responses
Map<String, Object> blob = blobResource(uri, "image/png", bytes);
Map<String, Object> text = textResource(uri, "text/html", content);
Map<String, Object> json = jsonResource(uri, dataMap);
Map<String, Object> err = errorResource("Not found");
```

## Running Your Service

### With YAML Routes

```bash
mvn exec:java -Dexec.mainClass=com.example.mcp.MyMcpApplication
```

### With Specific Route Pattern

```bash
mvn exec:java \
  -Dexec.mainClass=com.example.mcp.MyMcpApplication \
  -Dcamel.main.routesIncludePattern=classpath:routes/mcp-http.yaml
```

### Testing

```bash
# Initialize
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"test","version":"1.0.0"}}}'

# Call your tool
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"greet","arguments":{"name":"Alice"}}}'
```

## Best Practices

1. **Use `@BindToRegistry`** - Register processors automatically with Camel's registry
2. **Extend base classes** - Use `AbstractMcpResponseProcessor` for consistent JSON-RPC handling
3. **Define tools in YAML** - Keep `methods.yaml` as the source of truth for tool definitions
4. **Handle errors gracefully** - Use `onException` blocks and `mcpError` processor
5. **Add rate limiting** - Use `mcpRateLimit` processor for production services
6. **Validate input** - Check parameters before processing
7. **Use content type detection** - Leverage `isBinaryResource()`, `getMimeType()` helpers
8. **Try the consumer first** - For simple servers, `from("mcp:...")` is easier than manual Undertow routes

## Catalog APIs

### McpMethodCatalog

Registered as `mcpMethodCatalog` via `@BindToRegistry`. Loads tool definitions from `classpath:mcp/methods.yaml`.

```java
// List all tools
List<McpMethodDefinition> tools = mcpMethodCatalog.list();

// Find a specific tool
McpMethodDefinition tool = mcpMethodCatalog.findByName("echo");
```

### McpResourceCatalog

Registered as `mcpResourceCatalog` via `@BindToRegistry`. Loads resource definitions from `classpath:mcp/resources.yaml`.

```java
// List all resources
List<McpResourceDefinition> resources = mcpResourceCatalog.list();

// Find by URI
McpResourceDefinition res = mcpResourceCatalog.findByUri("resource://data/config");

// Check existence
boolean exists = mcpResourceCatalog.hasResource("resource://data/config");
```

## Karavan Metadata Generation

Generate visual designer metadata for [Apache Karavan](https://camel.apache.org/camel-karavan/):

```bash
mvn -Pkaravan-metadata compile exec:java
```

This runs `McpKaravanMetadataGenerator` which produces files under `src/main/resources/karavan/metadata/`. Regenerate after adding new MCP methods or changing component properties.

The generator is located at `src/main/java/io/dscope/tools/karavan/McpKaravanMetadataGenerator.java`.
