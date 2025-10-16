package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Serializes JSON-RPC error envelopes using properties set by upstream processors.
 */
@BindToRegistry("mcpError")
public class McpErrorProcessor extends AbstractMcpResponseProcessor {

    public static final String PROPERTY_ERROR_CODE = "mcp.error.code";
    public static final String PROPERTY_ERROR_MESSAGE = "mcp.error.message";
    public static final String PROPERTY_ERROR_DATA = "mcp.error.data";

    @Override
    @SuppressWarnings("unchecked")
    protected void handleResponse(Exchange exchange) {
        Number code = exchange.getProperty(PROPERTY_ERROR_CODE, Number.class);
        String message = exchange.getProperty(PROPERTY_ERROR_MESSAGE, String.class);
        Map<String, Object> data = exchange.getProperty(PROPERTY_ERROR_DATA, Map.class);

        if (code == null) {
            code = -32603; // internal error
        }
        if (message == null || message.isBlank()) {
            message = "An unexpected error occurred";
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code.intValue());
        error.put("message", message);
        if (data != null && !data.isEmpty()) {
            error.put("data", data);
        }

        writeError(exchange, error, 0);
    }
}
