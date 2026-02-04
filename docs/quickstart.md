# ⚡ Quickstart

## Build

```bash
mvn clean install
```

## Run the sample client

```bash
mvn exec:java -Dexec.mainClass=io.dscope.camel.mcp.CamelMcpRunner
```

## Start the sample MCP service (HTTP + WebSocket)

```bash
mvn -f samples/mcp-service/pom.xml exec:java
```

To run only the WebSocket routes, add `-Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-ws.yaml` to the command. Running `mvn package` in the same module generates `samples/mcp-service/target/openapi/mcp-service.yaml`, and tool metadata comes from `samples/mcp-service/src/main/resources/mcp/methods.yaml`.

## Calling MCP Methods

All MCP methods use JSON-RPC 2.0 format over HTTP (`POST http://localhost:8080/mcp`) or WebSocket (`ws://localhost:8090/mcp`).

### `initialize` - Start an MCP session

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "init-1",
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

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "init-1",
  "result": {
    "protocolVersion": "2024-11-05",
    "serverInfo": {
      "name": "camel-mcp-server",
      "version": "1.1.0"
    },
    "capabilities": {
      "tools": { "listChanged": true },
      "resources": { "subscribe": false, "listChanged": false }
    }
  }
}
```

### `ping` - Health check

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "ping-1", "method": "ping"}' \
  http://localhost:8080/mcp | jq '.'
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "ping-1",
  "result": {}
}
```

### `tools/list` - List available tools

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc": "2.0", "id": "tools-1", "method": "tools/list"}' \
  http://localhost:8080/mcp | jq '.'
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "tools-1",
  "result": {
    "tools": [
      {
        "name": "echo",
        "description": "Returns the input message",
        "inputSchema": {
          "type": "object",
          "properties": {
            "message": { "type": "string" }
          },
          "required": ["message"]
        }
      }
    ]
  }
}
```

### `tools/call` - Execute a tool

```bash
curl -s -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "call-1",
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

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "result": {
    "content": [
      {
        "type": "text",
        "text": "Hello from MCP!"
      }
    ]
  }
}
```

### `resources/get` - Fetch a resource

The service auto-detects content type based on file extension:

```bash
# JSON resource (no extension → loads .json file)
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"resources/get","params":{"resource":"example-resource"}}' \
  http://localhost:8080/mcp | jq '.'

# Text resource (html, css, js, md, etc.)
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"2","method":"resources/get","params":{"resource":"sample.html"}}' \
  http://localhost:8080/mcp | jq '.'

# Binary resource (images, PDFs, fonts → base64 encoded)
curl -s -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"3","method":"resources/get","params":{"resource":"sample-image.jpg"}}' \
  http://localhost:8080/mcp | jq '.'
```

Resources are loaded from `samples/mcp-service/src/main/resources/data/`. The response format varies by type:
- **Binary**: `{"uri": "...", "mimeType": "image/jpeg", "blob": "base64..."}`
- **Text**: `{"uri": "...", "mimeType": "text/html", "text": "content..."}`
- **JSON**: Direct structured data

## Calling Methods over WebSocket

```bash
npx wscat -c ws://localhost:8090/mcp
```

Once connected, send JSON-RPC messages:

```
# Initialize session
> {"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2024-11-05","clientInfo":{"name":"ws-client","version":"1.0.0"}}}
< {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2024-11-05","serverInfo":{...}}}

# Ping
> {"jsonrpc":"2.0","id":"2","method":"ping"}
< {"jsonrpc":"2.0","id":"2","result":{}}

# List tools
> {"jsonrpc":"2.0","id":"3","method":"tools/list"}
< {"jsonrpc":"2.0","id":"3","result":{"tools":[...]}}

# Call a tool
> {"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"echo","arguments":{"message":"Hello"}}}
< {"jsonrpc":"2.0","id":"4","result":{"content":[{"type":"text","text":"Hello"}]}}

# Get a resource
> {"jsonrpc":"2.0","id":"5","method":"resources/get","params":{"resource":"example-resource"}}
< {"jsonrpc":"2.0","id":"5","result":{"name":"example-resource",...}}
```

Alternative clients: `websocat`, VS Code WebSocket Client extension, or any WebSocket library.

## Test with Postman

Import the HTTP collection (`Camel-MCP-Sample.postman_collection.json`) and environment from `samples/mcp-service/postman/` to exercise `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call` without writing code. The same directory also contains a WebSocket collection (`Camel-MCP-WebSocket.postman_collection.json`) if you prefer to drive the MCP session over WS.
