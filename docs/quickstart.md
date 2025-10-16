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

```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"res-1","method":"resources/get","params":{"resource":"example-resource"}}' \
  http://localhost:8080/mcp | jq '.'
```

The final `jq` is optional but helpful for pretty-printing the JSON response. The service responds with the payload stored in `samples/mcp-service/src/main/resources/data/example-resource.json`.

## Call `resources/get` over WebSocket

```bash
npx wscat -c ws://localhost:8090/mcp
> {"jsonrpc":"2.0","id":"ws-1","method":"resources/get","params":{"resource":"example-resource"}}
< {"jsonrpc":"2.0","id":"ws-1","result":{"name":"example-resource","description":"Sample resource","chunks":[]}}
```

If you prefer another client, tools like `websocat` or the `VS Code WebSocket Client` extension work equally well.

## Test with Postman

Import the HTTP collection (`Camel-MCP-Sample.postman_collection.json`) and environment from `samples/mcp-service/postman/` to exercise `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call` without writing code. The same directory also contains a WebSocket collection (`Camel-MCP-WebSocket.postman_collection.json`) if you prefer to drive the MCP session over WS.
