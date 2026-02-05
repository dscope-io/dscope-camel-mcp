package io.dscope.camel.mcp.config;

/**
 * Configuration properties for MCP Apps Bridge.
 * 
 * These settings control the behavior of the MCP Apps UI session management
 * and notification delivery.
 */
public class McpAppsConfiguration {

    public static final long DEFAULT_SESSION_TIMEOUT_MS = 3600000L; // 1 hour
    public static final boolean DEFAULT_ENABLED = true;

    private boolean enabled = DEFAULT_ENABLED;
    private long sessionTimeoutMs = DEFAULT_SESSION_TIMEOUT_MS;
    private String hostName = "camel-mcp";
    private String hostVersion = "1.2.0";

    public McpAppsConfiguration() {
    }

    /**
     * Creates configuration from system properties.
     * 
     * Properties:
     * - mcp.apps.enabled (boolean, default: true)
     * - mcp.apps.session.timeout (long milliseconds, default: 3600000)
     * - mcp.apps.host.name (string, default: camel-mcp)
     * - mcp.apps.host.version (string, default: 1.2.0)
     * 
     * @return configuration instance
     */
    public static McpAppsConfiguration fromSystemProperties() {
        McpAppsConfiguration config = new McpAppsConfiguration();
        
        String enabledProp = System.getProperty("mcp.apps.enabled");
        if (enabledProp != null) {
            config.enabled = Boolean.parseBoolean(enabledProp);
        }
        
        String timeoutProp = System.getProperty("mcp.apps.session.timeout");
        if (timeoutProp != null) {
            try {
                config.sessionTimeoutMs = Long.parseLong(timeoutProp);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        
        String hostNameProp = System.getProperty("mcp.apps.host.name");
        if (hostNameProp != null && !hostNameProp.isBlank()) {
            config.hostName = hostNameProp;
        }
        
        String hostVersionProp = System.getProperty("mcp.apps.host.version");
        if (hostVersionProp != null && !hostVersionProp.isBlank()) {
            config.hostVersion = hostVersionProp;
        }
        
        return config;
    }

    /**
     * Whether MCP Apps Bridge is enabled.
     * 
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Session timeout in milliseconds.
     * Sessions expire if idle longer than this duration.
     * 
     * @return timeout in ms
     */
    public long getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    /**
     * Host name reported in ui/initialize response.
     * 
     * @return host name
     */
    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Host version reported in ui/initialize response.
     * 
     * @return host version
     */
    public String getHostVersion() {
        return hostVersion;
    }

    public void setHostVersion(String hostVersion) {
        this.hostVersion = hostVersion;
    }
}
