# Apache Camel MCP Component

> Apache Camel 4 extension that speaks the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) over JSON-RPC so agents and automations can call your Camel routes as tools.

## 📦 Versions

| Channel | Version | Maven Coordinate | Notes |
| --- | --- | --- | --- |
| Latest Release | 1.2.0 | `io.dscope.camel:camel-mcp:1.2.0` | Recommended for production use |
| Development Snapshot | 1.2.0 | `io.dscope.camel:camel-mcp:1.2.0` | Build from source (`mvn install`) to track `main` |

## 📋 Requirements

- Java 21+
- Maven 3.9+
- Apache Camel 4.15.0+

## 🚀 Features

- Implements core MCP JSON-RPC methods: `initialize`, `ping`, `resources/list`, `resources/get`, `tools/list`, and `tools/call`.
- **MCP Apps Bridge support**: `ui/initialize`, `ui/message`, `ui/update-model-context`, and `ui/tools/call` for embedded UI integration.
- Sends MCP traffic over standard Camel HTTP clients and exposes WebSocket helpers for streaming scenarios.
- Ships registry processors for JSON-RPC envelopes, tool catalogs, and notification workflows.
- Sample service and Postman collections to exercise MCP flows end-to-end.

📖 **[Development Guide](docs/development.md)** - Learn how to build your own MCP services with YAML and Java routes.

## 🛠 Installation

### Maven Dependency (Release)

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-mcp</artifactId>
  <version>1.2.0</version>
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

```
mcp:http://host:port/mcp?method=tools/list
```

| Option | Default | Purpose |
| --- | --- | --- |
| `method` | `tools/list` | MCP JSON-RPC method to invoke when producing |
| `configuration.*` | - | Any setters on `McpConfiguration` are available as URI parameters |

The exchange body should be a `Map` representing MCP `params`. The producer enriches it with `jsonrpc`, `id`, and the configured `method` before invoking the downstream HTTP endpoint.

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
    "hostInfo": {"name": "camel-mcp", "version": "1.2.0"},
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

```bash
# Core component smoke test
mvn exec:java -Dexec.mainClass=org.apache.camel.main.Main

# Sample MCP service (HTTP + WebSocket)
mvn -f samples/mcp-service/pom.xml exec:java
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

## 🧱 Project Layout

```
io.dscope.camel.mcp/
├── McpComponent        # Camel component entry point
├── McpEndpoint         # Holds configuration and producer/consumer instances
├── McpProducer         # Sends MCP JSON-RPC requests over HTTP
├── McpConsumer         # Planned inbound server (stub)
├── processor/          # JSON-RPC request/response helpers and tool processors
└── model/              # Jackson POJOs for MCP requests/responses
```

## 📄 License

Licensed under the Apache License 2.0. See `LICENSE` for the full text.
