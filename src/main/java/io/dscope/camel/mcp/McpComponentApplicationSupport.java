package io.dscope.camel.mcp;

import org.apache.camel.main.Main;

import io.dscope.camel.mcp.catalog.McpMethodCatalog;
import io.dscope.camel.mcp.processor.McpErrorProcessor;
import io.dscope.camel.mcp.processor.McpHealthStatusProcessor;
import io.dscope.camel.mcp.processor.McpHttpValidatorProcessor;
import io.dscope.camel.mcp.processor.McpInitializeProcessor;
import io.dscope.camel.mcp.processor.McpJsonRpcEnvelopeProcessor;
import io.dscope.camel.mcp.processor.McpNotificationAckProcessor;
import io.dscope.camel.mcp.processor.McpNotificationProcessor;
import io.dscope.camel.mcp.processor.McpNotificationsInitializedProcessor;
import io.dscope.camel.mcp.processor.McpPingProcessor;
import io.dscope.camel.mcp.processor.McpRateLimitProcessor;
import io.dscope.camel.mcp.processor.McpRequestSizeGuardProcessor;
import io.dscope.camel.mcp.processor.McpResourcesListProcessor;
import io.dscope.camel.mcp.processor.McpStreamProcessor;
import io.dscope.camel.mcp.processor.McpToolsListProcessor;

/**
 * Simplifies bootstrapping a Camel {@link Main} instance with the default MCP processors.
 */
public abstract class McpComponentApplicationSupport {

    private final McpMethodCatalog methodCatalog = new McpMethodCatalog();
    private final McpRequestSizeGuardProcessor requestSizeGuard = new McpRequestSizeGuardProcessor();
    private final McpHttpValidatorProcessor httpValidator = new McpHttpValidatorProcessor();
    private final McpRateLimitProcessor rateLimit = new McpRateLimitProcessor();
    private final McpJsonRpcEnvelopeProcessor jsonRpcEnvelope = new McpJsonRpcEnvelopeProcessor();
    private final McpInitializeProcessor initialize = new McpInitializeProcessor();
    private final McpPingProcessor ping = new McpPingProcessor();
    private final McpNotificationsInitializedProcessor notificationsInitialized = new McpNotificationsInitializedProcessor();
    private final McpNotificationProcessor notification = new McpNotificationProcessor();
    private final McpNotificationAckProcessor notificationAck = new McpNotificationAckProcessor();
    private final McpToolsListProcessor toolsList = new McpToolsListProcessor(methodCatalog);
    private final McpResourcesListProcessor resourcesList = new McpResourcesListProcessor();
    private final McpErrorProcessor error = new McpErrorProcessor();
    private final McpStreamProcessor stream = new McpStreamProcessor();
    private final McpHealthStatusProcessor healthStatus = new McpHealthStatusProcessor(rateLimit);

    public final void run(String[] args) throws Exception {
        Main main = createMain();
        bindDefaultBeans(main);
        bindAdditionalBeans(main);
        configureRoutes(main);
        main.run(args);
    }

    protected Main createMain() {
        return new Main();
    }

    protected void bindAdditionalBeans(Main main) {
        // subclasses override
    }

    protected boolean includeHttpValidator() {
        return false;
    }

    protected abstract String routesIncludePattern();

    protected McpMethodCatalog getMethodCatalog() {
        return methodCatalog;
    }

    protected McpRateLimitProcessor getRateLimitProcessor() {
        return rateLimit;
    }

    protected McpStreamProcessor getStreamProcessor() {
        return stream;
    }

    private void bindDefaultBeans(Main main) {
        main.bind("mcpRequestSizeGuard", requestSizeGuard);
        if (includeHttpValidator()) {
            main.bind("mcpHttpValidator", httpValidator);
        }
        main.bind("mcpRateLimit", rateLimit);
        main.bind("mcpJsonRpcEnvelope", jsonRpcEnvelope);
        main.bind("mcpInitialize", initialize);
        main.bind("mcpPing", ping);
        main.bind("mcpNotificationsInitialized", notificationsInitialized);
        main.bind("mcpNotification", notification);
        main.bind("mcpNotificationAck", notificationAck);
        main.bind("mcpToolsList", toolsList);
        main.bind("mcpResourcesList", resourcesList);
        main.bind("mcpError", error);
        main.bind("mcpStream", stream);
        main.bind("mcpHealthStatus", healthStatus);
    }

    private void configureRoutes(Main main) {
        String includePattern = routesIncludePattern();
        if (includePattern == null || includePattern.isBlank()) {
            throw new IllegalStateException("Route include pattern must be provided");
        }
        main.configure().setRoutesIncludePattern(includePattern);
    }
}
