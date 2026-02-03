package io.dscope.camel.mcp.catalog;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.dscope.camel.mcp.model.McpResource;

/**
 * Represents an MCP resource definition loaded from configuration.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class McpResourceDefinition {

    private String uri;
    private String name;
    private String description;
    private String mimeType;
    private String source;
    private Map<String, String> config;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public void setConfig(Map<String, String> config) {
        this.config = config;
    }

    /**
     * Converts this definition to an McpResource for the resources/list response.
     */
    public McpResource toResource() {
        return new McpResource(uri, name, description, mimeType);
    }
}
