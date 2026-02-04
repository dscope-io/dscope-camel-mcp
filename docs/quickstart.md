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

## Call `resources/get`

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

## Call `resources/get` over WebSocket

```bash
npx wscat -c ws://localhost:8090/mcp
> {"jsonrpc":"2.0","id":"ws-1","method":"resources/get","params":{"resource":"example-resource"}}
< {"jsonrpc":"2.0","id":"ws-1","result":{"name":"example-resource","description":"Sample resource","chunks":[]}}
```

If you prefer another client, tools like `websocat` or the `VS Code WebSocket Client` extension work equally well.

## Test with Postman

Import the HTTP collection (`Camel-MCP-Sample.postman_collection.json`) and environment from `samples/mcp-service/postman/` to exercise `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call` without writing code. The same directory also contains a WebSocket collection (`Camel-MCP-WebSocket.postman_collection.json`) if you prefer to drive the MCP session over WS.
