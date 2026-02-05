package io.dscope.camel.mcp.config;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.processor.McpUiInitializeProcessor;
import io.dscope.camel.mcp.processor.McpUiMessageProcessor;
import io.dscope.camel.mcp.processor.McpUiToolsCallPostProcessor;
import io.dscope.camel.mcp.processor.McpUiToolsCallProcessor;
import io.dscope.camel.mcp.processor.McpUiUpdateModelContextProcessor;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Configures and registers MCP Apps Bridge components.
 * 
 * This class provides helper methods to set up the session registry and
 * processors required for MCP Apps Bridge functionality.
 */
public class McpAppsConfigurer {

    private static final Logger LOG = LoggerFactory.getLogger(McpAppsConfigurer.class);

    private final McpAppsConfiguration configuration;
    private McpUiSessionRegistry sessionRegistry;

    public McpAppsConfigurer() {
        this(McpAppsConfiguration.fromSystemProperties());
    }

    public McpAppsConfigurer(McpAppsConfiguration configuration) {
        this.configuration = configuration != null ? configuration : new McpAppsConfiguration();
    }

    /**
     * Configures MCP Apps Bridge in the given Camel context.
     * 
     * @param camelContext the Camel context
     */
    public void configure(CamelContext camelContext) {
        if (!configuration.isEnabled()) {
            LOG.info("MCP Apps Bridge is disabled");
            return;
        }

        LOG.info("Configuring MCP Apps Bridge with session timeout={}ms", configuration.getSessionTimeoutMs());

        // Create shared session registry
        sessionRegistry = new McpUiSessionRegistry(configuration.getSessionTimeoutMs());
        sessionRegistry.start();

        // Create processors with shared registry
        McpUiInitializeProcessor uiInitializeProcessor = new McpUiInitializeProcessor(
                sessionRegistry,
                configuration.getHostName(),
                configuration.getHostVersion());

        McpUiMessageProcessor uiMessageProcessor = new McpUiMessageProcessor(sessionRegistry);
        McpUiUpdateModelContextProcessor uiUpdateModelContextProcessor = new McpUiUpdateModelContextProcessor(sessionRegistry);
        McpUiToolsCallProcessor uiToolsCallProcessor = new McpUiToolsCallProcessor(sessionRegistry);
        McpUiToolsCallPostProcessor uiToolsCallPostProcessor = new McpUiToolsCallPostProcessor(sessionRegistry);

        // Register components
        Registry registry = camelContext.getRegistry();
        bindToRegistry(registry, "mcpUiSessionRegistry", sessionRegistry);
        bindToRegistry(registry, "mcpUiInitialize", uiInitializeProcessor);
        bindToRegistry(registry, "mcpUiMessage", uiMessageProcessor);
        bindToRegistry(registry, "mcpUiUpdateModelContext", uiUpdateModelContextProcessor);
        bindToRegistry(registry, "mcpUiToolsCall", uiToolsCallProcessor);
        bindToRegistry(registry, "mcpUiToolsCallPost", uiToolsCallPostProcessor);

        LOG.info("MCP Apps Bridge configured successfully");
    }

    /**
     * Stops the session registry and cleans up resources.
     */
    public void shutdown() {
        if (sessionRegistry != null) {
            sessionRegistry.stop();
            LOG.info("MCP Apps Bridge shutdown complete");
        }
    }

    /**
     * Gets the session registry.
     * 
     * @return the session registry, or null if not configured
     */
    public McpUiSessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    /**
     * Gets the configuration.
     * 
     * @return the configuration
     */
    public McpAppsConfiguration getConfiguration() {
        return configuration;
    }

    private void bindToRegistry(Registry registry, String name, Object bean) {
        try {
            registry.bind(name, bean);
            LOG.debug("Registered {} as '{}'", bean.getClass().getSimpleName(), name);
        } catch (Exception e) {
            LOG.warn("Failed to register {}: {}", name, e.getMessage());
        }
    }
}
