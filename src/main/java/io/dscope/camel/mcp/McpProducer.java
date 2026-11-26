package io.dscope.camel.mcp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.model.McpRequest;
import io.dscope.camel.mcp.model.McpResponse;
import io.dscope.camel.mcp.processor.McpHttpValidatorProcessor;

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

    private final McpEndpoint endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    private ProducerTemplate producerTemplate;

    public McpProducer(McpEndpoint endpoint) { super(endpoint); this.endpoint = endpoint; }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        producerTemplate = endpoint.getCamelContext().createProducerTemplate();
        producerTemplate.start();
    }

    @Override
    protected void doStop() throws Exception {
        if (producerTemplate != null) {
            producerTemplate.stop();
            producerTemplate = null;
        }
        super.doStop();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        McpConfiguration cfg = endpoint.getConfiguration();
        long startedAtNanos = System.nanoTime();

        // Build MCP JSON-RPC request envelope.
        McpRequest req = new McpRequest();
        req.setJsonrpc("2.0");
        req.setId(UUID.randomUUID().toString());
        req.setMethod(resolveMethod(exchange, cfg));
        Map<String, Object> params = resolveParams(exchange);
        String protocolVersion = resolveProtocolVersion();
        if (McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION.equals(protocolVersion)) {
            Object currentMeta = params.get("_meta");
            Map<String, Object> meta = currentMeta instanceof Map<?, ?> rawMeta
                    ? copyMap(rawMeta)
                    : new LinkedHashMap<>();
            meta.put(McpHttpValidatorProcessor.META_PROTOCOL_VERSION, protocolVersion);
            if (!(meta.get(McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES) instanceof Map<?, ?>)) {
                meta.put(McpHttpValidatorProcessor.META_CLIENT_CAPABILITIES, Map.of());
            }
            params.put("_meta", meta);
        }
        req.setParams(params);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Dispatching MCP request id={}",req.getId());
        }

        Object resp;
        try {
            resp = dispatchByUriStructure(cfg.getUri(), req, protocolVersion);
        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            LOG.error("MCP request failed id={} durationMs={} reason={}",req.getId(),durationMs,McpDiagnostics.failureSummary(e));
            throw e;
        }

        if (LOG.isDebugEnabled()) {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            boolean hasError = resp instanceof McpResponse mcpResponse && mcpResponse.getError() != null;
            LOG.debug("Received MCP response id={} durationMs={} hasError={}",req.getId(),durationMs,hasError);
        }

        exchange.getMessage().setBody(resp);
    }

    private Object dispatchByUriStructure(String targetUri, McpRequest req, String protocolVersion) throws Exception {
        // Local Camel route dispatch: mcp:camel:<camel-endpoint>
        // Example: mcp:camel:direct:mcp-service?method=ping
        if (targetUri != null && targetUri.startsWith(LOCAL_URI_PREFIX)) {
            String localUri = targetUri.substring(LOCAL_URI_PREFIX.length());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using local MCP dispatch id={}",req.getId());
            }
            Object localResponse = producerTemplate.requestBody(localUri, req, Object.class);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Local MCP response id={}",req.getId());
            }
            return toMcpResponse(localResponse,req.getId());
        }

        // Remote transport dispatch (HTTP/WebSocket/etc) keeps JSON string wire format.
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using remote MCP dispatch id={}",req.getId());
        }
        String json = mapper.writeValueAsString(req);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP request encoded id={}",req.getId());
        }
        Map<String, Object> transportHeaders = buildRemoteTransportHeaders(req, protocolVersion);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP transport headers prepared id={}",req.getId());
        }
        if (McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION.equals(protocolVersion)
                && "subscriptions/listen".equals(req.getMethod())) {
            return requestEventStream(producerTemplate, targetUri, json, transportHeaders);
        }
        String result = producerTemplate.requestBodyAndHeaders(targetUri, json, transportHeaders, String.class);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Remote MCP response received id={}",req.getId());
        }
        return parseRemoteResponse(result, req.getId());
    }

    private McpResponse parseRemoteResponse(String body, String requestId) throws JsonProcessingException {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return toMcpResponse(trimmed,requestId);
        }
        StringBuilder data = new StringBuilder();
        for (String line : trimmed.split("\\R", -1)) {
            if (line.isBlank()) {
                McpResponse response = parseSseEvent(data, requestId);
                if (response != null) {
                    return response;
                }
                data.setLength(0);
            } else if (line.startsWith("data:")) {
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(line.substring("data:".length()).stripLeading());
            }
        }
        McpResponse response = parseSseEvent(data, requestId);
        if (response != null) {
            return response;
        }
        throw new JsonProcessingException("MCP response did not contain a JSON-RPC result for request " + requestId) { };
    }

    private McpResponse parseSseEvent(StringBuilder data, String requestId) throws JsonProcessingException {
        if (data.isEmpty() || "[DONE]".contentEquals(data.toString().trim())) {
            return null;
        }
        McpResponse response = mapper.readValue(data.toString(), McpResponse.class);
        return requestId.equals(response.getId()) && (response.getResult() != null || response.getError() != null)
                ? response : null;
    }

    private InputStream requestEventStream(
            ProducerTemplate template, String targetUri, String requestBody, Map<String, Object> headers) {
        Exchange response = template.request(targetUri, exchange -> {
            exchange.getIn().setBody(requestBody);
            headers.forEach(exchange.getIn()::setHeader);
        });
        if (response.getException() != null) {
            throw new IllegalStateException("MCP subscriptions/listen request failed", response.getException());
        }
        Integer status = response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE, Integer.class);
        if (status != null && (status < 200 || status >= 300)) {
            throw new IllegalStateException("MCP subscriptions/listen returned HTTP status " + status);
        }
        String contentType = response.getMessage().getHeader(Exchange.CONTENT_TYPE, String.class);
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("text/event-stream")) {
            throw new IllegalStateException(
                    "MCP subscriptions/listen response must use Content-Type text/event-stream");
        }

        Object body = response.getMessage().getBody();
        if (body instanceof InputStream stream) {
            return stream;
        }
        if (body instanceof byte[] bytes) {
            return new ByteArrayInputStream(bytes);
        }
        if (body instanceof String text) {
            return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("MCP subscriptions/listen returned no stream body");
    }

    private McpResponse toMcpResponse(Object body,String requestId) {
        if(body==null)throw new IllegalStateException("MCP response body is null");
        if(body instanceof McpResponse response)return response;
        try {
            com.fasterxml.jackson.databind.JsonNode node;
            if(body instanceof com.fasterxml.jackson.databind.JsonNode json)node=json;
            else if(body instanceof String json)node=mapper.readTree(json);
            else if(body instanceof Map<?,?> || body instanceof java.util.List<?>)node=mapper.valueToTree(body);
            else {
                String json=endpoint.getCamelContext().getTypeConverter().tryConvertTo(String.class,body);
                if(json==null)throw new IllegalArgumentException("Unsupported MCP response type");
                node=mapper.readTree(json);
            }
            if(node!=null&&node.isObject()&&(node.has("result")||node.has("error")))
                return mapper.treeToValue(node,McpResponse.class);
            // Compatibility for local legacy tools returning a bare JSON value.
            McpResponse response=new McpResponse();response.setJsonrpc("2.0");response.setId(requestId);
            response.setResult(node==null?mapper.nullNode():node);return response;
        } catch(Exception invalid) {
            // Jackson exceptions can contain source payloads; do not retain them as causes.
            throw new IllegalStateException("Failed to decode MCP response: "+McpDiagnostics.failureSummary(invalid));
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
            return new LinkedHashMap<>();
        }
        if (!(body instanceof Map<?, ?> rawParams)) {
            throw new IllegalArgumentException(
                    "MCP producer expects exchange body to be a Map<String,Object> (or null) to populate JSON-RPC params");
        }

        Map<String, Object> params = new LinkedHashMap<>();
        rawParams.forEach((key, value) -> params.put(String.valueOf(key), value));
        return params;
    }

    private Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private Map<String, Object> buildRemoteTransportHeaders(McpRequest request, String protocolVersion) {
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put("Accept", MCP_ACCEPT);
        headers.put("Content-Type", JSON_CONTENT_TYPE);

        if (protocolVersion != null && !protocolVersion.isBlank()) {
            headers.put("MCP-Protocol-Version", protocolVersion);
        }
        if (McpHttpValidatorProcessor.MODERN_PROTOCOL_VERSION.equals(protocolVersion)) {
            headers.put("Mcp-Method", request.getMethod());
            String name = switch (request.getMethod()) {
                case "tools/call", "prompts/get" -> String.valueOf(request.getParams().get("name"));
                case "resources/read" -> String.valueOf(request.getParams().get("uri"));
                default -> null;
            };
            if (name != null && !"null".equals(name)) {
                headers.put("Mcp-Name", McpHttpValidatorProcessor.encodeHeaderValue(name));
            }
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

}
