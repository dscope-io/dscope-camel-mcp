package io.dscope.camel.samples.mcp;

import java.util.Map;

import org.apache.camel.Exchange;

import io.dscope.camel.mcp.processor.AbstractMcpRequestProcessor;

/**
 * Extracts the desired resource name from MCP request parameters so the
 * response processor can load the corresponding JSON payload.
 */
public class SampleResourceRequestProcessor extends AbstractMcpRequestProcessor {

    public static final String EXCHANGE_PROPERTY_RESOURCE_NAME = "sample.resource.name";
    private static final String DEFAULT_RESOURCE = "example-resource";

    @Override
    protected void handleRequest(Exchange exchange, Map<String, Object> parameters) {
        Map<String, Object> params = parameters == null ? Map.of() : parameters;
        Object rawResource = params.getOrDefault("resource", DEFAULT_RESOURCE);
        String resourceName = rawResource == null ? DEFAULT_RESOURCE : rawResource.toString().trim();
        if (resourceName.isEmpty()) {
            throw new IllegalArgumentException("resource parameter must not be blank");
        }
        exchange.setProperty(EXCHANGE_PROPERTY_RESOURCE_NAME, resourceName);
    }
}
