# MCP Service Sample

This mini-project hosts both HTTP and WebSocket-facing MCP services with the Camel MCP component. The HTTP variant wires the processors into Undertow HTTP endpoints, while the WebSocket variant reuses the same processors over Undertow's WebSocket support. Both flows expose `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call` for experimentation, and they automatically load tool metadata from `src/main/resources/mcp/methods.yaml`.

## Prerequisites

- Java 21+
- Maven 3.9+
- A locally built copy of the component (`mvn clean install` from the repository root)

## Running the samples

```bash
# from the repository root
mvn clean install
cd samples/mcp-service
mvn compile exec:java
```

The command above now boots both the HTTP routes (`http://localhost:8080`) and the WebSocket helpers (`ws://localhost:8090/mcp`) by loading `mcp-service.yaml` and `mcp-service-ws.yaml` together. Add `-Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-ws.yaml` if you only need the WebSocket helpers.

## OpenAPI descriptor

Run `mvn package` in this module to emit `target/openapi/mcp-service.yaml`. The build wires in `McpOpenApiBuilder`, so the specification always reflects the current HTTP helper surface.

### Available endpoints

| HTTP Method | Path           | Description |
|-------------|----------------|-------------|
| POST        | `/mcp`         | JSON-RPC 2.0 endpoint implementing MCP methods |
| GET         | `/mcp/stream`  | Server-Sent Events handshake for future streaming updates |
| GET         | `/mcp/health`  | JSON health probe including rate limiter snapshot |

### WebSocket endpoint

- URL: `ws://localhost:8090/mcp`
- Behaviour: replies per-connection (no broadcast) with JSON-RPC envelopes.
- Example interaction using [`wscat`](https://www.npmjs.com/package/wscat):

	```bash
	npx wscat -c ws://localhost:8090/mcp
	> {"jsonrpc":"2.0","id":"ws-1","method":"resources/get","params":{"resource":"example-resource"}}
	< {"jsonrpc":"2.0","id":"ws-1","result":{"name":"example-resource","description":"Sample resource","chunks":[]}}
	```

Use the Postman collection in `postman/Camel-MCP-Sample.postman_collection.json` with the matching environment file to exercise the HTTP endpoint. For WebSocket tests, import `postman/Camel-MCP-WebSocket.postman_collection.json` together with its environment file, or drive the session from `wscat`, `websocat`, or any MCP-capable WebSocket client.

## Tools catalog

The sample ships with two illustrative tools declared in `src/main/resources/mcp/methods.yaml`:

- `echo` – returns the provided `text` as MCP content.
- `summarize` – trims a passage to `maxWords` for quick summaries.

Both tools are surfaced by the `tools/list` method and handled by `SampleToolCallProcessor`, which demonstrates how to build JSON-RPC envelopes for successful `tools/call` results. Unknown tools are routed to `McpErrorProcessor` for a compliant error response.

## Customisation ideas

- Extend `methods.yaml` with additional tool entries and branch on `McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TOOL_NAME` for custom logic.
- Swap Undertow with another HTTP component by editing the route URIs while keeping the processing chain intact.
- Adjust the rate limiter via `-Dmcp.rate.bucketCapacity` and `-Dmcp.rate.refillPerSecond` system properties to simulate throttling scenarios.
