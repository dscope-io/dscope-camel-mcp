package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.model.McpUiSession;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Handles ui/update-model-context requests from embedded MCP Apps.
 * 
 * Apps use this method to update the host's model context with additional
 * information that should be included in future model interactions.
 */
@BindToRegistry("mcpUiUpdateModelContext")
public class McpUiUpdateModelContextProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiUpdateModelContextProcessor.class);

    public static final String EXCHANGE_PROPERTY_UI_MODEL_CONTEXT = "mcp.ui.modelContext";
    public static final String EXCHANGE_PROPERTY_UI_MODEL_CONTEXT_OPERATION = "mcp.ui.modelContext.operation";

    private final McpUiSessionRegistry sessionRegistry;

    public McpUiUpdateModelContextProcessor() {
        this(new McpUiSessionRegistry());
    }

    public McpUiUpdateModelContextProcessor(McpUiSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);
        String sessionId = getSessionId(exchange);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing ui/update-model-context request id={} sessionId={}", getJsonRpcId(exchange), sessionId);
        }

        // Validate session
        McpUiSession session = validateSession(exchange, sessionId);
        if (session == null) {
            return; // Error already written
        }

        // Extract context
        Object context = params != null ? params.get("context") : null;
        String operation = params != null ? (String) params.get("operation") : "replace";

        if (context == null) {
            writeError(exchange, createError(-32602, "Missing required parameter: context"), 400);
            return;
        }

        // Update session with new context
        if ("merge".equalsIgnoreCase(operation) && session.getModelContext() instanceof Map) {
            Map<String, Object> existing = (Map<String, Object>) session.getModelContext();
            if (context instanceof Map) {
                existing.putAll((Map<String, Object>) context);
                session.setModelContext(existing);
            } else {
                session.setModelContext(context);
            }
        } else {
            session.setModelContext(context);
        }

        // Store on exchange for downstream processing
        exchange.setProperty(EXCHANGE_PROPERTY_UI_MODEL_CONTEXT, context);
        exchange.setProperty(EXCHANGE_PROPERTY_UI_MODEL_CONTEXT_OPERATION, operation);

        LOG.info("Model context updated for session {}: operation={}", sessionId, operation);

        // Confirm update
        Map<String, Object> result = newResultMap();
        result.put("updated", true);
        writeResult(exchange, result);
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
