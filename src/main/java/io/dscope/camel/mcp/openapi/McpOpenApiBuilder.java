package io.dscope.camel.mcp.openapi;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds a static OpenAPI definition describing the MCP HTTP endpoints exposed by the component samples.
 */
public class McpOpenApiBuilder {

    private String title = "Camel MCP Service";
    private String version = "1.0.0";
    private String description = "OpenAPI definition for the Camel MCP HTTP facade.";
    private final List<ServerEntry> servers = new ArrayList<>();
    private boolean includeWebSocketHelpers = true;

    public McpOpenApiBuilder withTitle(String value) {
        this.title = requireNonBlank(value, "title");
        return this;
    }

    public McpOpenApiBuilder withVersion(String value) {
        this.version = requireNonBlank(value, "version");
        return this;
    }

    public McpOpenApiBuilder withDescription(String value) {
        this.description = requireNonBlank(value, "description");
        return this;
    }

    public McpOpenApiBuilder addServer(String url, String serverDescription) {
        servers.add(new ServerEntry(requireNonBlank(url, "url"), serverDescription == null ? "" : serverDescription));
        return this;
    }

    public McpOpenApiBuilder includeWebSocketHelpers(boolean enabled) {
        this.includeWebSocketHelpers = enabled;
        return this;
    }

    public String buildYaml() {
        StringBuilder yaml = new StringBuilder();
        yaml.append("openapi: 3.0.3\n");
        yaml.append("info:\n");
        yaml.append("  title: ").append(escape(title)).append('\n');
        yaml.append("  version: ").append(escape(version)).append('\n');
        yaml.append("  description: |").append('\n');
        yaml.append(indentBlock(description, 4)).append('\n');
        List<ServerEntry> entries = servers.isEmpty() ? defaultServers() : servers;
        yaml.append("servers:\n");
        for (ServerEntry entry : entries) {
            yaml.append("  - url: ").append(escape(entry.url())).append('\n');
            if (!entry.description().isBlank()) {
                yaml.append("    description: ").append(escape(entry.description())).append('\n');
            }
        }
        yaml.append("paths:\n");
        appendHttpPath(yaml);
        appendStreamPath(yaml);
        appendHealthPath(yaml);
        if (includeWebSocketHelpers) {
            appendNotificationPath(yaml);
        }
        appendComponents(yaml);
        return yaml.toString();
    }

    public void writeYaml(Path outputPath) {
        Objects.requireNonNull(outputPath, "outputPath");
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, buildYaml());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write OpenAPI spec to " + outputPath, e);
        }
    }

    private void appendHttpPath(StringBuilder yaml) {
        yaml.append("  /mcp:\n");
        yaml.append("    post:\n");
        yaml.append("      summary: JSON-RPC entrypoint\n");
        yaml.append("      operationId: postMcp\n");
        yaml.append("      requestBody:\n");
        yaml.append("        required: true\n");
        yaml.append("        content:\n");
        yaml.append("          application/json:\n");
        yaml.append("            schema:\n");
        yaml.append("              $ref: '#/components/schemas/JsonRpcRequest'\n");
        yaml.append("      responses:\n");
        yaml.append("        '200':\n");
        yaml.append("          description: Successful JSON-RPC response\n");
        yaml.append("          content:\n");
        yaml.append("            application/json:\n");
        yaml.append("              schema:\n");
        yaml.append("                $ref: '#/components/schemas/JsonRpcResponse'\n");
        yaml.append("        '400':\n");
        yaml.append("          description: Invalid request payload\n");
        yaml.append("          content:\n");
        yaml.append("            application/json:\n");
        yaml.append("              schema:\n");
        yaml.append("                $ref: '#/components/schemas/JsonRpcError'\n");
        yaml.append("        '429':\n");
        yaml.append("          description: Rate limit exceeded\n");
        yaml.append("          content:\n");
        yaml.append("            application/json:\n");
        yaml.append("              schema:\n");
        yaml.append("                $ref: '#/components/schemas/JsonRpcError'\n");
    }

    private void appendStreamPath(StringBuilder yaml) {
        yaml.append("  /mcp/stream:\n");
        yaml.append("    get:\n");
        yaml.append("      summary: Stream events from the MCP server\n");
        yaml.append("      operationId: getMcpStream\n");
        yaml.append("      responses:\n");
        yaml.append("        '200':\n");
        yaml.append("          description: Stream established\n");
        yaml.append("          content:\n");
        yaml.append("            text/event-stream:\n");
        yaml.append("              schema:\n");
        yaml.append("                $ref: '#/components/schemas/StreamChunk'\n");
    }

    private void appendHealthPath(StringBuilder yaml) {
        yaml.append("  /mcp/health:\n");
        yaml.append("    get:\n");
        yaml.append("      summary: Health status\n");
        yaml.append("      operationId: getMcpHealth\n");
        yaml.append("      responses:\n");
        yaml.append("        '200':\n");
        yaml.append("          description: Health information\n");
        yaml.append("          content:\n");
        yaml.append("            application/json:\n");
        yaml.append("              schema:\n");
        yaml.append("                $ref: '#/components/schemas/HealthStatus'\n");
    }

    private void appendNotificationPath(StringBuilder yaml) {
        yaml.append("  /mcp/notifications:\n");
        yaml.append("    post:\n");
        yaml.append("      summary: Broadcast a server initiated notification\n");
        yaml.append("      operationId: postMcpNotifications\n");
        yaml.append("      requestBody:\n");
        yaml.append("        required: true\n");
        yaml.append("        content:\n");
        yaml.append("          application/json:\n");
        yaml.append("            schema:\n");
        yaml.append("              $ref: '#/components/schemas/JsonRpcRequest'\n");
        yaml.append("      responses:\n");
        yaml.append("        '202':\n");
        yaml.append("          description: Notification accepted for delivery\n");
    }

    private void appendComponents(StringBuilder yaml) {
        yaml.append("components:\n");
        yaml.append("  schemas:\n");
        yaml.append("    JsonRpcRequest:\n");
        yaml.append("      type: object\n");
        yaml.append("      required:\n");
        yaml.append("        - jsonrpc\n");
        yaml.append("        - method\n");
        yaml.append("      properties:\n");
        yaml.append("        jsonrpc:\n");
        yaml.append("          type: string\n");
        yaml.append("          enum: ['2.0']\n");
        yaml.append("        id:\n");
        yaml.append("          oneOf:\n");
        yaml.append("            - type: string\n");
        yaml.append("            - type: integer\n");
        yaml.append("          description: Correlation identifier for requests\n");
        yaml.append("        method:\n");
        yaml.append("          type: string\n");
        yaml.append("        params:\n");
        yaml.append("          type: object\n");
        yaml.append("          additionalProperties: true\n");
        yaml.append("    JsonRpcResponse:\n");
        yaml.append("      type: object\n");
        yaml.append("      required:\n");
        yaml.append("        - jsonrpc\n");
        yaml.append("      properties:\n");
        yaml.append("        jsonrpc:\n");
        yaml.append("          type: string\n");
        yaml.append("          enum: ['2.0']\n");
        yaml.append("        id:\n");
        yaml.append("          oneOf:\n");
        yaml.append("            - type: string\n");
        yaml.append("            - type: integer\n");
        yaml.append("        result:\n");
        yaml.append("          type: object\n");
        yaml.append("          additionalProperties: true\n");
        yaml.append("        error:\n");
        yaml.append("          $ref: '#/components/schemas/JsonRpcError'\n");
        yaml.append("    JsonRpcError:\n");
        yaml.append("      type: object\n");
        yaml.append("      required:\n");
        yaml.append("        - code\n");
        yaml.append("        - message\n");
        yaml.append("      properties:\n");
        yaml.append("        code:\n");
        yaml.append("          type: integer\n");
        yaml.append("        message:\n");
        yaml.append("          type: string\n");
        yaml.append("        data:\n");
        yaml.append("          type: object\n");
        yaml.append("          additionalProperties: true\n");
        yaml.append("    StreamChunk:\n");
        yaml.append("      type: object\n");
        yaml.append("      description: Representation of a server initiated stream payload\n");
        yaml.append("      properties:\n");
        yaml.append("        event:\n");
        yaml.append("          type: string\n");
        yaml.append("        data:\n");
        yaml.append("          type: string\n");
        yaml.append("          description: Payload encoded as JSON string\n");
        yaml.append("    HealthStatus:\n");
        yaml.append("      type: object\n");
        yaml.append("      required:\n");
        yaml.append("        - status\n");
        yaml.append("      properties:\n");
        yaml.append("        status:\n");
        yaml.append("          type: string\n");
        yaml.append("          description: Overall component status\n");
        yaml.append("        checks:\n");
        yaml.append("          type: array\n");
        yaml.append("          items:\n");
        yaml.append("            type: object\n");
        yaml.append("            properties:\n");
        yaml.append("              name:\n");
        yaml.append("                type: string\n");
        yaml.append("              status:\n");
        yaml.append("                type: string\n");
        yaml.append("              details:\n");
        yaml.append("                type: object\n");
        yaml.append("                additionalProperties: true\n");
    }

    private List<ServerEntry> defaultServers() {
        List<ServerEntry> defaults = new ArrayList<>();
        defaults.add(new ServerEntry("http://localhost:8080", "HTTP sample"));
        defaults.add(new ServerEntry("http://localhost:8090", "WebSocket sample"));
        return defaults;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static String escape(String value) {
        if (value.contains(":")) {
            return '"' + value.replace("\"", "\\\"") + '"';
        }
        return value;
    }

    private static String indentBlock(String text, int spaces) {
        String indent = " ".repeat(spaces);
        return text.lines()
                .map(line -> indent + line.replaceAll("\\s+$", ""))
                .reduce((first, second) -> first + '\n' + second)
                .orElse(indent + "");
    }

    private record ServerEntry(String url, String description) { }
}
