# 🧠 Architecture

## Component Model

The MCP component follows the standard Camel pattern:

**Component → Endpoint → Producer/Consumer**

- **McpComponent**: Creates endpoints from URIs (`mcp:http://...`)
- **McpEndpoint**: Holds configuration and creates producers or consumers
- **McpProducer**: Sends MCP requests to remote servers (client mode)
- **McpConsumer**: Receives MCP requests from clients (server mode)

## Consumer Architecture (Server Mode)

The `McpConsumer` creates a server endpoint that listens for incoming MCP requests:

```
┌─────────────────────────────────────────────────────────────┐
│                     MCP Consumer Flow                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  HTTP/WebSocket Request                                     │
│         ↓                                                    │
│  ┌──────────────────────────────┐                          │
│  │  Undertow Server             │                          │
│  │  (HTTP or WebSocket)         │                          │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  McpRequestSizeGuardProcessor│  Validate request size   │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  McpHttpValidatorProcessor   │  Check headers (HTTP)    │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  McpRateLimitProcessor       │  Apply rate limits       │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  McpJsonRpcEnvelopeProcessor │  Parse JSON-RPC          │
│  │                               │  Extract method/params   │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  User Processor              │  Custom business logic   │
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  ┌──────────────────────────────┐                          │
│  │  JSON Serialization          │  Convert response to JSON│
│  └──────────────┬───────────────┘                          │
│                 ↓                                           │
│  HTTP/WebSocket Response                                   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Exchange Properties Set by Consumer

The `McpJsonRpcEnvelopeProcessor` normalizes incoming JSON-RPC envelopes and stores metadata on the exchange:

- `mcp.jsonrpc.type`: REQUEST, NOTIFICATION, or RESPONSE
- `mcp.jsonrpc.id`: Request ID (for responses)
- `mcp.jsonrpc.method`: MCP method name (e.g., "tools/list")
- `mcp.tool.name`: Tool name (for tools/call requests)
- `mcp.notification.type`: Notification type (for notifications)

User processors can access these properties to implement method-specific logic.

## Producer Architecture (Client Mode)

The `McpProducer` sends requests to remote MCP servers. The exchange body should contain a Map with the request parameters.

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

### `resources/list`

Lists all available resources the server exposes. No parameters required.

Returns array of resource descriptors with `uri`, `name`, `description`, and `mimeType`.

### `resources/get`

Fetches a resource by name or URI.

| Field | Description |
|-------|-------------|
| `params.resource` | Resource identifier |

Returns resource content (format depends on type - see below).

### `resources/read`

Reads a resource by URI from the resource catalog. Similar to `resources/get` but uses URI-based lookup via `McpResourceCatalog`.

| Field | Description |
|-------|-------------|
| `params.uri` | Resource URI to read |

Returns resource contents array with `uri`, `mimeType`, and `text` or `blob`.

### `health`

Returns server health status including optional rate limiter statistics.

No parameters required. Returns `{ "status": "ok" }` with optional `rateLimiter` snapshot.

### `stream`

Provides SSE (Server-Sent Events) handshake for streaming transport.

Returns `:ok\n\n` with `text/event-stream` content type.

## MCP Apps Bridge Methods

The component implements the [MCP Apps Bridge](https://modelcontextprotocol.io/specification/2025-06-18/client/apps-bridge) specification for embedding interactive UIs within AI agent workflows.

### `ui/initialize`

Establishes a UI session for embedded interface communication.

| Field | Description |
|-------|-------------|
| `params.clientInfo` | UI client name and version |
| `params.resourceUri` | URI of the resource to display |
| `params.toolName` | Associated tool name (optional) |

Returns `sessionId`, `hostInfo`, and `capabilities`.

### `ui/message`

Sends a message from the embedded UI to the host.

| Field | Description |
|-------|-------------|
| `params.sessionId` | Session ID from `ui/initialize` |
| `params.type` | Message type (e.g., `user-action`) |
| `params.payload` | Message-specific data |

Returns `{ "acknowledged": true }`.

### `ui/update-model-context`

Updates the AI model's context with UI state.

| Field | Description |
|-------|-------------|
| `params.sessionId` | Session ID from `ui/initialize` |
| `params.context` | Context data to update |
| `params.mode` | Update mode: `merge` or `replace` |

Returns `{ "acknowledged": true, "mode": "merge" }`.

### `ui/tools/call`

Executes a tool within a UI session context.

| Field | Description |
|-------|-------------|
| `params.sessionId` | Session ID from `ui/initialize` |
| `params.name` | Tool name to invoke |
| `params.arguments` | Tool-specific arguments |

Returns tool execution result. The session is validated before execution.

## Method Processors

- **`initialize`** and **`ping`** respond with canned results for connectivity checks.

- **`resources/list`** returns the resource catalog loaded from `mcp/resources.yaml`. Each resource includes `uri`, `name`, `description`, and `mimeType`.

- **`resources/get`** delegates to a configurable processor bean (default: `mcpResourcesGet`). The core `McpResourcesGetProcessor` provides:
  - Automatic content type detection (binary vs text vs JSON)
  - MIME type resolution for 40+ file extensions
  - Static helpers: `blobResource()`, `textResource()`, `jsonResource()`
  
  The sample service provides `SampleResourcesGetProcessor` which loads resources from `samples/mcp-service/src/main/resources/data/` with support for:
  - **Binary**: images (jpg, png, gif, webp, svg), PDFs, fonts, archives → `{"uri", "mimeType", "blob": "base64..."}`
  - **Text**: html, css, js, ts, md, xml, yaml, source code → `{"uri", "mimeType", "text": "..."}`
  - **JSON**: files without extension (appends `.json`) → structured data

- **`tools/list`** and **`tools/call`** serve tool definitions and executions. Tool metadata loads from `classpath:mcp/methods.yaml`.

- **UI methods** (`ui/initialize`, `ui/message`, `ui/update-model-context`, `ui/tools/call`) are handled by dedicated processors:
  - `McpUiInitializeProcessor` - Creates UI sessions with unique IDs, stores in `McpUiSessionRegistry`
  - `McpUiMessageProcessor` - Validates session and acknowledges messages
  - `McpUiUpdateModelContextProcessor` - Updates model context with merge/replace modes
  - `McpUiToolsCallProcessor` - Validates session before delegating to tool processor
  
  Sessions are managed by `McpUiSessionRegistry` with configurable TTL (default: 1 hour).

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
2. `mcpHttpValidator` - Validates HTTP headers (Accept, Content-Type) for MCP Streamable HTTP transport (HTTP only)
3. `mcpRateLimit` - Applies rate limiting
4. `mcpJsonRpcEnvelope` - Parses JSON-RPC envelope, extracts method
5. Choice block - Routes to method-specific processor
6. Response serialization

## Generated Artifacts & Tooling

### Camel Component Descriptor

The `camel-package-maven-plugin` generates standard Camel component metadata from `@UriEndpoint` annotations:

```
src/generated/
├── resources/META-INF/io/dscope/camel/mcp/mcp.json   # Component JSON descriptor
└── java/.../
    ├── McpEndpointConfigurer.java      # Auto-configurer for endpoint properties
    ├── McpComponentConfigurer.java     # Auto-configurer for component properties
    └── McpEndpointUriFactory.java      # URI factory for endpoint URIs
```

The `mcp.json` descriptor is the authoritative property catalog, enabling IDE autocompletion, documentation generation, and property validation.

### Apache Karavan Metadata

The `karavan-metadata` Maven profile generates visual designer metadata:

```
src/main/resources/karavan/metadata/
├── component/mcp.json         # Component descriptor with method enums
├── mcp-methods.json            # 13 request + 3 notification methods catalog
├── kamelet/mcp-rest-service.json   # REST kamelet descriptor
├── kamelet/mcp-ws-service.json     # WebSocket kamelet descriptor
└── model-labels.json           # Human-friendly labels for UI
```

### AsciiDoc Documentation

`src/main/docs/mcp-component.adoc` follows Camel's standard component AsciiDoc format with auto-updated option tables.

## Extensibility

Both the library and sample service reuse the same base classes (`AbstractMcpRequestProcessor`, `AbstractMcpResponseProcessor`) so you can plug in custom logic while keeping JSON-RPC framing consistent. These building blocks let you extend MCP coverage with your own processors while reusing the shared HTTP transport and JSON serialization.

