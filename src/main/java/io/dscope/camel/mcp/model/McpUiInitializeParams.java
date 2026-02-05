package io.dscope.camel.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Parameters for ui/initialize request from embedded MCP App.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiInitializeParams {

    private String resourceUri;
    private McpUiClientInfo clientInfo;

    public McpUiInitializeParams() {
    }

    public McpUiInitializeParams(String resourceUri, McpUiClientInfo clientInfo) {
        this.resourceUri = resourceUri;
        this.clientInfo = clientInfo;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public McpUiClientInfo getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(McpUiClientInfo clientInfo) {
        this.clientInfo = clientInfo;
    }
}
