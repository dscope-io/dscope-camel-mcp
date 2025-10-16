package io.dscope.camel.samples.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.camel.Exchange;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor;

/**
 * Loads a JSON document from the classpath and returns it as the MCP result
 * payload.
 */
public class SampleResourceResponseProcessor extends AbstractMcpResponseProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        String resourceName = exchange
                .getProperty(SampleResourceRequestProcessor.EXCHANGE_PROPERTY_RESOURCE_NAME, String.class);
        if (resourceName == null || resourceName.isBlank()) {
            resourceName = "example-resource";
        }

        Map<String, Object> payload = loadPayload(resourceName);
        writeResult(exchange, payload);
    }

    private Map<String, Object> loadPayload(String resourceName) throws IOException {
        String resourcePath = "data/" + resourceName + ".json";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource payload not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(stream, MAP_TYPE);
        }
    }
}
