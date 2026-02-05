package io.dscope.camel.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parameters for ui/message request from embedded MCP App.
 * 
 * Used when the app sends follow-up messages to the host.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiMessageParams {

    private String message;
    private String type;

    public McpUiMessageParams() {
    }

    public McpUiMessageParams(String message, String type) {
        this.message = message;
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
