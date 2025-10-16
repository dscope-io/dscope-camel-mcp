package io.dscope.camel.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

public class McpConsumer extends DefaultConsumer {
    private final McpEndpoint endpoint;
    public McpConsumer(McpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // Placeholder for Undertow listener integration
    }
}
