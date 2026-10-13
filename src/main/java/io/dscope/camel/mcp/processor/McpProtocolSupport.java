package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Set;

import org.apache.camel.Exchange;

/**
 * Shared helpers for revision-specific MCP protocol behavior.
 */
public final class McpProtocolSupport {

    private static final String DEFAULT_SERVER_NAME = "camel-mcp-component";
    private static final String DEFAULT_SERVER_VERSION = "dev";
    private static final String SERVER_INFO_META = "io.modelcontextprotocol/serverInfo";
    private static final Set<String> INPUT_REQUIRED_METHODS = Set.of("prompts/get", "resources/read", "tools/call");
    private static final Set<String> INPUT_REQUEST_METHODS = Set.of(
            "elicitation/create", "sampling/createMessage", "roots/list");
    private static final Set<String> CACHEABLE_METHODS = Set.of(
            "server/discover",
            "events/list",
            "tools/list",
            "prompts/list",
            "resources/list",
            "resources/templates/list",
            "resources/read");

    private McpProtocolSupport() {
    }

    public static boolean isModernProtocol(Exchange exchange) {
        return McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION.equals(
                exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION, String.class));
    }

    public static Map<String, Object> decorateJsonRpcResponse(Exchange exchange, Map<?, ?> response) {
        Map<String, Object> decorated = copyMap(response);
        if (!isModernProtocol(exchange)) {
            return decorated;
        }
        if (decorated.containsKey("error") && decorated.get("error") == null && decorated.containsKey("result")) {
            decorated.remove("error");
        }
        if (decorated.containsKey("result") && decorated.get("result") == null && decorated.containsKey("error")) {
            decorated.remove("result");
        }
        if (decorated.containsKey("result") && decorated.containsKey("error")) {
            throw new IllegalArgumentException("MCP JSON-RPC response must not contain both result and error");
        }
        if (decorated.containsKey("result") && decorated.get("result") instanceof Map<?, ?> result) {
            decorated.put("result", decorateResult(exchange, result));
        } else if (decorated.containsKey("result")) {
            throw new IllegalArgumentException("MCP JSON-RPC result must be an object");
        }
        return decorated;
    }

    public static Map<String, Object> decorateResult(Exchange exchange, Map<?, ?> result) {
        Map<String, Object> decoratedResult = copyMap(result);
        if (!isModernProtocol(exchange)) {
            return decoratedResult;
        }
        Object resultTypeValue = decoratedResult.putIfAbsent("resultType", "complete");
        String resultType = resultTypeValue == null
                ? String.valueOf(decoratedResult.get("resultType"))
                : String.valueOf(resultTypeValue);
        if (!"complete".equals(resultType) && !"input_required".equals(resultType)) {
            throw new IllegalArgumentException("Unsupported MCP resultType: " + resultType);
        }

        if ("input_required".equals(resultType)) {
            String method = exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, String.class);
            if (method == null || !INPUT_REQUIRED_METHODS.contains(method)) {
                throw new IllegalArgumentException("input_required is not supported for MCP method: " + method);
            }
            Object inputRequests = decoratedResult.get("inputRequests");
            Object requestState = decoratedResult.get("requestState");
            if (decoratedResult.containsKey("inputRequests") && !(inputRequests instanceof Map<?, ?>)) {
                throw new IllegalStateException("MCP inputRequests must be an object");
            }
            if (decoratedResult.containsKey("requestState")
                    && (!(requestState instanceof String state) || state.isBlank())) {
                throw new IllegalStateException("MCP requestState must be a non-empty string");
            }
            boolean hasInputRequests = inputRequests instanceof Map<?, ?> requests && !requests.isEmpty();
            boolean hasRequestState = requestState instanceof String state && !state.isBlank();
            if (!hasInputRequests && !hasRequestState) {
                throw new IllegalStateException(
                        "An input_required MCP result must include inputRequests or requestState");
            }
            if (hasInputRequests) {
                validateInputRequests(exchange, (Map<?, ?>) inputRequests);
            }
            decoratedResult.remove("ttlMs");
            decoratedResult.remove("cacheScope");
        } else if (isCacheableMethod(exchange)) {
            decoratedResult.putIfAbsent("ttlMs", 0);
            decoratedResult.putIfAbsent("cacheScope", "private");
            validateCacheHints(decoratedResult);
        }

        Map<String, Object> meta = objectValue(decoratedResult.get("_meta"));
        meta.putIfAbsent(SERVER_INFO_META, serverInfo());
        decoratedResult.put("_meta", meta);
        return decoratedResult;
    }

    private static void validateInputRequests(Exchange exchange, Map<?, ?> inputRequests) {
        Map<String, Object> requestMeta = objectValue(
                exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_REQUEST_META));
        Map<String, Object> clientCapabilities = objectValue(requestMeta.get(
                McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES));

        inputRequests.forEach((key, value) -> {
            if (!(key instanceof String requestId) || requestId.isBlank()) {
                throw new IllegalStateException("MCP inputRequests keys must be non-empty strings");
            }
            if (!(value instanceof Map<?, ?> request)) {
                throw new IllegalStateException("MCP inputRequests values must be request objects");
            }
            Object methodValue = request.get("method");
            if (!(methodValue instanceof String method) || !INPUT_REQUEST_METHODS.contains(method)) {
                throw new IllegalStateException("Unsupported MCP input request method: " + methodValue);
            }
            if (!"roots/list".equals(method) && !(request.get("params") instanceof Map<?, ?>)) {
                throw new IllegalStateException("MCP " + method + " input request must include params");
            }
            Map<String, Object> params = objectValue(request.get("params"));
            switch (method) {
                case "elicitation/create" -> validateElicitationInputRequest(clientCapabilities, params);
                case "sampling/createMessage" -> validateSamplingInputRequest(clientCapabilities, params);
                case "roots/list" -> requireClientCapability(clientCapabilities, "roots");
                default -> throw new IllegalStateException("Unsupported MCP input request method: " + method);
            }
        });
    }

    private static void validateElicitationInputRequest(
            Map<String, Object> clientCapabilities, Map<String, Object> params) {
        Map<String, Object> elicitation = requireClientCapability(clientCapabilities, "elicitation");
        String mode = params.get("mode") instanceof String value ? value : "form";
        if (!"form".equals(mode) && !"url".equals(mode)) {
            throw new IllegalStateException("MCP elicitation mode must be form or url");
        }
        if (!(params.get("message") instanceof String message) || message.isBlank()) {
            throw new IllegalStateException("MCP elicitation requests must include a non-empty message");
        }
        if (!elicitation.containsKey(mode) && !("form".equals(mode) && elicitation.isEmpty())) {
            throw new IllegalStateException("MCP client does not support elicitation mode: " + mode);
        }
    }

    private static void validateSamplingInputRequest(
            Map<String, Object> clientCapabilities, Map<String, Object> params) {
        Map<String, Object> sampling = requireClientCapability(clientCapabilities, "sampling");
        if (params.containsKey("tools") && !sampling.containsKey("tools")) {
            throw new IllegalStateException("MCP client does not support sampling with tools");
        }
        if (params.containsKey("includeContext")
                && !"none".equals(params.get("includeContext"))
                && !sampling.containsKey("context")) {
            throw new IllegalStateException("MCP client does not support sampling context");
        }
    }

    private static Map<String, Object> requireClientCapability(
            Map<String, Object> clientCapabilities, String capability) {
        Object value = clientCapabilities.get(capability);
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalStateException("MCP client did not declare the " + capability + " capability");
        }
        return copyMap(map);
    }

    private static void validateCacheHints(Map<String, Object> result) {
        Object ttlMs = result.get("ttlMs");
        if (!(ttlMs instanceof Number ttl) || ttl.longValue() < 0
                || ttl.doubleValue() != ttl.longValue()) {
            throw new IllegalArgumentException("MCP ttlMs must be a non-negative integer");
        }
        Object cacheScope = result.get("cacheScope");
        if (!"public".equals(cacheScope) && !"private".equals(cacheScope)) {
            throw new IllegalArgumentException("MCP cacheScope must be public or private");
        }
    }

    private static boolean isCacheableMethod(Exchange exchange) {
        String method = exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, String.class);
        return method != null && CACHEABLE_METHODS.contains(method);
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
