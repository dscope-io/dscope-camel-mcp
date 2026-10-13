package io.dscope.camel.mcp.events;

import java.util.Map;
import io.dscope.camel.mcp.processor.McpProtocolException;

/** Safe, categorized callback failure; does not disclose URLs, secrets or response bodies. */
public final class McpCallbackException extends McpProtocolException {
    public McpCallbackException(String reason) {
        super(-32015, "CallbackEndpointError", 400, Map.of("reason", reason));
    }

    public String getReason() {
        return (String) getData().get("reason");
    }
}
