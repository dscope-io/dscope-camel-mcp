# 🧠 Architecture

Component → Endpoint → Producer/Consumer.

The `McpJsonRpcEnvelopeProcessor` normalizes incoming JSON-RPC envelopes and stores metadata (method, id, notification type) on the exchange. From there, Camel choice blocks route to feature-specific processors.

## Supported MCP Methods

All methods use JSON-RPC 2.0 format. Requests are sent via HTTP (`POST /mcp`) or WebSocket (`ws://host:port/mcp`).

### `initialize`

Establishes an MCP session between client and server.

| Field | Description |
|-------|-------------|
| `params.protocolVersion` | MCP protocol version (e.g., `2024-11-05`) |
| `params.clientInfo` | Client name and version |
| `params.capabilities` | Requested capabilities |

Returns server info and supported capabilities.

### `ping`

Health check endpoint. No parameters required. Returns empty result `{}`.

### `tools/list`

Lists all available tools the server exposes. No parameters required.

Returns array of tool definitions with `name`, `description`, and `inputSchema`.

### `tools/call`

Executes a tool by name with provided arguments.

| Field | Description |
|-------|-------------|
| `params.name` | Tool name to invoke |
| `params.arguments` | Tool-specific arguments object |

Returns execution result in `content` array.

### `resources/get`

Fetches a resource by name or URI.

| Field | Description |
|-------|-------------|
| `params.resource` | Resource identifier |

Returns resource content (format depends on type - see below).

## Method Processors

- **`initialize`** and **`ping`** respond with canned results for connectivity checks.

- **`resources/get`** delegates to a configurable processor bean (default: `mcpResourcesGet`). The core `McpResourcesGetProcessor` provides:
  - Automatic content type detection (binary vs text vs JSON)
  - MIME type resolution for 40+ file extensions
  - Static helpers: `blobResource()`, `textResource()`, `jsonResource()`
  
  The sample service provides `SampleResourcesGetProcessor` which loads resources from `samples/mcp-service/src/main/resources/data/` with support for:
  - **Binary**: images (jpg, png, gif, webp, svg), PDFs, fonts, archives → `{"uri", "mimeType", "blob": "base64..."}`
  - **Text**: html, css, js, ts, md, xml, yaml, source code → `{"uri", "mimeType", "text": "..."}`
  - **JSON**: files without extension (appends `.json`) → structured data

- **`tools/list`** and **`tools/call`** serve tool definitions and executions. Tool metadata loads from `classpath:mcp/methods.yaml`.

## Transport Layer

The component supports two transport protocols:

### HTTP Transport

- **Endpoint**: `POST http://host:8080/mcp`
- **Pattern**: Request/response
- **Use case**: Simple integrations, REST-style clients, stateless requests
- **Route file**: `mcp-http-service.camel.yaml`

### WebSocket Transport

- **Endpoint**: `ws://host:8090/mcp`
- **Pattern**: Persistent bidirectional connection
- **Use case**: Agent sessions, streaming, real-time notifications
- **Route file**: `mcp-ws-service.camel.yaml`

WebSocket configuration via Undertow:

```yaml
from:
  uri: "undertow:ws://0.0.0.0:8090/mcp?sendToAll=false&allowedOrigins=*&exchangePattern=InOut"
```

| Option | Value | Purpose |
|--------|-------|--------|
| `sendToAll` | `false` | Response goes only to originating client |
| `allowedOrigins` | `*` | CORS policy (restrict in production) |
| `exchangePattern` | `InOut` | Enable request/response semantics |

Both transports share the same processor pipeline:
1. `mcpRequestSizeGuard` - Validates request size limits
2. `mcpRateLimit` - Applies rate limiting
3. `mcpJsonRpcEnvelope` - Parses JSON-RPC envelope, extracts method
4. Choice block - Routes to method-specific processor
5. Response serialization

## Extensibility

Both the library and sample service reuse the same base classes (`AbstractMcpRequestProcessor`, `AbstractMcpResponseProcessor`) so you can plug in custom logic while keeping JSON-RPC framing consistent. These building blocks let you extend MCP coverage with your own processors while reusing the shared HTTP transport and JSON serialization.

