# Camel MCP Component

## Overview
`camel-mcp` is a general-purpose [Model Context Protocol (MCP)](https://modelcontextprotocol.io) component for Apache Camel 4.
It allows routes to act as MCP clients or servers while speaking JSON-RPC 2.0 over HTTP.

### Requirements
- Java 21+
- Maven 3.9+
- Apache Camel 4.15.0 (managed via the project `pom.xml`)

### Example
The tests and sample runner load Camel YAML DSL files. A minimal client route looks like:

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

### Features
- Supports core MCP JSON-RPC methods: `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call`.
- Simple HTTP and WebSocket transports with automatic JSON-RPC envelope handling.
- Extensible configuration for future streaming (SSE / WS) and custom processors.

### Extensibility
- Extend `io.dscope.camel.mcp.processor.AbstractMcpRequestProcessor` for custom request handling. It normalizes JSON-RPC metadata and exposes a template method with the already decoded parameters.
- Extend `io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor` for JSON-RPC responses. It provides helpers for writing result/error envelopes and applying standard protocol headers.
- Notification flows can reuse `McpNotificationProcessor` to populate exchange properties, then branch to resource-specific processors built on these base classes.

### Build
```bash
mvn clean install
```

The integration test boots an Undertow mock server route defined under `src/test/resources/routes`. During the build Maven will start and stop this embedded server automatically.

### Resources/Get Usage

The component exposes a convenience route for retrieving static resources via the sample service:

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

With the sample service running (see below), the exchange body returns the JSON contents stored under `samples/mcp-service/src/main/resources/data/example-resource.json`.

### Running the Samples

```bash
# Core component smoke test
mvn exec:java -Dexec.mainClass=org.apache.camel.main.Main

# Sample MCP service (HTTP + WebSocket)
mvn -f samples/mcp-service/pom.xml exec:java
```

When the HTTP sample service is active you can exercise the endpoints with `curl`:

```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"res-1","method":"resources/get","params":{"resource":"example-resource"}}' \
  http://localhost:8080/mcp
```

With the sample running you'll have HTTP endpoints on `http://localhost:8080` and WebSocket helpers on `ws://localhost:8090/mcp`. Add `-Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-ws.yaml` if you need the WebSocket helpers alone, or point tooling at `samples/mcp-service/target/openapi/mcp-service.yaml` (generated via `mvn package`) for the HTTP contract. Custom tools are declared in `samples/mcp-service/src/main/resources/mcp/methods.yaml`; they flow straight into the `tools/list` handler without extra wiring.

For an interactive workflow import the HTTP collection (`Camel-MCP-Sample.postman_collection.json`) and environment from `samples/mcp-service/postman/`, then run the provided `resources/get`, `ping`, `tools/list`, and `tools/call` requests. To try the WebSocket variant, import `Camel-MCP-WebSocket.postman_collection.json` with its environment or connect with a client such as `npx wscat -c ws://localhost:8090/mcp` and send MCP JSON-RPC envelopes directly.
