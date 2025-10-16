package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;

/**
 * Base class for MCP request processors. It normalizes access to the JSON-RPC
 * request parameters while preserving the original exchange body for downstream
 * processors.
 */
public abstract class AbstractMcpRequestProcessor extends AbstractMcpProcessor {

    @Override
    protected final void doProcess(Exchange exchange) throws Exception {
        handleRequest(exchange, getRequestParameters(exchange));
    }

    /**
     * Allows subclasses to implement their specific request handling logic.
     */
    protected abstract void handleRequest(Exchange exchange, Map<String, Object> parameters) throws Exception;

    protected final boolean isNotification(Exchange exchange) {
        String type = getJsonRpcType(exchange);
        return type != null && type.equalsIgnoreCase("NOTIFICATION");
    }
}
