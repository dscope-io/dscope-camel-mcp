package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.catalog.McpMethodCatalog;
import io.dscope.camel.mcp.catalog.McpMethodDefinition;

/**
 * Implements the MCP tools/list method using a registry-backed catalog.
 */
@BindToRegistry("mcpToolsList")
public class McpToolsListProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpToolsListProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpMethodCatalog catalog;

    public McpToolsListProcessor() {
        this(new McpMethodCatalog());
    }

    public McpToolsListProcessor(McpMethodCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing tools/list request id={} params={}", getJsonRpcId(exchange), params);
        }

        List<Map<String, Object>> tools = catalog.list().stream()
                .map(this::toToolEntry)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tools", tools);

        writeResult(exchange, result);

        if (LOG.isDebugEnabled()) {
            LOG.debug("tools/list response id={} contains {} tools", getJsonRpcId(exchange), tools.size());
        }
    }

    private Map<String, Object> toToolEntry(McpMethodDefinition definition) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("name", definition.getName());
        tool.put("title", definition.getTitle());
        tool.put("description", definition.getDescription());
        tool.put("inputSchema", deepCopy(definition.getInputSchema()));
        tool.put("outputSchema", deepCopy(definition.getOutputSchema()));
        tool.put("annotations", deepCopy(definition.getAnnotations()));
        // Include _meta if present
        if (definition.getMeta() != null) {
            tool.put("_meta", OBJECT_MAPPER.convertValue(definition.getMeta(), Map.class));
        }
        return tool;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return OBJECT_MAPPER.convertValue(source, Map.class);
    }

}
