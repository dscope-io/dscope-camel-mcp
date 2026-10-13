package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

/**
 * Implements the MCP 2026-07-28 server discovery request.
 */
@BindToRegistry("mcpServerDiscover")
public class McpServerDiscoverProcessor extends AbstractMcpResponseProcessor {

    private static final List<String> SUPPORTED_VERSIONS = List.of(
            McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION,
            McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION);

    private final boolean eventsEnabled;

    public McpServerDiscoverProcessor() {
        this(false);
    }

    /** Explicit capability opt-in, also usable in application platform-http routes. */
    public McpServerDiscoverProcessor(boolean eventsEnabled) {
        this.eventsEnabled = eventsEnabled;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("supportedVersions", SUPPORTED_VERSIONS);
        Map<String, Object> capabilities = new LinkedHashMap<>(Map.of(
                "tools", Map.of(),
                "resources", Map.of()));
        if (eventsEnabled) capabilities.put("events", Map.of());
        result.put("capabilities", capabilities);
        result.put("ttlMs", 3_600_000);
        result.put("cacheScope", "public");
        writeResult(exchange, result);
    }
}
