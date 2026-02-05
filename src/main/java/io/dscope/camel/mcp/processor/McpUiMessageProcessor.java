package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.model.McpUiSession;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Handles ui/message requests from embedded MCP Apps.
 * 
 * Apps use this method to send follow-up messages to the host.
 */
@BindToRegistry("mcpUiMessage")
public class McpUiMessageProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiMessageProcessor.class);

    public static final String EXCHANGE_PROPERTY_UI_MESSAGE = "mcp.ui.message";
    public static final String EXCHANGE_PROPERTY_UI_MESSAGE_TYPE = "mcp.ui.message.type";

    private final McpUiSessionRegistry sessionRegistry;

    public McpUiMessageProcessor() {
        this(new McpUiSessionRegistry());
    }

    public McpUiMessageProcessor(McpUiSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);
        String sessionId = getSessionId(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing ui/message request id={} sessionId={}", getJsonRpcId(exchange), sessionId);
        }

        // Validate session
        McpUiSession session = validateSession(exchange, sessionId);
        if (session == null) {
            return; // Error already written
        }

        // Extract message content
        String message = params != null ? (String) params.get("message") : null;
        String type = params != null ? (String) params.get("type") : null;

        if (message == null || message.isBlank()) {
            writeError(exchange, createError(-32602, "Missing required parameter: message"), 400);
            return;
        }

        // Store message on exchange for downstream processing
        exchange.setProperty(EXCHANGE_PROPERTY_UI_MESSAGE, message);
        if (type != null) {
            exchange.setProperty(EXCHANGE_PROPERTY_UI_MESSAGE_TYPE, type);
        }

        LOG.info("UI Message from session {}: type={} message={}", sessionId, type, message);

        // Acknowledge receipt
        Map<String, Object> result = newResultMap();
        result.put("acknowledged", true);
        writeResult(exchange, result);
    }

    private String getSessionId(Exchange exchange) {
        // Try header first (for HTTP requests)
        String sessionId = exchange.getIn().getHeader("X-MCP-Session-Id", String.class);
        if (sessionId == null) {
            // Try exchange property (set during ui/initialize)
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
            writeError(exchange, createError(-32001, "Session ID is required. Provide X-MCP-Session-Id header or sessionId parameter."), 400);
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
