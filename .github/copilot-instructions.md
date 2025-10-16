# Camel MCP Component - AI Coding Instructions

## Architecture Overview

This is an Apache Camel 4 component that implements the Model Context Protocol (MCP) for AI agent integration. The component follows Camel's standard plugin architecture:

- **Component Registration**: `src/main/resources/META-INF/services/org/apache/camel/component/mcp` registers `McpComponent`
- **Protocol Flow**: Routes act as MCP clients sending JSON-RPC 2.0 requests (`initialize`, `tools/list`, `tools/call`)
- **Transport**: HTTP-based communication using Camel's HTTP components internally

### Key Files to Understand
- `McpComponent.java` - Entry point, creates endpoints from URIs like `mcp:http://localhost:8080/mcp?method=initialize`
- `McpEndpoint.java` - Holds `McpConfiguration` and instantiates producer/consumer singletons
- `McpProducer.java` - Handles outbound MCP requests, wraps payloads in JSON-RPC format
- `McpConsumer.java` - Placeholder for inbound MCP server functionality (Undertow listener not wired yet)
- `model/` - JSON-RPC request/response POJOs using Jackson
- `CamelMcpRunner.java` - Boots a sample YAML route for local smoke tests (loads from `src/test/resources/routes`)

## Development Workflows

### Building & Testing
```bash
# Requires Java 21+
mvn clean install
mvn exec:java -Dexec.mainClass=org.apache.camel.main.Main  # Run example route
```

Use VS Code tasks: "Build with Maven" and "Run Camel MCP" for convenience. Both commands rely on Camel's YAML DSL loader, so keep `camel-yaml-dsl` on the classpath.

### Testing Pattern
Tests load YAML routes via `Main.configure().setRoutesIncludePattern(...)` (see `McpComponentTest.java`) to compose a mock server plus client route:
- `mock-mcp-server.yaml` exposes `POST /mcp` and conditionally returns canned MCP responses
- `example-mcp.yaml` triggers the component once via a `timer://` source and logs the JSON-RPC reply
Integration-style assertions are done on raw HTTP responses; spin up both routes when reproducing tests locally.

The YAML DSL examples set JSON strings with `setBody.constant` and immediately `unmarshal.json` to build the Map payload the producer expects—mirror that pattern when adding new scenarios.

## Component Conventions

### URI Format
```
mcp:targetUri?method=mcpMethod&param=value
```
- `targetUri` - HTTP endpoint of the MCP server  
- `method` - MCP JSON-RPC method (defaults to `tools/list`)

### Message Flow
1. Incoming exchange body must be a `Map` that becomes the JSON-RPC `params`; null produces an empty payload
2. Producer injects `jsonrpc: "2.0"`, random UUID `id`, and the configured `method`
3. Body is serialized with Jackson and sent using `ProducerTemplate.requestBody(...)` to the target URI
4. Response JSON is parsed into `McpResponse` and set on the OUT message; callers should extract `getResult()`

### Configuration Pattern
`McpConfiguration` uses Camel's `@UriPath` and `@UriParam` annotations for automatic parameter binding from route URIs. Validate new query parameters here so Camel tooling (autocompletion/docs) stays accurate.

### Server-Side Roadmap
`McpConsumer.doStart()` is currently a stub. The intended flow is to register an Undertow HTTP endpoint that unmarshals JSON-RPC requests, delegates to the route `Processor`, and writes an `McpResponse`. Keep this in mind when adding consumer-related code—no server transport exists yet.

When filling this in, reuse the producer's `ObjectMapper` settings so request/response schemas stay aligned. Plan to:
- Spin up Undertow via Camel's `UndertowComponent` listening on the configured URI
- Convert incoming JSON to `McpRequest`, invoke `Processor.process(exchange)`
- Serialize the `Exchange` body (expecting `McpResponse`) back to JSON before returning HTTP 200

## Key Dependencies & Integration

- **Jackson** for JSON serialization of MCP protocol messages (default `ObjectMapper`, no custom modules yet)
- **camel-http`/HTTP URIs** for the synchronous producer transport; this component piggybacks on whatever Camel endpoint backs the target URI
- **camel-main** to bootstrap routes for tests and samples (YAML loader is configured through `RoutesIncludePattern`)
- **camel-yaml-dsl** so Camel can parse the YAML route definitions that drive the tests
- **camel-undertow** intended for server-side MCP endpoints (not implemented, safe to remove unless consumer work resumes)
- **logback-classic** (test scope) provides the SLF4J backend during Maven Surefire runs

## Publishing & Deployment

Project is configured for Maven Central publication via GitHub Actions:
- Tag format: `v1.0.0` triggers automatic release
- GPG signing and Sonatype OSSRH integration configured
- See `docs/PUBLISH_GUIDE.md` for complete deployment setup

## Extending the Component

- Add method presets by constraining `McpConfiguration.setMethod` (e.g., validate enums or expose fluent options)
- Expand request metadata via `McpRequest` if MCP spec evolves; adjust serialization in `McpProducer`
- Implement `McpConsumer.doStart()` with Undertow routing when server support is needed; ensure JSON parsing mirrors `McpProducer`
- When adding fields to responses, update `McpResponse` and the mock route payloads so tests keep passing
- Add new protocol hooks by extending the YAML route examples—e.g. create `tools/list` payloads in `example-mcp.yaml` and define matching canned responses in `mock-mcp-server.yaml`