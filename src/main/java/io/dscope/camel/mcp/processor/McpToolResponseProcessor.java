package io.dscope.camel.mcp.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@BindToRegistry("mcpToolResponseProcessor")
public class McpToolResponseProcessor extends AbstractMcpResponseProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        Integer httpCode = exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        boolean abortRoute = Boolean.TRUE.equals(exchange.getProperty("abortRoute", Boolean.class));
        if (abortRoute || (httpCode != null && httpCode >= 400)) {
            writeError(exchange, error(-32602, extractErrorMessage(exchange)), httpCode != null ? httpCode : 400);
            return;
        }

        String toolName = exchange.getProperty("mcp.tool.name", String.class);
        Object resultPayload = normalizeResult(exchange.getMessage().getBody());

        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("status", "ok");
        structuredContent.put("result", resultPayload);
        if (toolName != null && !toolName.isBlank()) {
            structuredContent.put("method", toolName);
        }
        if (resultPayload instanceof List<?> list) {
            structuredContent.put("rowCount", list.size());
        }

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", buildSummary(toolName, resultPayload)));

        Map<String, Object> result = newResultMap();
        result.put("content", content);
        result.put("structuredContent", structuredContent);
        result.put("isError", Boolean.FALSE);
        writeResult(exchange, result);
    }

    private Map<String, Object> error(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private String extractErrorMessage(Exchange exchange) {
        Object body = exchange.getMessage().getBody();
        if (body == null) {
            return "Tool invocation failed";
        }
        if (body instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try {
                    Map<?, ?> parsed = objectMapper.readValue(trimmed, Map.class);
                    Object error = parsed.get("error");
                    if (error instanceof String errorText && !errorText.isBlank()) {
                        return errorText;
                    }
                } catch (JsonProcessingException ignored) {
                }
            }
            return trimmed;
        }
        return String.valueOf(body);
    }

    private Object normalizeResult(Object body) {
        if (!(body instanceof String text)) {
            return body;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, Object.class);
            } catch (JsonProcessingException ignored) {
            }
        }
        return trimmed;
    }

    private String buildSummary(String toolName, Object resultPayload) {
        int rowCount = resultPayload instanceof List<?> list ? list.size() : (resultPayload == null ? 0 : 1);
        String method = (toolName == null || toolName.isBlank()) ? "tool" : toolName;
        return method + " returned " + rowCount + " row(s)";
    }
}