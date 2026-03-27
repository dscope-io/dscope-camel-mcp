package io.dscope.camel.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Java helper API for invoking MCP producer endpoints and extracting MCP result payloads.
 */
public final class McpClient {
    private static final Logger LOG = LoggerFactory.getLogger(McpClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PAYLOAD_PREVIEW_LIMIT = 4000;

    private McpClient() {
    }

    public static McpResponse call(ProducerTemplate template, String mcpEndpointUri, Map<String, Object> params) {
        Map<String, Object> safeParams = nullSafeParams(params);
        if (LOG.isDebugEnabled()) {
            LOG.debug("McpClient call endpoint={} method=<default> params={}",
                    mcpEndpointUri, previewPayload(safeParams));
        }
        McpResponse response = template.requestBody(mcpEndpointUri, safeParams, McpResponse.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("McpClient response endpoint={} method=<default> response={}",
                    mcpEndpointUri, previewPayload(response));
        }
        return response;
    }

    public static McpResponse call(
            ProducerTemplate template,
            String mcpEndpointUri,
            String method,
            Map<String, Object> params
    ) {
        Map<String, Object> safeParams = nullSafeParams(params);
        if (LOG.isDebugEnabled()) {
            LOG.debug("McpClient call endpoint={} method={} params={}",
                mcpEndpointUri, method, previewPayload(safeParams));
        }
        McpResponse response = template.requestBodyAndHeader(
                mcpEndpointUri,
            safeParams,
                McpProducer.HEADER_METHOD,
                method,
                McpResponse.class
        );
        if (LOG.isDebugEnabled()) {
            LOG.debug("McpClient response endpoint={} method={} response={}",
                mcpEndpointUri, method, previewPayload(response));
        }
        return response;
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

    private static String previewPayload(Object payload) {
        if (payload == null) {
            return "<null>";
        }
        try {
            if (payload instanceof String text) {
                return previewText(text);
            }
            return previewText(MAPPER.writeValueAsString(payload));
        } catch (JsonProcessingException | RuntimeException e) {
            return String.valueOf(payload);
        }
    }

    private static String previewText(String text) {
        if (text == null) {
            return "<null>";
        }
        if (text.length() <= PAYLOAD_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, PAYLOAD_PREVIEW_LIMIT) + "...<truncated " + (text.length() - PAYLOAD_PREVIEW_LIMIT) + " chars>";
    }
}
