package io.dscope.camel.mcp.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Generic acknowledgement processor for MCP notifications. It reuses the same
 * 204 No Content response behaviour that we expose for the
 * {@code notifications/initialized} notification so other notification types
 * can share the acknowledgement logic.
 */
public class McpNotificationAckProcessor implements Processor {

    private final McpNotificationsInitializedProcessor delegate = new McpNotificationsInitializedProcessor();

    @Override
    public void process(Exchange exchange) throws Exception {
        delegate.process(exchange);
    }
}
