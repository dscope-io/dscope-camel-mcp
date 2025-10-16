package io.dscope.camel.mcp.processor;

import java.nio.charset.StandardCharsets;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Simple per-request size guard to reject overly large MCP JSON-RPC payloads before parsing.
 * Configurable via system/property: mcp.maxRequestBytes (defaults 32768).
 * Can be disabled via system property {@code mcp.requestSizeGuard.enabled=false}.
 */
@BindToRegistry("mcpRequestSizeGuard")
public class McpRequestSizeGuardProcessor implements Processor {

    private final int maxBytes;
    private final boolean enabled;

    public McpRequestSizeGuardProcessor() {
        this.maxBytes = Integer.getInteger("mcp.maxRequestBytes", 32 * 1024);
        String enabledProperty = System.getProperty("mcp.requestSizeGuard.enabled", "true");
        this.enabled = Boolean.parseBoolean(enabledProperty);
    }

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            return;
        }
        if (!enabled) {
            return;
        }
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return;
        }
        byte[] bytes;
        if (body instanceof byte[] b) {
            bytes = b;
        } else {
            String text = exchange.getIn().getBody(String.class);
            bytes = text.getBytes(StandardCharsets.UTF_8);
            exchange.getIn().setBody(text);
        }
        int length = bytes.length;
        if (length > maxBytes) {
            throw new IllegalArgumentException("Request body too large (" + length + " bytes, max " + maxBytes + ")");
        }
    }

    public int getMaxBytes() {
        return maxBytes;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
