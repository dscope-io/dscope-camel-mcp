package io.dscope.camel.mcp;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.support.DefaultEndpoint;

public class McpEndpoint extends DefaultEndpoint {
    private final McpConfiguration configuration;
    public McpEndpoint(String uri, McpComponent component, McpConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }
    @Override public Producer createProducer() { return new McpProducer(this); }
    @Override public Consumer createConsumer(Processor processor) { return new McpConsumer(this, processor); }
    @Override public boolean isSingleton() { return true; }
    public McpConfiguration getConfiguration() { return configuration; }
}
