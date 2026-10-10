package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpServerDiscoverProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void returnsSupportedVersionsCapabilitiesAndModernResultMetadata() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            var exchange = new DefaultExchange(context);
            exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, "discover-1");
            exchange.setProperty(
                    McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION,
                    McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION);

            new McpServerDiscoverProcessor().process(exchange);

            Map<String, Object> envelope = MAPPER.readValue(
                    exchange.getIn().getBody(String.class), new TypeReference<>() { });
            Map<String, Object> result = (Map<String, Object>) envelope.get("result");
            Map<String, Object> capabilities = (Map<String, Object>) result.get("capabilities");
            Map<String, Object> meta = (Map<String, Object>) result.get("_meta");

            assertEquals("discover-1", envelope.get("id"));
            assertEquals("complete", result.get("resultType"));
            assertEquals(java.util.List.of("2026-07-28", "2025-06-18"), result.get("supportedVersions"));
            assertTrue(capabilities.containsKey("tools"));
            assertTrue(capabilities.containsKey("resources"));
            assertTrue(meta.containsKey("io.modelcontextprotocol/serverInfo"));
            assertEquals(3_600_000, result.get("ttlMs"));
            assertEquals("public", result.get("cacheScope"));
        }
    }
}
