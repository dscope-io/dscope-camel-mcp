package io.dscope.camel.mcp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Host information returned in ui/initialize response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiHostInfo {

    private static final String DEFAULT_NAME = "camel-mcp";
    private static final String DEFAULT_VERSION = "1.2.0";

    private String name;
    private String version;

    public McpUiHostInfo() {
        this(DEFAULT_NAME, DEFAULT_VERSION);
    }

    public McpUiHostInfo(String name, String version) {
        this.name = name;
        this.version = version;
    }

    public static McpUiHostInfo defaultHostInfo() {
        return new McpUiHostInfo();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
