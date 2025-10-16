package io.dscope.camel.mcp.processor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Implements the MCP ping method.
 */
@BindToRegistry("mcpPing")
public class McpPingProcessor extends AbstractMcpResponseProcessor {

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", Boolean.TRUE);
        result.put("timestamp", Instant.now().toString());
        if (!params.isEmpty()) {
            result.put("echo", params);
        }

        writeResult(exchange, result);
    }
}
