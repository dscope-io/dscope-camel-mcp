package io.dscope.camel.samples.mcp;

import org.apache.camel.main.Main;

import io.dscope.camel.mcp.McpComponentApplicationSupport;

/**
 * Boots the sample MCP service, loading both HTTP and WebSocket helper routes by default.
 * <p>
 * Pass {@code -Dcamel.main.routesIncludePattern=classpath:routes/mcp-service-ws.yaml} to run the
 * WebSocket-only helpers while reusing the same processors and method catalog loaded from
 * {@code classpath:mcp/methods.yaml}.
 */
public final class McpServiceApplication extends McpComponentApplicationSupport {

    private McpServiceApplication() {
        // utility
    }

    public static void main(String[] args) throws Exception {
        new McpServiceApplication().run(args);
    }

    @Override
    protected boolean includeHttpValidator() {
        return true;
    }

    @Override
    protected String routesIncludePattern() {
        return "classpath:routes/mcp-service*.yaml";
    }

    @Override
    protected void bindAdditionalBeans(Main main) {
        main.bind("sampleToolCallProcessor", new SampleToolCallProcessor());
        main.bind("sampleResourceRequest", new SampleResourceRequestProcessor());
        main.bind("sampleResourceResponse", new SampleResourceResponseProcessor());
    }
}
