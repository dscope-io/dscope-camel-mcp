package io.dscope.camel.mcp.model;

import java.util.Base64;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the content of an MCP resource for the resources/read response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpResourceContent {

    private String uri;
    private String mimeType;
    private String text;
    private String blob;

    public McpResourceContent() {
    }

    private McpResourceContent(String uri, String mimeType, String text, String blob) {
        this.uri = uri;
        this.mimeType = mimeType;
        this.text = text;
        this.blob = blob;
    }

    /**
     * Factory method for text content.
     */
    public static McpResourceContent text(String uri, String mimeType, String text) {
        return new McpResourceContent(uri, mimeType, text, null);
    }

    /**
     * Factory method for binary (blob) content.
     */
    public static McpResourceContent blob(String uri, String mimeType, byte[] data) {
        String encoded = data != null ? Base64.getEncoder().encodeToString(data) : null;
        return new McpResourceContent(uri, mimeType, null, encoded);
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getBlob() {
        return blob;
    }

    public void setBlob(String blob) {
        this.blob = blob;
    }
}
