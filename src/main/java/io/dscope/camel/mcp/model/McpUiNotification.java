package io.dscope.camel.mcp.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Notification sent from host to embedded MCP App.
 * 
 * Used for ui/notifications/tool-input and ui/notifications/tool-result.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiNotification {

    public static final String METHOD_TOOL_INPUT = "ui/notifications/tool-input";
    public static final String METHOD_TOOL_RESULT = "ui/notifications/tool-result";

    private String jsonrpc = "2.0";
    private String method;
    private Object params;

    public McpUiNotification() {
    }

    public McpUiNotification(String method, Object params) {
        this.method = method;
        this.params = params;
    }

    /**
     * Creates a tool input notification.
     * 
     * @param toolName  the name of the tool being called
     * @param arguments the arguments passed to the tool
     * @return a notification for tool input
     */
    public static McpUiNotification toolInput(String toolName, Object arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", toolName);
        params.put("arguments", arguments);
        return new McpUiNotification(METHOD_TOOL_INPUT, params);
    }

    /**
     * Creates a tool result notification.
     * 
     * @param toolName the name of the tool that was called
     * @param result   the result from the tool execution
     * @return a notification for tool result
     */
    public static McpUiNotification toolResult(String toolName, Object result) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", toolName);
        params.put("result", result);
        return new McpUiNotification(METHOD_TOOL_RESULT, params);
    }

    /**
     * Creates a tool result notification with error.
     * 
     * @param toolName the name of the tool that was called
     * @param error    the error that occurred
     * @return a notification for tool result with error
     */
    public static McpUiNotification toolError(String toolName, String error) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("toolName", toolName);
        params.put("error", error);
        return new McpUiNotification(METHOD_TOOL_RESULT, params);
    }

    public String getJsonrpc() {
        return jsonrpc;
    }

    public void setJsonrpc(String jsonrpc) {
        this.jsonrpc = jsonrpc;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Object getParams() {
        return params;
    }

    public void setParams(Object params) {
        this.params = params;
    }

    /**
     * Converts this notification to a Map for JSON serialization.
     * 
     * @return a map representation of this notification
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("jsonrpc", jsonrpc);
        map.put("method", method);
        if (params != null) {
            map.put("params", params);
        }
        return map;
    }
}
