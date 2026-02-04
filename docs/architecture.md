# 🧠 Architecture

Component → Endpoint → Producer/Consumer.

The `McpJsonRpcEnvelopeProcessor` normalizes incoming JSON-RPC envelopes and stores metadata (method, id, notification type) on the exchange. From there, Camel choice blocks route to feature-specific processors:



- `initialize` and `ping` respond with canned results for connectivity checks.

- `resources/get` delegates to a configurable processor bean (default: `mcpResourcesGet`). The core `McpResourcesGetProcessor` provides:
  - Automatic content type detection (binary vs text vs JSON)
  - MIME type resolution for 40+ file extensions
  - Static helpers: `blobResource()`, `textResource()`, `jsonResource()`
  
  The sample service provides `SampleResourcesGetProcessor` which loads resources from `samples/mcp-service/src/main/resources/data/` with support for:
  - **Binary**: images (jpg, png, gif, webp, svg), PDFs, fonts, archives
  - **Text**: html, css, js, ts, md, xml, yaml, source code files
  - **JSON**: files without extension (appends `.json`)

- `tools/list` and `tools/call` serve mock tool definitions and executions for integration testing.

Both the library and sample service reuse the same base classes (`AbstractMcpRequestProcessor`, `AbstractMcpResponseProcessor`) so you can plug in custom logic while keeping JSON-RPC framing consistent. These building blocks let you extend MCP coverage with your own processors while reusing the shared HTTP transport and JSON serialization.

