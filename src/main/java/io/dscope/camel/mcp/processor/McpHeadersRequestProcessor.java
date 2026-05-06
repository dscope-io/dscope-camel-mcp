package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

@BindToRegistry("mcpHeadersRequestProcessor")
public class McpHeadersRequestProcessor extends AbstractMcpRequestProcessor {

    @Override
    protected void handleRequest(Exchange exchange, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        parameters.forEach((key, value) -> exchange.getIn().setHeader(key, value));
    }
}