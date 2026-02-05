package io.dscope.camel.mcp.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of ui/initialize - tells MCP App what capabilities the host supports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpUiInitializeResult {

    private String sessionId;
    private McpUiHostInfo hostInfo;
    private List<String> capabilities;

    public McpUiInitializeResult() {
    }

    public McpUiInitializeResult(String sessionId, McpUiHostInfo hostInfo, List<String> capabilities) {
        this.sessionId = sessionId;
        this.hostInfo = hostInfo;
        this.capabilities = capabilities;
    }

    /**
     * Creates a default result with standard capabilities.
     * 
     * @param sessionId the session identifier
     * @return a fully populated result
     */
    public static McpUiInitializeResult createDefault(String sessionId) {
        return new McpUiInitializeResult(
                sessionId,
                McpUiHostInfo.defaultHostInfo(),
                List.of(
                        "tools/call",
                        "ui/message",
                        "ui/update-model-context",
                        "ui/notifications/tool-input",
                        "ui/notifications/tool-result"));
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public McpUiHostInfo getHostInfo() {
        return hostInfo;
    }

    public void setHostInfo(McpUiHostInfo hostInfo) {
        this.hostInfo = hostInfo;
    }

    public List<String> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }
}
