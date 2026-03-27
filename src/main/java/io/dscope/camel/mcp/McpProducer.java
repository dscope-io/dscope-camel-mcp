package io.dscope.camel.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpRequest;
import io.dscope.camel.mcp.model.McpResponse;
import io.dscope.camel.mcp.processor.McpHttpValidatorProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Camel producer-side implementation for MCP client calls.
 * <p>
 * It wraps the incoming exchange body into a JSON-RPC 2.0 MCP request,
 * sends it to the configured target URI, and maps the JSON response back
 * to an {@link McpResponse} object.
 */
public class McpProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(McpProducer.class);
    public static final String HEADER_METHOD = "CamelMcpMethod";
    public static final String HEADER_PROTOCOL_VERSION = "CamelMcpProtocolVersion";
    private static final String LOCAL_URI_PREFIX = "camel:";
    private static final String MCP_ACCEPT = "application/json, text/event-stream";
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final int PAYLOAD_PREVIEW_LIMIT = 4000;

    private final McpEndpoint endpoint;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpProducer(McpEndpoint endpoint) { super(endpoint); this.endpoint = endpoint; }

    @Override
    public void process(Exchange exchange) throws Exception {
        McpConfiguration cfg = endpoint.getConfiguration();
        long startedAtNanos = System.nanoTime();

        // Build MCP JSON-RPC request envelope.
        McpRequest req = new McpRequest();
        req.setJsonrpc("2.0");
        req.setId(UUID.randomUUID().toString());
        req.setMethod(resolveMethod(exchange, cfg));
        req.setParams(resolveParams(exchange));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching MCP request id={} method={} targetUri={} paramKeys={} params={}",
                req.getId(), req.getMethod(), cfg.getUri(), req.getParams().keySet(), previewPayload(req.getParams()));
        }

        McpResponse resp;
        try {
            resp = dispatchByUriStructure(cfg.getUri(), req);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            LOG.error("MCP request failed id={} method={} targetUri={} durationMs={}",
                    req.getId(), req.getMethod(), cfg.getUri(), durationMs, e);
            throw e;
        }

        if (LOG.isDebugEnabled()) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            boolean hasError = resp != null && resp.getError() != null;
            LOG.debug("Received MCP response id={} method={} durationMs={} hasError={} response={}",
                req.getId(), req.getMethod(), durationMs, hasError, previewPayload(resp));
        }

        exchange.getMessage().setBody(resp);
    }

    private McpResponse dispatchByUriStructure(String targetUri, McpRequest req) throws Exception {
        // Local Camel route dispatch: mcp:camel:<camel-endpoint>
        // Example: mcp:camel:direct:mcp-service?method=ping
        if (targetUri != null && targetUri.startsWith(LOCAL_URI_PREFIX)) {
            String localUri = targetUri.substring(LOCAL_URI_PREFIX.length());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using local MCP dispatch id={} method={} localUri={}", req.getId(), req.getMethod(), localUri);
            }
            Object localResponse = endpoint.getCamelContext()
                    .createProducerTemplate()
                    .requestBody(localUri, req, Object.class);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Local MCP response id={} method={} localUri={} payload={}",
                        req.getId(), req.getMethod(), localUri, previewPayload(localResponse));
            }
            return toMcpResponse(localResponse);
        }

        // Remote transport dispatch (HTTP/WebSocket/etc) keeps JSON string wire format.
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using remote MCP dispatch id={} method={} uri={}", req.getId(), req.getMethod(), targetUri);
        }
        String json = mapper.writeValueAsString(req);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP request payload id={} method={} uri={} payload={}",
                    req.getId(), req.getMethod(), targetUri, previewText(json));
        }
        Map<String, Object> transportHeaders = buildRemoteTransportHeaders();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP request headers id={} method={} uri={} headers={}",
                req.getId(), req.getMethod(), targetUri, transportHeaders);
        }
        ProducerTemplate template = endpoint.getCamelContext().createProducerTemplate();
        String result = template.requestBodyAndHeaders(targetUri, json, transportHeaders, String.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP response payload id={} method={} uri={} payload={}",
                    req.getId(), req.getMethod(), targetUri, previewText(result));
        }
        return mapper.readValue(result, McpResponse.class);
    }

    private McpResponse toMcpResponse(Object responseBody) {
        if (responseBody == null) {
            LOG.error("MCP local dispatch returned null response body");
            throw new IllegalStateException("MCP response body is null");
        }
        if (responseBody instanceof McpResponse mcpResponse) {
            return mcpResponse;
        }
        if (responseBody instanceof String json) {
            try {
                return mapper.readValue(json, McpResponse.class);
            } catch (JsonProcessingException e) {
                LOG.error("Failed to parse local MCP response JSON size={}B", json.length(), e);
                throw new IllegalStateException("Failed to parse local MCP response JSON", e);
            }
        }
        if (responseBody instanceof Map<?, ?> map) {
            try {
                return mapper.convertValue(map, McpResponse.class);
            } catch (IllegalArgumentException e) {
                LOG.error("Failed to convert local MCP response map keys={} to McpResponse", map.keySet(), e);
                throw e;
            }
        }
        try {
            return mapper.convertValue(responseBody, McpResponse.class);
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to convert local MCP response type={} to McpResponse",
                    responseBody.getClass().getName(), e);
            throw e;
        }
    }

    private String resolveMethod(Exchange exchange, McpConfiguration cfg) {
        String headerMethod = exchange.getIn().getHeader(HEADER_METHOD, String.class);
        if (headerMethod != null && !headerMethod.isBlank()) {
            return headerMethod;
        }
        return cfg.getMethod();
    }

    private Map<String, Object> resolveParams(Exchange exchange) {
        Object body = exchange.getIn().getBody();
        if (body == null) {
            return Map.of();
        }
        if (!(body instanceof Map<?, ?> rawParams)) {
            throw new IllegalArgumentException(
                    "MCP producer expects exchange body to be a Map<String,Object> (or null) to populate JSON-RPC params");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        rawParams.forEach((key, value) -> params.put(String.valueOf(key), value));
        return params;
    }

    private Map<String, Object> buildRemoteTransportHeaders() {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put("Accept", MCP_ACCEPT);
        headers.put("Content-Type", JSON_CONTENT_TYPE);

        String protocolVersion = resolveProtocolVersion();
        if (protocolVersion != null && !protocolVersion.isBlank()) {
            headers.put("MCP-Protocol-Version", protocolVersion);
        }
        return headers;
    }

    private String resolveProtocolVersion() {
        String propertyValue = endpoint.getCamelContext()
                .getGlobalOption(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String systemValue = System.getProperty(HEADER_PROTOCOL_VERSION);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        return null;
    }

    private String previewPayload(Object payload) {
        if (payload == null) {
            return "<null>";
        }
        try {
            if (payload instanceof String text) {
                return previewText(text);
            }
            return previewText(mapper.writeValueAsString(payload));
        } catch (JsonProcessingException | RuntimeException e) {
            return "<unserializable:" + payload.getClass().getName() + ">";
        }
    }

    private String previewText(String text) {
        if (text == null) {
            return "<null>";
        }
        if (text.length() <= PAYLOAD_PREVIEW_LIMIT) {
            return text;
        }
        return text.substring(0, PAYLOAD_PREVIEW_LIMIT) + "...<truncated " + (text.length() - PAYLOAD_PREVIEW_LIMIT) + " chars>";
    }
}
