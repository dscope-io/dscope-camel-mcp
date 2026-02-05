package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.model.McpUiSession;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Handles tools/call requests FROM embedded MCP Apps.
 * 
 * This processor validates the UI session and then delegates to the actual
 * tool execution. It also sends notifications to the UI about tool input/result.
 */
@BindToRegistry("mcpUiToolsCall")
public class McpUiToolsCallProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiToolsCallProcessor.class);

    public static final String EXCHANGE_PROPERTY_FROM_UI = "mcp.fromUi";

    private final McpUiSessionRegistry sessionRegistry;

    public McpUiToolsCallProcessor() {
        this(new McpUiSessionRegistry());
    }

    public McpUiToolsCallProcessor(McpUiSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        // Note: McpJsonRpcEnvelopeProcessor.handleUiToolsCall already:
        // 1. Extracted toolName and set EXCHANGE_PROPERTY_TOOL_NAME
        // 2. Set body to the arguments map
        // So params here IS the arguments, and toolName is in exchange property
        Map<String, Object> arguments = getRequestParameters(exchange);
        String sessionId = getSessionId(exchange);
        String toolName = getToolName(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing UI tools/call request id={} sessionId={} tool={}", 
                    getJsonRpcId(exchange), sessionId, toolName);
        }

        // Validate session
        McpUiSession session = validateSession(exchange, sessionId);
        if (session == null) {
            return; // Error already written
        }

        // Mark this as coming from a UI
        exchange.setProperty(EXCHANGE_PROPERTY_FROM_UI, true);
        exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, sessionId);

        if (toolName == null || toolName.isBlank()) {
            writeError(exchange, createError(-32602, "Missing required parameter: name"), 400);
            return;
        }

        // Notify UI about tool input (before execution)
        sessionRegistry.notifyToolInput(sessionId, toolName, arguments);

        LOG.debug("UI tools/call for session {}: tool={} args={}", sessionId, toolName, arguments);

        // Body is already set to arguments by envelope processor
        // The actual tool execution will be handled by the sample tool processor
        // After tool execution, use McpUiToolsCallPostProcessor to send result notification
    }

    private String getSessionId(Exchange exchange) {
        // Try header first (for HTTP requests)
        String sessionId = exchange.getIn().getHeader("X-MCP-Session-Id", String.class);
        if (sessionId == null) {
            // Try exchange property
            sessionId = exchange.getProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, String.class);
        }
        if (sessionId == null) {
            // Try params
            Map<String, Object> params = getRequestParameters(exchange);
            if (params != null) {
                sessionId = (String) params.get("sessionId");
            }
        }
        return sessionId;
    }

    private McpUiSession validateSession(Exchange exchange, String sessionId) {
        if (sessionId == null) {
            writeError(exchange, createError(-32001, "Session ID is required for UI tool calls. Provide X-MCP-Session-Id header or sessionId parameter."), 400);
            return null;
        }

        return sessionRegistry.getAndTouch(sessionId).orElseGet(() -> {
            writeError(exchange, createError(-32001, "Invalid or expired session: " + sessionId), 401);
            return null;
        });
    }

    private Map<String, Object> createError(int code, String message) {
        Map<String, Object> error = newResultMap();
        error.put("code", code);
        error.put("message", message);
        return error;
    }
}
