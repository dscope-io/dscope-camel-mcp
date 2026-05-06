package io.dscope.camel.mcp.processor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RemoteMcpToolCallProxyProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteMcpToolCallProxyProcessor.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final URI remoteEndpoint;
    private final Map<String, Object> defaultArguments;
    private final String protocolVersion;

    public RemoteMcpToolCallProxyProcessor(String remoteEndpointUri, Map<String, Object> defaultArguments) {
        this(remoteEndpointUri, defaultArguments, "2025-06-18");
    }

    public RemoteMcpToolCallProxyProcessor(String remoteEndpointUri, Map<String, Object> defaultArguments, String protocolVersion) {
        this.remoteEndpoint = URI.create(remoteEndpointUri);
        this.defaultArguments = defaultArguments == null ? Map.of() : Map.copyOf(defaultArguments);
        this.protocolVersion = protocolVersion == null || protocolVersion.isBlank() ? "2025-06-18" : protocolVersion;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        String toolName = getToolName(exchange);
        Map<String, Object> arguments = getRequestParameters(exchange, true);
        defaultArguments.forEach(arguments::putIfAbsent);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", UUID.randomUUID().toString());
        payload.put("method", "tools/call");
        payload.put("params", Map.of("name", toolName, "arguments", arguments));

        HttpRequest request = HttpRequest.newBuilder(remoteEndpoint)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", protocolVersion)
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            exchange.setProperty("abortRoute", Boolean.TRUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 502);
            exchange.getMessage().setBody("Remote MCP call failed: " + ex.getMessage());
            return;
        }

        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, response.statusCode());
        Map<String, Object> rpcPayload;
        try {
            rpcPayload = parseBody(response.body());
        } catch (IOException ex) {
            exchange.setProperty("abortRoute", Boolean.TRUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 502);
            exchange.getMessage().setBody("Remote MCP returned an unreadable response");
            LOG.warn("Remote MCP proxy received unreadable response: tool={}, status={}, body={}", toolName, response.statusCode(), response.body(), ex);
            return;
        }
        Object rpcError = rpcPayload.get("error");
        if (response.statusCode() >= 400 || rpcError != null) {
            exchange.setProperty("abortRoute", Boolean.TRUE);
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, response.statusCode() >= 400 ? response.statusCode() : 400);
            exchange.getMessage().setBody(extractErrorMessage(response.body(), rpcError));
            LOG.warn("Remote MCP proxy failed: tool={}, status={}, body={}", toolName, response.statusCode(), response.body());
            return;
        }

        exchange.getMessage().setBody(rpcPayload.getOrDefault("result", Map.of()));
        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
    }

    public Map<String, Object> parseBody(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        String trimmed = body.trim();
        if (trimmed.startsWith("{")) {
            return objectMapper.readValue(trimmed, MAP_TYPE);
        }
        Map<String, Object> ssePayload = parseSseBody(trimmed);
        if (!ssePayload.isEmpty()) {
            return ssePayload;
        }
        throw new IOException("Remote MCP response is not JSON or SSE JSON-RPC");
    }

    private Map<String, Object> parseSseBody(String body) throws IOException {
        StringBuilder data = new StringBuilder();
        for (String line : body.split("\\R", -1)) {
            if (line.isBlank()) {
                Map<String, Object> event = parseSseData(data);
                if (!event.isEmpty()) {
                    return event;
                }
                data.setLength(0);
                continue;
            }
            if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
        return parseSseData(data);
    }

    private Map<String, Object> parseSseData(StringBuilder data) throws IOException {
        if (data.isEmpty()) {
            return Map.of();
        }
        String json = data.toString().trim();
        if (json.isBlank() || "[DONE]".equals(json)) {
            return Map.of();
        }
        return objectMapper.readValue(json, MAP_TYPE);
    }

    private String extractErrorMessage(String body, Object rpcError) {
        if (rpcError instanceof Map<?, ?> errorMap) {
            Object message = errorMap.get("message");
            if (message instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return body == null || body.isBlank() ? "Remote MCP call failed" : body;
    }
}