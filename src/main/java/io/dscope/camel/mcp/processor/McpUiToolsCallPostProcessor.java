package io.dscope.camel.mcp.processor;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Post-processor for UI tools/call requests.
 * 
 * Sends tool result notification back to the UI after tool execution completes.
 * Should be placed after the actual tool call processor in the route.
 */
@BindToRegistry("mcpUiToolsCallPost")
public class McpUiToolsCallPostProcessor extends AbstractMcpProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiToolsCallPostProcessor.class);

    private final McpUiSessionRegistry sessionRegistry;

    public McpUiToolsCallPostProcessor() {
        this(new McpUiSessionRegistry());
    }

    public McpUiToolsCallPostProcessor(McpUiSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    protected void doProcess(Exchange exchange) throws Exception {
        // Check if this request came from a UI
        Boolean fromUi = exchange.getProperty(McpUiToolsCallProcessor.EXCHANGE_PROPERTY_FROM_UI, Boolean.class);
        if (!Boolean.TRUE.equals(fromUi)) {
            return; // Not from UI, nothing to do
        }

        String sessionId = exchange.getProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, String.class);
        String toolName = exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TOOL_NAME, String.class);

        if (sessionId == null || toolName == null) {
            LOG.warn("UI post-processor missing sessionId or toolName");
            return;
        }

        // Check if there was an error
        Object errorProperty = exchange.getProperty("mcp.error.code");
        if (errorProperty != null) {
            String errorMessage = exchange.getProperty("mcp.error.message", "Tool execution failed", String.class);
            sessionRegistry.notifyToolError(sessionId, toolName, errorMessage);
            LOG.debug("Sent tool error notification to session {}: tool={} error={}", sessionId, toolName, errorMessage);
        } else {
            // Get the result from the exchange
            Object result = exchange.getIn().getBody();
            sessionRegistry.notifyToolResult(sessionId, toolName, result);
            LOG.debug("Sent tool result notification to session {}: tool={}", sessionId, toolName);
        }
    }
}
