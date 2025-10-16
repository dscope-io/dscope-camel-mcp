package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Responds to MCP initialize requests with server info and capabilities.
 */
@BindToRegistry("mcpInitialize")
public class McpInitializeProcessor extends AbstractMcpResponseProcessor {

    private static final String DEFAULT_SERVER_NAME = "camel-mcp-component";
    private static final String DEFAULT_SERVER_VERSION = "dev";

    private final String serverName;
    private final String serverVersion;

    public McpInitializeProcessor() {
        this(resolveServerName(), resolveServerVersion());
    }

    McpInitializeProcessor(String serverName, String serverVersion) {
        this.serverName = Optional.ofNullable(serverName).filter(name -> !name.isBlank()).orElse(DEFAULT_SERVER_NAME);
        this.serverVersion = Optional.ofNullable(serverVersion).filter(version -> !version.isBlank()).orElse(DEFAULT_SERVER_VERSION);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);

        String protocolVersion = resolveProtocolVersion(exchange);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", protocolVersion);
        result.put("serverInfo", buildServerInfo());
        result.put("capabilities", buildCapabilities(params));

        writeResult(exchange, result);
        setProtocolHeaders(exchange, protocolVersion);
    }

    private Map<String, Object> buildServerInfo() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", serverName);
        serverInfo.put("version", serverVersion);
        return serverInfo;
    }

    private Map<String, Object> buildCapabilities(Map<String, Object> params) {
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("tools/list", Boolean.TRUE);
        capabilities.put("tools/call", Boolean.TRUE);
    capabilities.put("ping", Boolean.TRUE);
        if (params.containsKey("logging")) {
            capabilities.put("logging", Map.of());
        }
        return capabilities;
    }

    private static String resolveServerName() {
        return System.getProperty("mcp.server.name", DEFAULT_SERVER_NAME);
    }

    private static String resolveServerVersion() {
        return System.getProperty("mcp.server.version", DEFAULT_SERVER_VERSION);
    }
}
