package io.dscope.camel.mcp.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the _meta field for MCP tools in tools/list response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpToolMeta {

    private McpUiMeta ui;
    private Map<String, Object> hints;

    public McpToolMeta() {
    }

    public McpToolMeta(McpUiMeta ui, Map<String, Object> hints) {
        this.ui = ui;
        this.hints = hints;
    }

    public McpUiMeta getUi() {
        return ui;
    }

    public void setUi(McpUiMeta ui) {
        this.ui = ui;
    }

    public Map<String, Object> getHints() {
        return hints;
    }

    public void setHints(Map<String, Object> hints) {
        this.hints = hints;
    }
}
