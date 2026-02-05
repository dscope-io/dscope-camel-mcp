package io.dscope.camel.mcp.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parameters for ui/update-model-context request from embedded MCP App.
 * 
 * Used when the app updates the host's model context.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiUpdateModelContextParams {

    private Map<String, Object> context;
    private String operation;

    public McpUiUpdateModelContextParams() {
    }

    public McpUiUpdateModelContextParams(Map<String, Object> context, String operation) {
        this.context = context;
        this.operation = operation;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public void setContext(Map<String, Object> context) {
        this.context = context;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }
}
