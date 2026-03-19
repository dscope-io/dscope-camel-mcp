package io.dscope.camel.mcp.processor;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for serializing MCP responses to JSON strings for HTTP transport.
 */
final class McpJsonWriter {

    private static final Logger LOG = LoggerFactory.getLogger(McpJsonWriter.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpJsonWriter() {
        // no instances
    }

    static void writeJson(Exchange exchange, Object payload) {
        Objects.requireNonNull(exchange, "exchange");
        try {
            exchange.getIn().setBody(OBJECT_MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            LOG.error("Unable to serialize MCP response payloadType={} method={} id={}",
                    payload != null ? payload.getClass().getName() : "<null>",
                    exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD),
                    exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID),
                    e);
            throw new IllegalStateException("Unable to serialize MCP response", e);
        }
    }
}
