package io.dscope.camel.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.ProducerTemplate;

import java.util.Map;

/**
 * Java helper API for invoking MCP producer endpoints and extracting MCP result payloads.
 */
public final class McpClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpClient() {
    }

    public static McpResponse call(ProducerTemplate template, String mcpEndpointUri, Map<String, Object> params) {
        return template.requestBody(mcpEndpointUri, nullSafeParams(params), McpResponse.class);
    }

    public static McpResponse call(
            ProducerTemplate template,
            String mcpEndpointUri,
            String method,
            Map<String, Object> params
    ) {
        return template.requestBodyAndHeader(
                mcpEndpointUri,
                nullSafeParams(params),
                McpProducer.HEADER_METHOD,
                method,
                McpResponse.class
        );
    }

    public static Object callResult(ProducerTemplate template, String mcpEndpointUri, Map<String, Object> params) {
        McpResponse response = call(template, mcpEndpointUri, params);
        return response != null ? response.getResult() : null;
    }

    public static Object callResult(
            ProducerTemplate template,
            String mcpEndpointUri,
            String method,
            Map<String, Object> params
    ) {
        McpResponse response = call(template, mcpEndpointUri, method, params);
        return response != null ? response.getResult() : null;
    }

    public static JsonNode callResultJson(
            ProducerTemplate template,
            String mcpEndpointUri,
            Map<String, Object> params
    ) {
        return toJsonNode(callResult(template, mcpEndpointUri, params));
    }

    public static JsonNode callResultJson(
            ProducerTemplate template,
            String mcpEndpointUri,
            String method,
            Map<String, Object> params
    ) {
        return toJsonNode(callResult(template, mcpEndpointUri, method, params));
    }

    public static Object pingResult(ProducerTemplate template, String mcpEndpointUri) {
        return callResult(template, mcpEndpointUri, "ping", Map.of());
    }

    public static Object toolsListResult(ProducerTemplate template, String mcpEndpointUri) {
        return callResult(template, mcpEndpointUri, "tools/list", Map.of());
    }

    public static JsonNode pingResultJson(ProducerTemplate template, String mcpEndpointUri) {
        return callResultJson(template, mcpEndpointUri, "ping", Map.of());
    }

    public static JsonNode toolsListResultJson(ProducerTemplate template, String mcpEndpointUri) {
        return callResultJson(template, mcpEndpointUri, "tools/list", Map.of());
    }

    private static Map<String, Object> nullSafeParams(Map<String, Object> params) {
        return params != null ? params : Map.of();
    }

    private static JsonNode toJsonNode(Object value) {
        return value == null ? null : MAPPER.valueToTree(value);
    }
}
