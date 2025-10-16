package io.dscope.camel.samples.mcp;

import java.nio.file.Path;

import io.dscope.camel.mcp.openapi.McpOpenApiBuilder;

/**
 * Generates an OpenAPI document for the sample MCP services.
 */
public final class McpSampleOpenApiGenerator {

    private McpSampleOpenApiGenerator() {
        // utility
    }

    public static void main(String[] args) {
        Path output = args != null && args.length > 0
                ? Path.of(args[0])
                : Path.of("target", "openapi", "mcp-service.yaml");

        new McpOpenApiBuilder()
                .withTitle("Camel MCP Sample Service")
                .withVersion("1.0.0")
                .withDescription("OpenAPI definition for the Camel MCP sample HTTP and WebSocket helpers.")
                .addServer("http://localhost:8080", "HTTP sample service")
                .addServer("http://localhost:8090", "WebSocket support endpoints")
                .writeYaml(output);
    }
}
