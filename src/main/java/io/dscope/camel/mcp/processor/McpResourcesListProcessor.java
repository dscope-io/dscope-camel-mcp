package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.catalog.McpResourceCatalog;
import io.dscope.camel.mcp.model.McpResource;

/**
 * Implements the MCP resources/list method using a registry-backed catalog.
 */
@BindToRegistry("mcpResourcesList")
public class McpResourcesListProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpResourcesListProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpResourceCatalog catalog;

    public McpResourcesListProcessor() {
        this(new McpResourceCatalog());
    }

    public McpResourcesListProcessor(McpResourceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing resources/list request id={} params={}", getJsonRpcId(exchange), params);
        }

        List<McpResource> resources = catalog.listResources();

        List<Map<String, Object>> resourceMaps = resources.stream()
                .map(r -> OBJECT_MAPPER.convertValue(r, Map.class))
                .map(m -> (Map<String, Object>) m)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resources", resourceMaps);

        writeResult(exchange, result);

        if (LOG.isDebugEnabled()) {
            LOG.debug("resources/list response id={} contains {} resources", getJsonRpcId(exchange), resources.size());
        }
    }
}
