package io.dscope.camel.mcp.processor;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import io.dscope.camel.mcp.events.McpEventsProvider;
import io.dscope.camel.mcp.events.McpWebhookClient;
import org.apache.camel.Exchange;

/** MCP 2026-07-28 Events dispatch with application-owned authorization/storage. */
public final class McpEventsProcessor extends AbstractMcpResponseProcessor {
    private final McpEventsProvider provider;

    public McpEventsProcessor(McpEventsProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider");
    }

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        if (!McpProtocolSupport.isModernProtocol(exchange)) {
            throw new McpProtocolException(-32601, "Events require protocol 2026-07-28", 404, Map.of());
        }
        Map<String, Object> params = getRequestParameters(exchange);
        String method = getJsonRpcMethod(exchange);
        if (params.containsKey("cursor") && params.get("cursor") != null) {
            string(params, "cursor");
        }
        switch (method) {
            case "events/list" -> writeResult(exchange, provider.list(exchange, params));
            case "events/subscribe", "events/unsubscribe" -> {
                string(params, "name");
                if (params.containsKey("arguments") && !(params.get("arguments") instanceof Map<?, ?>)) {
                    throw invalid("arguments must be an object");
                }
                if (!(params.get("delivery") instanceof Map<?, ?> delivery)
                        || !"webhook".equals(delivery.get("mode"))) {
                    throw invalid("delivery.mode must be webhook");
                }
                String url = string(delivery, "url");
                try {
                    McpWebhookClient.validateUrl(URI.create(url));
                } catch (IllegalArgumentException e) {
                    throw invalid("delivery.url must be an absolute HTTPS callback URL");
                }
                if ("events/subscribe".equals(method)) {
                    try {
                        McpWebhookClient.decodeSecret(string(delivery, "secret"));
                    } catch (IllegalArgumentException e) {
                        throw invalid("delivery.secret must be whsec_ base64 encoding 24..64 bytes");
                    }
                    Object ttl = params.get("ttlMs");
                    if (ttl != null && (!(ttl instanceof Number n) || n.longValue() <= 0
                            || n.doubleValue() != n.longValue())) {
                        throw invalid("ttlMs must be a positive integer or null");
                    }
                    writeResult(exchange, provider.subscribe(exchange, params));
                } else {
                    provider.unsubscribe(exchange, params);
                    writeResult(exchange, Map.of());
                }
            }
            default -> throw new McpProtocolException(-32601, "Unsupported Events method", 404, Map.of());
        }
    }

    private static String string(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof String value) || value.isBlank()) {
            throw invalid(key + " must be a non-empty string");
        }
        return value;
    }

    private static McpProtocolException invalid(String message) {
        return new McpProtocolException(-32602, message, 400, Map.of());
    }
}
