# Architecture# 🧠 Architecture



Component -> Endpoint -> Producer/Consumer.Component → Endpoint → Producer/Consumer.



The `McpJsonRpcEnvelopeProcessor` normalizes incoming JSON-RPC envelopes and stores metadata (method, id, notification type) on the exchange. From there, Camel choice blocks route to feature-specific processors:The `McpJsonRpcEnvelopeProcessor` normalizes incoming JSON-RPC envelopes and stores metadata (method, id, notification type) on the exchange. From there, Camel choice blocks route to feature-specific processors:



- `initialize` and `ping` respond with canned results for connectivity checks.- `initialize` and `ping` respond with canned results for connectivity checks.

- `resources/get` delegates to `SampleResourceRequestProcessor` and `SampleResourceResponseProcessor`, which read request parameters and stream JSON documents from `samples/mcp-service/src/main/resources/data/`.- `resources/get` delegates to `SampleResourceRequestProcessor` and `SampleResourceResponseProcessor`, which read request parameters and stream JSON documents from `samples/mcp-service/src/main/resources/data/`.

- `tools/list` and `tools/call` serve mock tool definitions and executions for integration testing.- `tools/list` and `tools/call` serve mock tool definitions and executions for integration testing.



Both the library and sample service reuse the same base classes (`AbstractMcpRequestProcessor`, `AbstractMcpResponseProcessor`) so you can plug in custom logic while keeping JSON-RPC framing consistent. These building blocks let you extend MCP coverage with your own processors while reusing the shared HTTP transport and JSON serialization.Both the library and sample service reuse the same base classes (`AbstractMcpRequestProcessor`, `AbstractMcpResponseProcessor`) so you can plug in custom logic while keeping JSON-RPC framing consistent.

