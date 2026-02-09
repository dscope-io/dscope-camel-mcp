# Apache Camel MCP Component

> Apache Camel 4 extension that speaks the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) over JSON-RPC so agents and automations can call your Camel routes as tools.

## 📦 Versions

| Channel | Version | Maven Coordinate | Notes |
| --- | --- | --- | --- |
| Latest Release | 1.3.0 | `io.dscope.camel:camel-mcp:1.3.0` | Recommended for production use |
| Development Snapshot | 1.3.0 | `io.dscope.camel:camel-mcp:1.3.0` | Build from source (`mvn install`) to track `main` |

## 📋 Requirements

- Java 21+
- Maven 3.9+
- Apache Camel 4.15.0+

## 🚀 Features

- **Producer (client) mode**: Send MCP JSON-RPC requests to remote servers via `to("mcp:http://host/mcp?method=tools/list")`.
- **Consumer (server) mode**: Expose MCP endpoints with `from("mcp:http://0.0.0.0:3000/mcp")` — built-in request validation, JSON-RPC parsing, rate limiting, and response serialization.
- Implements core MCP methods: `initialize`, `ping`, `resources/list`, `resources/read`, `resources/get`, `tools/list`, `tools/call`, `health`, and `stream`.
- **MCP Apps Bridge support**: `ui/initialize`, `ui/message`, `ui/update-model-context`, and `ui/tools/call` for embedded UI integration.
- **Notifications**: `notifications/initialized`, `notifications/cancelled`, `notifications/progress`.
- HTTP and WebSocket transports for both producer and consumer modes.
- Ships 20+ registry processors for JSON-RPC envelopes, tool catalogs, resource catalogs, and notification workflows.
- **Apache Karavan integration**: Generated visual designer metadata for drag-and-drop MCP route building.
- **Camel tooling support**: `@UriEndpoint`-based component descriptor generation (`mcp.json`) for IDE autocompletion and documentation.
- Two sample projects and Postman collections to exercise MCP flows end-to-end.

📖 **[Development Guide](docs/development.md)** - Learn how to build your own MCP services with YAML and Java routes.

## 🛠 Installation

### Maven Dependency (Release)

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-mcp</artifactId>
  <version>1.3.0</version>
</dependency>
```

### Build From Source (Snapshot)

```bash
git clone https://github.com/dscope-io/dscope-camel-mcp.git
cd dscope-camel-mcp
mvn clean install
```

## 🔧 Configuration

### URI Format

**Producer (Client) Mode:**
```
mcp:http://host:port/mcp?method=tools/list
```

**Consumer (Server) Mode:**
```
mcp:http://host:port/path
mcp:http://host:port/path?websocket=true
```

### Configuration Options

| Option | Default | Mode | Purpose |
| --- | --- | --- | --- |
| `method` | `tools/list` | Producer | MCP JSON-RPC method to invoke when producing |
| `websocket` | `false` | Consumer | Enable WebSocket transport instead of HTTP |
| `sendToAll` | `false` | Consumer | Broadcast WebSocket messages to all clients |
| `allowedOrigins` | `*` | Consumer | CORS allowed origins for WebSocket |
| `httpMethodRestrict` | `POST` | Consumer | Restrict HTTP methods (e.g., POST, GET) |

### Producer Mode

The exchange body should be a `Map` representing MCP `params`. The producer enriches it with `jsonrpc`, `id`, and the configured `method` before invoking the downstream HTTP endpoint.

### Consumer Mode

The consumer creates an HTTP or WebSocket server endpoint that:
1. Validates incoming requests (headers, content-type)
2. Parses JSON-RPC envelopes
3. Extracts method and parameters to exchange properties
4. Routes to your processor
5. Serializes responses to JSON

Exchange properties set by the consumer:
- `mcp.jsonrpc.type` - REQUEST, NOTIFICATION, or RESPONSE
- `mcp.jsonrpc.id` - Request ID for responses
- `mcp.jsonrpc.method` - The MCP method being called
- `mcp.tool.name` - Tool name (for tools/call)

## � WebSocket Transport

The component supports WebSocket connections for persistent, bidirectional MCP sessions. This is ideal for:
- Long-running agent sessions
- Streaming responses
- Real-time notifications

### Endpoints

| Protocol | Endpoint | Purpose |
|----------|----------|--------|
| HTTP | `http://localhost:8080/mcp` | Request/response style |
| WebSocket | `ws://localhost:8090/mcp` | Persistent bidirectional |

### WebSocket Route Configuration

The sample service configures WebSocket via Undertow:

```yaml
- route:
    id: mcp-service-ws
    from:
      uri: "undertow:ws://0.0.0.0:8090/mcp?sendToAll=false&allowedOrigins=*&exchangePattern=InOut"
```

| Option | Default | Purpose |
|--------|---------|--------|
| `sendToAll` | `false` | Send response only to originating client |
| `allowedOrigins` | `*` | CORS allowed origins (use specific domains in production) |
| `exchangePattern` | `InOut` | Enable request/response pattern |

### Connecting with wscat

```bash
# Install wscat (one-time)
npm install -g wscat

# Connect to MCP WebSocket
npx wscat -c ws://localhost:8090/mcp
```

### Alternative WebSocket Clients

- **websocat**: `websocat ws://localhost:8090/mcp`
- **VS Code**: Install "WebSocket Client" extension
- **Postman**: Import WebSocket collection from `samples/mcp-service/postman/`
- **Python**: Use `websockets` library
- **JavaScript**: Native `WebSocket` API or `ws` package

### Python Example

```python
import asyncio
import websockets
import json

async def mcp_client():
    async with websockets.connect('ws://localhost:8090/mcp') as ws:
        # Initialize session
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "id": "1",
            "method": "initialize",
            "params": {
                "protocolVersion": "2024-11-05",
                "clientInfo": {"name": "python-client", "version": "1.0.0"}
            }
        }))
        print(await ws.recv())
        
        # List tools
        await ws.send(json.dumps({
            "jsonrpc": "2.0",
            "id": "2",
            "method": "tools/list"
        }))
        print(await ws.recv())

asyncio.run(mcp_client())
```

### JavaScript/Node.js Example

```javascript
const WebSocket = require('ws');
const ws = new WebSocket('ws://localhost:8090/mcp');

ws.on('open', () => {
    // Initialize session
    ws.send(JSON.stringify({
        jsonrpc: "2.0",
        id: "1",
        method: "initialize",
        params: {
            protocolVersion: "2024-11-05",
            clientInfo: { name: "node-client", version: "1.0.0" }
        }
    }));
});

ws.on('message', (data) => {
    console.log('Received:', JSON.parse(data));
});
```

## �📚 Usage Examples

### Calling MCP Methods via curl

All MCP methods use JSON-RPC 2.0 format. Here's how to call each supported method:

#### `initialize` - Start an MCP session

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "clientInfo": {
        "name": "my-client",
        "version": "1.0.0"
      },
      "capabilities": {}
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

#### `ping` - Health check

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "2", "method": "ping"}' \
  http://localhost:8080/mcp | jq '.'
```

#### `tools/list` - List available tools

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "3", "method": "tools/list"}' \
  http://localhost:8080/mcp | jq '.'
```

#### `resources/list` - List available resources

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "4", "method": "resources/list"}' \
  http://localhost:8080/mcp | jq '.'
```

#### `tools/call` - Execute a tool

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "echo",
      "arguments": {
        "message": "Hello from MCP!"
      }
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

#### `resources/get` - Fetch a resource

```bash
# JSON resource
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "5",
    "method": "resources/get",
    "params": {
      "resource": "example-resource"
    }
  }' \
  http://localhost:8080/mcp | jq '.'

# Binary resource (returns base64)
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "6", "method": "resources/get", "params": {"resource": "sample-image.jpg"}}' \
  http://localhost:8080/mcp | jq '.'
```

### MCP Apps Bridge (UI Methods)

The component supports the [MCP Apps Bridge](https://modelcontextprotocol.io/specification/2025-06-18/client/apps-bridge) specification for embedding interactive UIs within AI agent workflows.

#### `ui/initialize` - Start a UI session

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "ui-1",
    "method": "ui/initialize",
    "params": {
      "clientInfo": {"name": "my-ui", "version": "1.0.0"},
      "resourceUri": "mcp://resource/chart-editor.html",
      "toolName": "chart-editor"
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

Response includes a `sessionId` for subsequent UI calls:
```json
{
  "result": {
    "sessionId": "abc123-...",
    "hostInfo": {"name": "camel-mcp", "version": "1.3.0"},
    "capabilities": ["tools/call", "ui/message", "ui/update-model-context"]
  }
}
```

#### `ui/tools/call` - Execute a tool from UI context

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "ui-2",
    "method": "ui/tools/call",
    "params": {
      "sessionId": "<sessionId-from-ui-initialize>",
      "name": "echo",
      "arguments": {"text": "Hello from UI!"}
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

#### `ui/message` - Send messages from embedded UI

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "ui-3",
    "method": "ui/message",
    "params": {
      "sessionId": "<sessionId>",
      "type": "user-action",
      "payload": {"action": "button-clicked"}
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

#### `ui/update-model-context` - Update AI model context

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "ui-4",
    "method": "ui/update-model-context",
    "params": {
      "sessionId": "<sessionId>",
      "context": {"chartConfig": {"type": "bar", "data": [1,2,3]}},
      "mode": "merge"
    }
  }' \
  http://localhost:8080/mcp | jq '.'
```

### Calling MCP Methods via WebSocket

```bash
npx wscat -c ws://localhost:8090/mcp

# Initialize
> {"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"ws-client","version":"1.0.0"}}}

# Ping
> {"jsonrpc":"2.0","id":"2","method":"ping"}

# List tools
> {"jsonrpc":"2.0","id":"3","method":"tools/list"}

# Call a tool
> {"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"echo","arguments":{"message":"Hello"}}}

# Get a resource
> {"jsonrpc":"2.0","id":"5","method":"resources/get","params":{"resource":"example-resource"}}
```

### Minimal YAML Client (Camel Route)

```yaml
- route:
    id: example-mcp-client
    from:
      uri: "timer://runOnce?repeatCount=1"
      steps:
        - setBody:
            constant: |
              {
                "method": "initialize",
                "params": {
                  "clientInfo": {
                    "name": "camel-mcp",
                    "version": "1.0.0"
                  }
                }
              }
        - unmarshal:
            json:
              library: Jackson
        - to:
            uri: "mcp:http://localhost:8080/mcp?method=initialize"
        - log:
            message: "MCP Response: ${body}"
```

### Resources/Get With Sample Service

The `resources/get` method supports automatic content type detection:
- **Binary** (images, PDFs, fonts) → returned as base64 `blob`
- **Text** (html, css, js, md) → returned as `text` with MIME type
- **JSON** (no extension) → returned as structured data

```yaml
- route:
    id: example-resources-client
    from:
      uri: "timer://resources?repeatCount=1"
      steps:
        - setBody:
            constant: |
              {
                "params": {
                  "resource": "example-resource"
                }
              }
        - unmarshal:
            json:
              library: Jackson
        - to:
            uri: "mcp:http://localhost:8080/mcp?method=resources/get"
        - log:
            message: "Resource payload: ${body[result]}"
```

### MCP Server (Consumer) Examples

The MCP consumer allows you to create MCP protocol servers that listen for incoming JSON-RPC requests.

#### Basic HTTP Server

```java
from("mcp:http://localhost:8080/mcp")
    .process(exchange -> {
        // Your custom MCP request processing
        String method = exchange.getProperty("mcp.jsonrpc.method", String.class);
        Map<String, Object> params = exchange.getIn().getBody(Map.class);
        
        // Process request and set response
        Map<String, Object> response = Map.of(
            "jsonrpc", "2.0",
            "id", exchange.getProperty("mcp.jsonrpc.id"),
            "result", Map.of("status", "ok")
        );
        exchange.getMessage().setBody(response);
    });
```

#### WebSocket Server

```java
from("mcp:http://localhost:8090/mcp?websocket=true")
    .process(exchange -> {
        // Process MCP requests over WebSocket
        // Response automatically serialized to JSON
    });
```

#### YAML-Based Server

```yaml
- route:
    id: mcp-server
    from:
      uri: "mcp:http://0.0.0.0:8080/mcp"
      steps:
        - choice:
            when:
              - simple: "${exchangeProperty.mcp.jsonrpc.method} == 'ping'"
                steps:
                  - setBody:
                      constant:
                        jsonrpc: "2.0"
                        result: {}
              - simple: "${exchangeProperty.mcp.jsonrpc.method} == 'tools/list'"
                steps:
                  - setBody:
                      constant:
                        jsonrpc: "2.0"
                        result:
                          tools:
                            - name: "echo"
                              description: "Echo the input"
```

The consumer automatically:
- Validates HTTP headers (Content-Type, Accept)
- Parses JSON-RPC envelopes
- Extracts method and parameters as exchange properties
- Applies rate limiting and request size guards
- Serializes response bodies to JSON

## 🤖 MCP Tooling

- `AbstractMcpRequestProcessor` and `AbstractMcpResponseProcessor` provide templates for custom tool handlers.
- `McpResourcesGetProcessor` handles resource loading with automatic content type detection and helper methods:
  - `isBinaryResource(name)` / `isTextResource(name)` — check content type
  - `getMimeType(name)` — resolve MIME type from extension
  - `blobResource(uri, mimeType, bytes)` — create binary response
  - `textResource(uri, mimeType, content)` — create text response
  - `jsonResource(uri, data)` — create JSON response
- `McpNotificationProcessor` normalizes JSON-RPC notifications and exchange properties.
- Tool catalogs load from `classpath:mcp/methods.yaml` and feed `tools/list` responses automatically.

## 🧪 Testing

```bash
mvn clean install
```

The integration test boots a mock MCP server defined in `src/test/resources/routes` via Camel Main.

## 🧰 Samples

### mcp-service (Kamelet/YAML routes)

Full-featured MCP server with Kamelet-based routing, UI Bridge, resource catalog, and OpenAPI generation.

```bash
mvn -f samples/mcp-service/pom.xml exec:java
# HTTP: http://localhost:8080/mcp  |  WebSocket: ws://localhost:8090/mcp
```

### mcp-consumer (Direct consumer URI)

Minimal MCP server demonstrating the `from("mcp:...")` consumer approach — pure Java, no YAML needed.

```bash
mvn -f samples/mcp-consumer/pom.xml exec:java
# HTTP: http://localhost:3000/mcp  |  WebSocket: ws://localhost:3001/mcp
```

See [samples/mcp-consumer/README.md](samples/mcp-consumer/README.md) for details and curl examples.

### Core component smoke test

```bash
mvn exec:java -Dexec.mainClass=org.apache.camel.main.Main
```

When the sample is running you can exercise the MCP HTTP endpoint:

```bash
# JSON resource (no extension)
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"resources/get","params":{"resource":"example-resource"}}' \
  http://localhost:8080/mcp

# HTML resource
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"2","method":"resources/get","params":{"resource":"sample.html"}}' \
  http://localhost:8080/mcp

# Binary image resource
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"3","method":"resources/get","params":{"resource":"sample-image.jpg"}}' \
  http://localhost:8080/mcp
```

HTTP endpoints listen on `http://localhost:8080`; WebSocket helpers are available on `ws://localhost:8090/mcp`. Generated OpenAPI definitions live under `samples/mcp-service/target/openapi/`. Postman collections are bundled in `samples/mcp-service/postman/` for interactive exploration.

## 🔌 IDE & Tooling Integration

### Camel Component Descriptor

The build generates a standard Camel component descriptor at `src/generated/resources/META-INF/io/dscope/camel/mcp/mcp.json`. This enables:
- IDE autocompletion for `mcp:` URIs in YAML / Java routes
- Auto-generated option tables in documentation
- Property validation via `McpEndpointConfigurer` and `McpComponentConfigurer`

Additional Camel-standard properties are exposed automatically: `bridgeErrorHandler`, `lazyStartProducer`, `exceptionHandler`, `exchangePattern`, `autowiredEnabled`.

### Apache Karavan Integration

Generate visual designer metadata for [Apache Karavan](https://camel.apache.org/camel-karavan/):

```bash
mvn -Pkaravan-metadata compile exec:java
```

This produces metadata under `src/main/resources/karavan/metadata/`:

| File | Purpose |
|------|---------|
| `component/mcp.json` | Component descriptor with all properties and method enums |
| `mcp-methods.json` | Catalog of 13 request methods + 3 notification methods |
| `kamelet/mcp-rest-service.json` | REST kamelet descriptor (port 8080) |
| `kamelet/mcp-ws-service.json` | WebSocket kamelet descriptor (port 8090) |
| `model-labels.json` | Human-friendly labels for methods and kamelets |

Regenerate after adding new MCP methods or changing component properties.

## 🧱 Project Layout

```
io.dscope.camel.mcp/
├── McpComponent        # Camel component entry point
├── McpEndpoint         # Holds configuration, creates producer/consumer (@UriEndpoint, Category.AI)
├── McpConfiguration    # URI path/param bindings with Camel annotations
├── McpProducer         # Sends MCP JSON-RPC requests to remote servers (client mode)
├── McpConsumer         # Receives MCP requests via HTTP/WebSocket (server mode)
├── processor/          # 20+ built-in processors for JSON-RPC, tools, resources, UI, notifications
├── catalog/            # McpMethodCatalog + McpResourceCatalog (loaded from YAML)
├── service/            # McpUiSessionRegistry, McpWebSocketNotifier
├── model/              # Jackson POJOs: requests, responses, resources, UI sessions, notifications
└── tools/karavan/      # McpKaravanMetadataGenerator for Karavan visual designer

samples/
├── mcp-service/        # Full-featured MCP server using Kamelets/YAML routes (port 8080/8090)
└── mcp-consumer/       # Minimal MCP server using direct consumer URI (port 3000/3001)

src/generated/          # Auto-generated component descriptors (mcp.json, configurers, URI factory)
src/main/resources/karavan/metadata/  # Generated Karavan metadata (component, kamelets, labels)
src/main/docs/mcp-component.adoc      # AsciiDoc component documentation for Camel tooling
```

## 📄 License

Licensed under the Apache License 2.0. See `LICENSE` for the full text.
