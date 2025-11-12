# MCP Service Sample

This sample hosts both HTTP and WebSocket MCP services using the Camel MCP component. The HTTP route binds processors to Undertow HTTP endpoints; the WebSocket route reuses them over Undertow's WS support. Both flows expose the MCP JSON-RPC methods: `initialize`, `ping`, `resources/get`, `tools/list`, `tools/call`, plus notifications (e.g. `notifications/initialized`). Tool metadata is loaded from `src/main/resources/mcp/methods.yaml`.

## Prerequisites

- Java 21+
- Maven 3.9+
- A locally built copy of the component (`mvn clean install` from the repository root)

## Running the samples

```bash
# from the repository root
mvn clean install
mvn -f samples/mcp-service/pom.xml exec:java
```

The exec goal boots both HTTP (`http://localhost:8080/mcp`) and WebSocket (`ws://localhost:8090/mcp`) endpoints by loading the standard routes. If you only need WebSocket, pass:

```bash
mvn -f samples/mcp-service/pom.xml exec:java -Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-ws.yaml
```

### Kamelet-powered variant

Run the Kamelet template (REST + WS) instead of the raw routes:

```bash
mvn -f samples/mcp-service/pom.xml exec:java -Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-kamelet.camel.yaml
```

This starts:

* REST: `http://localhost:8080/mcp`
* WebSocket: `ws://localhost:8090/mcp`

You can change ports/paths by editing the query parameters on the Kamelet URIs inside `routes/mcp-service-kamelet.camel.yaml` (`restPort`, `wsPort`, `restContextPath`, `wsPath`).

## OpenAPI descriptor

Run `mvn package` in this module to emit `target/openapi/mcp-service.yaml`. The build wires in `McpOpenApiBuilder`, so the specification always reflects the current HTTP helper surface.

### Available endpoints

| HTTP Method | Path           | Description |
|-------------|----------------|-------------|
| POST        | `/mcp`         | JSON-RPC 2.0 endpoint implementing MCP methods |
| GET         | `/mcp/stream`  | Placeholder streaming/SSE handshake (future) |
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

## Tools catalog & behavior

Declared in `src/main/resources/mcp/methods.yaml`:

- `echo` – returns the provided `text` unmodified.
- `summarize` – trims a passage to `maxWords` (default 50). Alias: `summary`.

Dynamic resource fallback:

Any other tool name sent to `tools/call` is treated as a resource lookup. The request's `arguments.resource` (default `example-resource`) is loaded from `classpath:data/<resource>.json` and returned as the result. This logic lives in `SampleToolCallProcessor` delegating to `SampleResourceRequestProcessor` and `SampleResourceResponseProcessor`. If the JSON file is missing you'll receive an error.

Examples:

```bash
# echo
curl -s http://localhost:8080/mcp \
	-H 'Content-Type: application/json' \
	-d '{"jsonrpc":"2.0","id":"1","method":"tools/call","params":{"name":"echo","arguments":{"text":"Hello MCP"}}}'

# summarize (alias: summary)
curl -s http://localhost:8080/mcp \
	-H 'Content-Type: application/json' \
	-d '{"jsonrpc":"2.0","id":"2","method":"tools/call","params":{"name":"summarize","arguments":{"text":"A long string of words that will be shortened","maxWords":5}}}'

# resource fallback (tool name not explicitly declared)
curl -s http://localhost:8080/mcp \
	-H 'Content-Type: application/json' \
	-d '{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"resource","arguments":{"resource":"example-resource"}}}'
```

List tools:

```bash
curl -s http://localhost:8080/mcp \
	-H 'Content-Type: application/json' \
	-d '{"jsonrpc":"2.0","id":"10","method":"tools/list","params":{}}'
```

Ping:

```bash
curl -s http://localhost:8080/mcp \
	-H 'Content-Type: application/json' \
	-d '{"jsonrpc":"2.0","id":"11","method":"ping","params":{}}'
```

## Customisation ideas

- Add more tool entries to `methods.yaml` (the Java fallback will still load resources for unknown names unless you change `SampleToolCallProcessor`).
- Replace Undertow by editing endpoint schemes (e.g. `jetty` or `netty-http`); processors remain unchanged.
- Tweak rate limiting via `-Dmcp.rate.bucketCapacity` / `-Dmcp.rate.refillPerSecond` system properties.
- Introduce streaming by wiring a reactive processor for `/mcp/stream` SSE responses.

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| 404 / connection refused | Sample not started or wrong port | Re-run the `exec:java` command; verify logs show Undertow started on 8080/8090 |
| JSON error: `Unsupported jsonrpc version` | Sent version other than `2.0` | Use `"jsonrpc":"2.0"` |
| `params.name is required for tools/call` | Missing `name` field | Include a `name` inside `params` |
| Resource lookup error | JSON file not found | Add `data/<resource>.json` to classpath |
| WebSocket errors on unknown tool | Old processor version | Rebuild (`mvn clean install`) to pick up delegation logic |
