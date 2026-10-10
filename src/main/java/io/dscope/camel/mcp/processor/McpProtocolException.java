package io.dscope.camel.mcp.processor;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * An MCP protocol error that carries its JSON-RPC code and HTTP status.
 */
public class McpProtocolException extends IllegalArgumentException {

    private final int code;
    private final int httpStatus;
    private final Map<String, Object> data;

    public McpProtocolException(int code, String message, int httpStatus, Map<String, Object> data) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public int getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
