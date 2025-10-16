package io.dscope.camel.mcp.processor;

import java.util.Objects;

import org.apache.camel.Exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for serializing MCP responses to JSON strings for HTTP transport.
 */
final class McpJsonWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpJsonWriter() {
        // no instances
    }

    static void writeJson(Exchange exchange, Object payload) {
        Objects.requireNonNull(exchange, "exchange");
        try {
            exchange.getIn().setBody(OBJECT_MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize MCP response", e);
        }
    }
}
