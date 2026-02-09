package io.dscope.camel.mcp;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Camel component for the Model Context Protocol (MCP).
 *
 * Enables Camel routes to act as MCP clients (producer) sending JSON-RPC 2.0 requests,
 * or MCP servers (consumer) exposing tools, resources, and prompts to AI agents.
 */
@UriEndpoint(
    firstVersion = "1.0.0",
    scheme = "mcp",
    title = "MCP",
    syntax = "mcp:uri",
    category = { Category.AI },
    producerOnly = false,
    lenientProperties = true
)
@Metadata(annotations = {
    "protocol=http"
})
public class McpEndpoint extends DefaultEndpoint {

    @UriPath(description = "The target MCP server URI (e.g. http://localhost:8080/mcp). "
            + "For consumers this is the listen address; for producers the remote server address.")
    @Metadata(required = true)
    private String uri;

    @UriParam(label = "producer", defaultValue = "tools/list",
            description = "The MCP JSON-RPC method to invoke.")
    private String method;

    @UriParam(label = "consumer", defaultValue = "false",
            description = "When true the consumer creates a WebSocket endpoint instead of HTTP.")
    private boolean websocket;

    private final McpConfiguration configuration;

    public McpEndpoint(String endpointUri, McpComponent component, McpConfiguration configuration) {
        super(endpointUri, component);
        this.configuration = configuration;
    }

    @Override public Producer createProducer() { return new McpProducer(this); }
    @Override public Consumer createConsumer(Processor processor) { return new McpConsumer(this, processor); }
    @Override public boolean isSingleton() { return true; }

    public McpConfiguration getConfiguration() { return configuration; }

    public String getUri() { return configuration.getUri(); }
    public void setUri(String uri) { configuration.setUri(uri); }

    public String getMethod() { return configuration.getMethod(); }
    public void setMethod(String method) { configuration.setMethod(method); }

    public boolean isWebsocket() { return configuration.isWebsocket(); }
    public void setWebsocket(boolean websocket) { configuration.setWebsocket(websocket); }
}
