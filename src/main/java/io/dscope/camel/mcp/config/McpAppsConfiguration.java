package io.dscope.camel.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Configuration properties for MCP Apps Bridge.
 * 
 * These settings control the behavior of the MCP Apps UI session management
 * and notification delivery.
 */
public class McpAppsConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(McpAppsConfiguration.class);

    public static final long DEFAULT_SESSION_TIMEOUT_MS = 3600000L; // 1 hour
    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_CONTEXT_MAX_BYTES = McpUiSessionRegistry.DEFAULT_CONTEXT_MAX_BYTES;
    public static final int DEFAULT_CONTEXT_MAX_ENTRIES = McpUiSessionRegistry.DEFAULT_CONTEXT_MAX_ENTRIES;

    private boolean enabled = DEFAULT_ENABLED;
    private long sessionTimeoutMs = DEFAULT_SESSION_TIMEOUT_MS;
    private int contextMaxBytes = DEFAULT_CONTEXT_MAX_BYTES;
    private int contextMaxEntries = DEFAULT_CONTEXT_MAX_ENTRIES;
    private String hostName = "camel-mcp";
    private String hostVersion = "1.5.0";

    public McpAppsConfiguration() {
    }

    /**
     * Creates configuration from system properties.
     * 
     * Properties:
     * - mcp.apps.enabled (boolean, default: true)
     * - mcp.apps.session.timeout (long milliseconds, default: 3600000)
     * - mcp.apps.host.name (string, default: camel-mcp)
     * - mcp.apps.context.maxBytes (integer, default: 262144)
     * - mcp.apps.context.maxEntries (integer, default: 512)
     * - mcp.apps.host.version (string, default: 1.5.0)
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
                LOG.warn("Invalid mcp.apps.session.timeout value '{}' ; using default {}",
                        timeoutProp, DEFAULT_SESSION_TIMEOUT_MS);
            }
        }

        config.contextMaxBytes = positiveIntProperty(
                "mcp.apps.context.maxBytes", DEFAULT_CONTEXT_MAX_BYTES);
        config.contextMaxEntries = positiveIntProperty(
                "mcp.apps.context.maxEntries", DEFAULT_CONTEXT_MAX_ENTRIES);
        
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

    private static int positiveIntProperty(String name, int defaultValue) {
        String value = System.getProperty(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to the warning and default below.
        }
        LOG.warn("Invalid {} value '{}'; using default {}", name, value, defaultValue);
        return defaultValue;
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

    public int getContextMaxBytes() {
        return contextMaxBytes;
    }

    public void setContextMaxBytes(int contextMaxBytes) {
        this.contextMaxBytes = contextMaxBytes;
    }

    public int getContextMaxEntries() {
        return contextMaxEntries;
    }

    public void setContextMaxEntries(int contextMaxEntries) {
        this.contextMaxEntries = contextMaxEntries;
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
