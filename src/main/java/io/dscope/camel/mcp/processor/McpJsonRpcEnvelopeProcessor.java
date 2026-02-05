package io.dscope.camel.mcp.processor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.TypeConversionException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parses JSON-RPC envelopes and stores metadata on the exchange for downstream processors.
 */
@BindToRegistry("mcpJsonRpcEnvelope")
public class McpJsonRpcEnvelopeProcessor implements Processor {

    public static final String EXCHANGE_PROPERTY_RAW_MESSAGE = "mcp.jsonrpc.raw";
    public static final String EXCHANGE_PROPERTY_TYPE = "mcp.jsonrpc.type";
    public static final String EXCHANGE_PROPERTY_ID = "mcp.jsonrpc.id";
    public static final String EXCHANGE_PROPERTY_METHOD = "mcp.jsonrpc.method";
    public static final String EXCHANGE_PROPERTY_TOOL_NAME = "mcp.tool.name";
    public static final String EXCHANGE_PROPERTY_NOTIFICATION_TYPE = "mcp.notification.type";
    public static final String EXCHANGE_PROPERTY_NOTIFICATION_PARAMS = "mcp.notification.params";

    private enum MessageType {
        REQUEST,
        NOTIFICATION,
        RESPONSE
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() { };

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }

        Message message = exchange.getIn();
        Map<String, Object> payload = readPayload(exchange, message);
        if (payload == null) {
            String bodyType = Optional.ofNullable(exchange.getProperty("mcp.jsonrpc.bodyType", String.class)).orElse("<unknown>");
            String bodyText = Optional.ofNullable(exchange.getProperty("mcp.jsonrpc.bodyText", String.class)).orElse(null);
            int previewLength = bodyText == null ? 0 : Math.min(bodyText.length(), 200);
            String preview = bodyText == null ? "<null>" : bodyText.substring(0, previewLength);
            throw new IllegalArgumentException("JSON-RPC payload must be an object (bodyType=" + bodyType + ", preview=" + preview + ")");
        }

        exchange.setProperty(EXCHANGE_PROPERTY_RAW_MESSAGE, payload);

        String version = asTrimmedString(payload.get("jsonrpc"))
                .orElseThrow(() -> new IllegalArgumentException("jsonrpc version is required"));
        if (!"2.0".equals(version)) {
            throw new IllegalArgumentException("Unsupported jsonrpc version: " + version);
        }

        Object idValue = payload.get("id");
        boolean hasId = payload.containsKey("id") && idValue != null;
        asTrimmedString(payload.get("method"))
                .ifPresentOrElse(method -> handleMethod(exchange, payload, method, hasId, idValue), () -> {
                    if (payload.containsKey("result") || payload.containsKey("error")) {
                        exchange.setProperty(EXCHANGE_PROPERTY_TYPE, MessageType.RESPONSE.name());
                        if (payload.containsKey("id")) {
                            exchange.setProperty(EXCHANGE_PROPERTY_ID, idValue);
                        }
                    } else {
                        throw new IllegalArgumentException("JSON-RPC payload must contain method, result or error");
                    }
                });
    }

    private void handleMethod(Exchange exchange, Map<String, Object> payload, String method, boolean hasId, Object idValue) {
        MessageType type = hasId ? MessageType.REQUEST : MessageType.NOTIFICATION;
        exchange.setProperty(EXCHANGE_PROPERTY_TYPE, type.name());
        exchange.setProperty(EXCHANGE_PROPERTY_METHOD, method);
        if (hasId) {
            exchange.setProperty(EXCHANGE_PROPERTY_ID, idValue);
        }

        Map<String, Object> params = asMap(payload.get("params"), "params");

        switch (method) {
            case "initialize" -> handleInitialize(exchange, params);
            case "ping" -> handlePing(exchange, params);
            case "resources/list" -> handleResourcesList(exchange, params);
            case "resources/read" -> handleResourcesRead(exchange, params);
            case "resources/get" -> handleResourcesGet(exchange, params);
            case "tools/list" -> handleToolsList(exchange, params);
            case "tools/call" -> handleToolsCall(exchange, params);
            case "health" -> handleHealth(exchange, params);
            case "stream" -> handleStream(exchange, params);
            // MCP Apps Bridge methods
            case "ui/initialize" -> handleUiInitialize(exchange, params);
            case "ui/message" -> handleUiMessage(exchange, params);
            case "ui/update-model-context" -> handleUiUpdateModelContext(exchange, params);
            case "ui/tools/call" -> handleUiToolsCall(exchange, params);
            default -> {
                if (method.startsWith("notifications/")) {
                    handleNotification(exchange, method, params);
                } else if (method.startsWith("ui/notifications/")) {
                    handleUiNotification(exchange, method, params);
                } else {
                    throw new IllegalArgumentException("Unsupported MCP method: " + method);
                }
            }
        }
    }

    private void handleInitialize(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handlePing(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleToolsList(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleResourcesList(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleResourcesRead(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleToolsCall(Exchange exchange, Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be provided for tools/call");
        }

        String toolName = asTrimmedString(params.get("name"))
                .orElseThrow(() -> new IllegalArgumentException("params.name is required for tools/call"));
        exchange.setProperty(EXCHANGE_PROPERTY_TOOL_NAME, toolName);

        Map<String, Object> arguments = Optional.ofNullable(asMap(params.get("arguments"), "params.arguments"))
                .orElseGet(LinkedHashMap::new);
        exchange.getIn().setBody(arguments);
    }

    private void handleResourcesGet(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleHealth(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleStream(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleNotification(Exchange exchange, String method, Map<String, Object> params) {
        String type = method.substring("notifications/".length()).trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Notification method must include a type segment");
        }
        Map<String, Object> notificationParams = params == null ? Map.of() : params;
        exchange.getIn().setBody(notificationParams);
    }

    // MCP Apps Bridge handlers

    private void handleUiInitialize(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleUiMessage(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleUiUpdateModelContext(Exchange exchange, Map<String, Object> params) {
        exchange.getIn().setBody(params == null ? Map.of() : params);
    }

    private void handleUiToolsCall(Exchange exchange, Map<String, Object> params) {
        if (params == null) {
            throw new IllegalArgumentException("params must be provided for ui/tools/call");
        }

        // Extract sessionId and set as exchange property (required for session validation)
        asTrimmedString(params.get("sessionId"))
                .ifPresent(sessionId -> exchange.setProperty(McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID, sessionId));

        String toolName = asTrimmedString(params.get("name"))
                .orElseThrow(() -> new IllegalArgumentException("params.name is required for ui/tools/call"));
        exchange.setProperty(EXCHANGE_PROPERTY_TOOL_NAME, toolName);

        Map<String, Object> arguments = Optional.ofNullable(asMap(params.get("arguments"), "params.arguments"))
                .orElseGet(LinkedHashMap::new);
        exchange.getIn().setBody(arguments);
    }

    private void handleUiNotification(Exchange exchange, String method, Map<String, Object> params) {
        String type = method.substring("ui/notifications/".length()).trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("UI notification method must include a type segment");
        }
        Map<String, Object> notificationParams = params == null ? Map.of() : params;
        exchange.getIn().setBody(notificationParams);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(fieldName + " must be an object");
    }

    private Optional<String> asTrimmedString(Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(Exchange exchange, Message message) {
        Object body = message.getBody();
        exchange.setProperty("mcp.jsonrpc.bodyType", body != null ? body.getClass().getName() : "<null>");
        if (body == null) {
            return null;
        }

        if (body instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }

        try {
            String json = message.getBody(String.class);
            if (json == null) {
                byte[] bytes = message.getBody(byte[].class);
                if (bytes != null && bytes.length > 0) {
                    json = new String(bytes, StandardCharsets.UTF_8);
                }
            }

            if (json == null || json.isBlank()) {
                exchange.setProperty("mcp.jsonrpc.bodyText", json);
                return null;
            }
            exchange.setProperty("mcp.jsonrpc.bodyText", json);
            return OBJECT_MAPPER.readValue(json, MAP_TYPE_REFERENCE);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse JSON-RPC payload", e);
        } catch (TypeConversionException e) {
            throw new IllegalArgumentException("Unable to convert JSON-RPC payload to text", e);
        }
    }
}
