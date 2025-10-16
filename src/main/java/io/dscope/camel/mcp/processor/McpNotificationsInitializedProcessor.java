package io.dscope.camel.mcp.processor;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Handles the notifications/initialized MCP message by acknowledging without a JSON-RPC response.
 */
@BindToRegistry("mcpNotificationsInitialized")
public class McpNotificationsInitializedProcessor extends AbstractMcpResponseProcessor {

    @Override
    protected void handleResponse(Exchange exchange) {
        applyNoContentResponse(exchange, 204);
    }
}
