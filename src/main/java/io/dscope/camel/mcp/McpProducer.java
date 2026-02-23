package io.dscope.camel.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpRequest;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.support.DefaultProducer;

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
    public static final String HEADER_METHOD = "CamelMcpMethod";
    private static final String LOCAL_URI_PREFIX = "camel:";

    private final McpEndpoint endpoint;
    private final ObjectMapper mapper = new ObjectMapper();

    public McpProducer(McpEndpoint endpoint) { super(endpoint); this.endpoint = endpoint; }

    @Override
    public void process(Exchange exchange) throws Exception {
        McpConfiguration cfg = endpoint.getConfiguration();

        // Build MCP JSON-RPC request envelope.
        McpRequest req = new McpRequest();
        req.setJsonrpc("2.0");
        req.setId(UUID.randomUUID().toString());
        req.setMethod(resolveMethod(exchange, cfg));
        req.setParams(resolveParams(exchange));

        McpResponse resp = dispatchByUriStructure(cfg.getUri(), req);
        exchange.getMessage().setBody(resp);
    }

    private McpResponse dispatchByUriStructure(String targetUri, McpRequest req) throws Exception {
        // Local Camel route dispatch: mcp:camel:<camel-endpoint>
        // Example: mcp:camel:direct:mcp-service?method=ping
        if (targetUri != null && targetUri.startsWith(LOCAL_URI_PREFIX)) {
            String localUri = targetUri.substring(LOCAL_URI_PREFIX.length());
            Object localResponse = endpoint.getCamelContext()
                    .createProducerTemplate()
                    .requestBody(localUri, req, Object.class);
            return toMcpResponse(localResponse);
        }

        // Remote transport dispatch (HTTP/WebSocket/etc) keeps JSON string wire format.
        String json = mapper.writeValueAsString(req);
        ProducerTemplate template = endpoint.getCamelContext().createProducerTemplate();
        String result = template.requestBody(targetUri, json, String.class);
        return mapper.readValue(result, McpResponse.class);
    }

    private McpResponse toMcpResponse(Object responseBody) {
        if (responseBody == null) {
            throw new IllegalStateException("MCP response body is null");
        }
        if (responseBody instanceof McpResponse mcpResponse) {
            return mcpResponse;
        }
        if (responseBody instanceof String json) {
            try {
                return mapper.readValue(json, McpResponse.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse local MCP response JSON", e);
            }
        }
        if (responseBody instanceof Map<?, ?> map) {
            return mapper.convertValue(map, McpResponse.class);
        }
        return mapper.convertValue(responseBody, McpResponse.class);
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
}
