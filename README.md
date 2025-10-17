# Apache Camel MCP Component

> Apache Camel 4 extension that speaks the [Model Context Protocol (MCP)](https://modelcontextprotocol.io) over JSON-RPC so agents and automations can call your Camel routes as tools.

## 📦 Versions

| Channel | Version | Maven Coordinate | Notes |
| --- | --- | --- | --- |
| Latest Release | 1.0.0 | `io.dscope.camel:camel-mcp:1.0.0` | Recommended for production use |
| Development Snapshot | 1.0.0 | `io.dscope.camel:camel-mcp:1.0.0` | Build from source (`mvn install`) to track `main` |

## 📋 Requirements

- Java 21+
- Maven 3.9+
- Apache Camel 4.15.0+

## 🚀 Features

- Implements core MCP JSON-RPC methods: `initialize`, `ping`, `resources/get`, `tools/list`, and `tools/call`.
- Sends MCP traffic over standard Camel HTTP clients and exposes WebSocket helpers for streaming scenarios.
- Ships registry processors for JSON-RPC envelopes, tool catalogs, and notification workflows.
- Sample service and Postman collections to exercise MCP flows end-to-end.

## 🛠 Installation

### Maven Dependency (Release)

```xml
<dependency>
  <groupId>io.dscope.camel</groupId>
  <artifactId>camel-mcp</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Build From Source (Snapshot)

```bash
git clone https://github.com/dscope-io/dscope-camel-mcp.git
cd dscope-camel-mcp
mvn clean install
```

## 🔧 Configuration

### URI Format

```
mcp:http://host:port/mcp?method=tools/list
```

| Option | Default | Purpose |
| --- | --- | --- |
| `method` | `tools/list` | MCP JSON-RPC method to invoke when producing |
| `configuration.*` | - | Any setters on `McpConfiguration` are available as URI parameters |

The exchange body should be a `Map` representing MCP `params`. The producer enriches it with `jsonrpc`, `id`, and the configured `method` before invoking the downstream HTTP endpoint.

## 📚 Usage Examples

### Minimal YAML Client

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

### Resources/Get With Sample Service

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

## 🤖 MCP Tooling

- `AbstractMcpRequestProcessor` and `AbstractMcpResponseProcessor` provide templates for custom tool handlers.
- `McpNotificationProcessor` normalizes JSON-RPC notifications and exchange properties.
- Tool catalogs load from `classpath:mcp/methods.yaml` and feed `tools/list` responses automatically.

## 🧪 Testing

```bash
mvn clean install
```

The integration test boots a mock MCP server defined in `src/test/resources/routes` via Camel Main.

## 🧰 Samples

```bash
# Core component smoke test
mvn exec:java -Dexec.mainClass=org.apache.camel.main.Main

# Sample MCP service (HTTP + WebSocket)
mvn -f samples/mcp-service/pom.xml exec:java
```

When the sample is running you can exercise the MCP HTTP endpoint:

```bash
curl -s \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"res-1","method":"resources/get","params":{"resource":"example-resource"}}' \
  http://localhost:8080/mcp
```

HTTP endpoints listen on `http://localhost:8080`; WebSocket helpers are available on `ws://localhost:8090/mcp`. Generated OpenAPI definitions live under `samples/mcp-service/target/openapi/`. Postman collections are bundled in `samples/mcp-service/postman/` for interactive exploration.

## 🧱 Project Layout

```
io.dscope.camel.mcp/
├── McpComponent        # Camel component entry point
├── McpEndpoint         # Holds configuration and producer/consumer instances
├── McpProducer         # Sends MCP JSON-RPC requests over HTTP
├── McpConsumer         # Planned inbound server (stub)
├── processor/          # JSON-RPC request/response helpers and tool processors
└── model/              # Jackson POJOs for MCP requests/responses
```

## 📄 License

Licensed under the Apache License 2.0. See `LICENSE` for the full text.
