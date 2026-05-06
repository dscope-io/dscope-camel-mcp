package io.dscope.camel.mcp.processor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WebMcpGatewaySupport {

    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    protected Map<String, Object> readRequest(Exchange exchange) throws IOException {
        Object body = exchange.getMessage().getBody();
        if (body instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        String text = exchange.getMessage().getBody(String.class);
        text = text == null ? "" : text.trim();
        return text.isBlank() ? Map.of() : objectMapper.readValue(text, MAP_TYPE);
    }

    protected Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        return Map.of();
    }

    protected Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    protected Object normalizeResult(Object body) {
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

    protected void writeToolResult(Exchange exchange, Object id, String toolName, Object body, Integer httpCode, boolean abortRoute) {
        if (abortRoute || (httpCode != null && httpCode >= 400)) {
            writeError(exchange, id, -32602, body == null ? "Tool invocation failed" : String.valueOf(body), httpCode == null ? 400 : httpCode);
            return;
        }
        Object resultPayload = normalizeResult(body);
        Map<String, Object> structuredContent = new LinkedHashMap<>();
        structuredContent.put("status", "ok");
        structuredContent.put("method", toolName);
        structuredContent.put("result", resultPayload);
        if (resultPayload instanceof List<?> list) {
            structuredContent.put("rowCount", list.size());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(Map.of(
            "type", "text",
            "text", toolName + " returned " + rowCount(resultPayload) + " row(s)")));
        result.put("structuredContent", structuredContent);
        result.put("isError", Boolean.FALSE);
        writeResult(exchange, id, result);
    }

    protected void callRemoteTool(Exchange exchange,
                                  URI remoteEndpoint,
                                  Object id,
                                  String toolName,
                                  Map<String, Object> arguments,
                                  Map<String, Object> defaultArguments,
                                  String protocolVersion) throws Exception {
        Map<String, Object> effectiveArguments = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        if (defaultArguments != null) {
            defaultArguments.forEach(effectiveArguments::putIfAbsent);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", UUID.randomUUID().toString());
        payload.put("method", "tools/call");
        payload.put("params", Map.of("name", toolName, "arguments", effectiveArguments));

        String resolvedProtocolVersion = protocolVersion == null || protocolVersion.isBlank() ? DEFAULT_PROTOCOL_VERSION : protocolVersion;
        HttpRequest request = HttpRequest.newBuilder(remoteEndpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", resolvedProtocolVersion)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            writeError(exchange, id, -32000, "Remote MCP service call failed: " + ex.getMessage(), 502);
            return;
        }

        Map<String, Object> rpcPayload = parseMap(response.body());
        Object rpcError = rpcPayload.get("error");
        if (response.statusCode() >= 400 || rpcError != null) {
            writeError(exchange, id, -32602, extractRemoteError(response.body(), rpcError), response.statusCode() >= 400 ? response.statusCode() : 400);
            return;
        }
        writeResult(exchange, id, rpcPayload.getOrDefault("result", Map.of()));
    }

    protected void writeNotificationAck(Exchange exchange) {
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json; charset=UTF-8");
        exchange.getMessage().setBody("{}");
    }

    protected void writeInternalError(Exchange exchange, Object id, Exception exception) {
        String message = exception == null ? "WebMCP request failed" : "WebMCP request failed: " + exception.getMessage();
        writeError(exchange, id, -32603, message, 500);
    }

    protected void writeResult(Exchange exchange, Object id, Object result) {
        writeJson(exchange, 200, Map.of(
            "jsonrpc", "2.0",
            "id", id == null ? "" : id,
            "result", result == null ? Map.of() : result));
    }

    protected void writeError(Exchange exchange, Object id, int code, String message, int httpStatus) {
        writeJson(exchange, httpStatus, Map.of(
            "jsonrpc", "2.0",
            "id", id == null ? "" : id,
            "error", Map.of(
                "code", code,
                "message", message == null || message.isBlank() ? "WebMCP request failed" : message)));
    }

    private Map<String, Object> parseMap(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        }
        StringBuilder data = new StringBuilder();
        for (String line : trimmed.split("\\R", -1)) {
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
        String json = data.toString().trim();
        return json.isBlank() || "[DONE]".equals(json) ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
    }

    private int rowCount(Object resultPayload) {
        return resultPayload instanceof List<?> list ? list.size() : (resultPayload == null ? 0 : 1);
    }

    private String extractRemoteError(String body, Object rpcError) {
        if (rpcError instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            if (message instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return body == null || body.isBlank() ? "Tool invocation failed" : body;
    }

    private void writeJson(Exchange exchange, int statusCode, Map<String, Object> body) {
        try {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json; charset=UTF-8");
            exchange.getMessage().setBody(objectMapper.writeValueAsString(body));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to write WebMCP response", ex);
        }
    }
}