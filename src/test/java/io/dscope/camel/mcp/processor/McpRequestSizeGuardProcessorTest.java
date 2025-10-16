package io.dscope.camel.mcp.processor;

import org.apache.camel.support.DefaultExchange;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class McpRequestSizeGuardProcessorTest {

    private void clearProperties() {
        System.clearProperty("mcp.requestSizeGuard.enabled");
        System.clearProperty("mcp.maxRequestBytes");
    }

    @Test
    void allowsPayloadWithinLimit() throws Exception {
        System.setProperty("mcp.maxRequestBytes", "2048");
    DefaultExchange exchange = new DefaultExchange(new org.apache.camel.impl.DefaultCamelContext());
        byte[] payload = new byte[512];
        exchange.getIn().setBody(payload);

        McpRequestSizeGuardProcessor processor = new McpRequestSizeGuardProcessor();
        try {
            processor.process(exchange);
            assertEquals(byte[].class, exchange.getIn().getBody().getClass());
        } finally {
            clearProperties();
        }
    }

    @Test
    void rejectsPayloadBeyondLimit() {
        System.setProperty("mcp.maxRequestBytes", "1024");
    DefaultExchange exchange = new DefaultExchange(new org.apache.camel.impl.DefaultCamelContext());
        exchange.getIn().setBody(new byte[2049]);

        McpRequestSizeGuardProcessor processor = new McpRequestSizeGuardProcessor();
        try {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> processor.process(exchange));
            assertEquals("Request body too large (2049 bytes, max 1024)", ex.getMessage());
        } finally {
            clearProperties();
        }
    }
}
