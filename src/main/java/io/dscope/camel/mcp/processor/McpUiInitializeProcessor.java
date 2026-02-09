package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.model.McpUiSession;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Handles ui/initialize requests from embedded MCP Apps.
 * 
 * Creates a session and returns the host capabilities to the app.
 */
@BindToRegistry("mcpUiInitialize")
public class McpUiInitializeProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiInitializeProcessor.class);

    public static final String EXCHANGE_PROPERTY_UI_SESSION_ID = "mcp.ui.sessionId";

    private static final String DEFAULT_HOST_NAME = "camel-mcp";
    private static final String DEFAULT_HOST_VERSION = "1.3.0";

    private static final List<String> CAPABILITIES = List.of(
            "tools/call",
            "ui/message",
            "ui/update-model-context",
            "ui/notifications/tool-input",
            "ui/notifications/tool-result");

    private final McpUiSessionRegistry sessionRegistry;
    private final String hostName;
    private final String hostVersion;

    public McpUiInitializeProcessor() {
        this(new McpUiSessionRegistry(), DEFAULT_HOST_NAME, DEFAULT_HOST_VERSION);
    }

    public McpUiInitializeProcessor(McpUiSessionRegistry sessionRegistry) {
        this(sessionRegistry, DEFAULT_HOST_NAME, DEFAULT_HOST_VERSION);
    }

    public McpUiInitializeProcessor(McpUiSessionRegistry sessionRegistry, String hostName, String hostVersion) {
        this.sessionRegistry = sessionRegistry;
        this.hostName = hostName != null ? hostName : DEFAULT_HOST_NAME;
        this.hostVersion = hostVersion != null ? hostVersion : DEFAULT_HOST_VERSION;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);

        String resourceUri = params != null ? (String) params.get("resourceUri") : null;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing ui/initialize request id={} resourceUri={}", getJsonRpcId(exchange), resourceUri);
        }

        // Create a new session
        McpUiSession session = sessionRegistry.register(resourceUri);

        // Store session ID on exchange for downstream processors
        exchange.setProperty(EXCHANGE_PROPERTY_UI_SESSION_ID, session.getSessionId());

        // Build result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getSessionId());
        result.put("hostInfo", buildHostInfo());
        result.put("capabilities", CAPABILITIES);

        writeResult(exchange, result);

        if (LOG.isDebugEnabled()) {
            LOG.debug("ui/initialize response id={} sessionId={}", getJsonRpcId(exchange), session.getSessionId());
        }
    }

    private Map<String, Object> buildHostInfo() {
        Map<String, Object> hostInfo = new LinkedHashMap<>();
        hostInfo.put("name", hostName);
        hostInfo.put("version", hostVersion);
        return hostInfo;
    }
}
