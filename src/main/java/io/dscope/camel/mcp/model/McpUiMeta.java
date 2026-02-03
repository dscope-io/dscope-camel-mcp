package io.dscope.camel.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents UI metadata for MCP tools.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiMeta {

    private String resourceUri;
    private String csp;
    private String theme;

    public McpUiMeta() {
    }

    public McpUiMeta(String resourceUri, String csp, String theme) {
        this.resourceUri = resourceUri;
        this.csp = csp;
        this.theme = theme;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }

    public String getCsp() {
        return csp;
    }

    public void setCsp(String csp) {
        this.csp = csp;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }
}
