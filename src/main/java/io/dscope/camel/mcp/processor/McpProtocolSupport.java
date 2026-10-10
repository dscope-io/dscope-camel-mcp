package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;

/**
 * Shared helpers for revision-specific MCP protocol behavior.
 */
public final class McpProtocolSupport {

    private static final String DEFAULT_SERVER_NAME = "camel-mcp-component";
    private static final String DEFAULT_SERVER_VERSION = "dev";
    private static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";

    private McpProtocolSupport() {
    }

    public static boolean isModernProtocol(Exchange exchange) {
        return McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION.equals(
                exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, String.class));
    }

    public static Map<String, Object> decorateJsonRpcResponse(Exchange exchange, Map<?, ?> response) {
        Map<String, Object> decorated = copyMap(response);
        if (decorated.get("result") instanceof Map<?, ?> result) {
            decorated.put("result", decorateResult(exchange, result));
        }
        return decorated;
    }

    public static Map<String, Object> decorateResult(Exchange exchange, Map<?, ?> result) {
        Map<String, Object> decoratedResult = copyMap(result);
        if (!isModernProtocol(exchange)) {
            return decoratedResult;
        }
        decoratedResult.putIfAbsent("resultType", "complete");
        Map<String, Object> meta = objectValue(decoratedResult.get("_meta"));
        meta.putIfAbsent(SERVER_INFO_META, serverInfo());
        decoratedResult.put("_meta", meta);
        return decoratedResult;
    }

    public static Map<String, Object> serverInfo() {
        Map<String, Object> serverInfo = new LinkedHashMap<>();
        serverInfo.put("name", resolveServerName());
        serverInfo.put("version", resolveServerVersion());
        return serverInfo;
    }

    public static String resolveServerName() {
        return System.getProperty("mcp.server.name", DEFAULT_SERVER_NAME);
    }

    public static String resolveServerVersion() {
        return System.getProperty("mcp.server.version", DEFAULT_SERVER_VERSION);
    }

    private static Map<String, Object> objectValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }
}
