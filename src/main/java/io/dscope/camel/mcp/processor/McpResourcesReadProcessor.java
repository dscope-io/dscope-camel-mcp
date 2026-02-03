package io.dscope.camel.mcp.processor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.catalog.McpResourceCatalog;
import io.dscope.camel.mcp.catalog.McpResourceDefinition;
import io.dscope.camel.mcp.model.McpResourceContent;

/**
 * Implements the MCP resources/read method using a registry-backed catalog.
 */
@BindToRegistry("mcpResourcesRead")
public class McpResourcesReadProcessor extends AbstractMcpResponseProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpResourcesReadProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String BUILTIN_UI_PATH = "io/dscope/camel/mcp/ui/";

    private final McpResourceCatalog catalog;

    public McpResourcesReadProcessor() {
        this(new McpResourceCatalog());
    }

    public McpResourcesReadProcessor(McpResourceCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    protected void handleResponse(Exchange exchange) {
        Map<String, Object> params = getRequestParameters(exchange);
        String uri = params != null ? (String) params.get("uri") : null;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing resources/read request id={} uri={}", getJsonRpcId(exchange), uri);
        }

        if (uri == null || uri.isBlank()) {
            writeError(exchange, createError(-32602, "Missing required parameter: uri"), 400);
            return;
        }

        Optional<McpResourceDefinition> optDef = catalog.findByUri(uri);
        if (optDef.isEmpty()) {
            writeError(exchange, createError(-32602, "Resource not found: " + uri), 404);
            return;
        }

        McpResourceDefinition def = optDef.get();

        try {
            String content = loadContent(def);

            // Inject config if it's the built-in UI
            if (def.getSource() != null && def.getSource().startsWith("builtin:")) {
                content = injectConfig(content, def.getConfig());
            }

            McpResourceContent resourceContent = McpResourceContent.text(
                    uri, def.getMimeType(), content);

            Map<String, Object> contentMap = OBJECT_MAPPER.convertValue(resourceContent, Map.class);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("contents", List.of(contentMap));

            writeResult(exchange, result);

            if (LOG.isDebugEnabled()) {
                LOG.debug("resources/read response id={} for uri={}", getJsonRpcId(exchange), uri);
            }
        } catch (Exception e) {
            LOG.error("Failed to load resource: {}", uri, e);
            writeError(exchange, createError(-32603, "Failed to load resource: " + e.getMessage()), 500);
        }
    }

    private Map<String, Object> createError(int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return error;
    }

    private String loadContent(McpResourceDefinition def) throws IOException {
        String source = def.getSource();

        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("Resource source is not defined for: " + def.getUri());
        }

        if (source.equals("builtin:mcp-app")) {
            return loadBuiltinUi("mcp-app.html");
        }

        if (source.startsWith("classpath:")) {
            return loadFromClasspath(source.substring("classpath:".length()));
        }

        throw new IllegalArgumentException("Unknown source type: " + source);
    }

    private String loadBuiltinUi(String filename) throws IOException {
        return loadFromClasspath(BUILTIN_UI_PATH + filename);
    }

    private String loadFromClasspath(String path) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = McpResourcesReadProcessor.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Resource not found on classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String injectConfig(String html, Map<String, String> config) {
        if (config == null || config.isEmpty()) {
            return html;
        }
        try {
            String configJson = OBJECT_MAPPER.writeValueAsString(config);
            // Replace the CONFIG object initialization with merged config
            return html.replace(
                    "const CONFIG = {",
                    "const CONFIG = Object.assign(" + configJson + ", {"
            ).replace(
                    "};  // END CONFIG",
                    "});  // END CONFIG"
            );
        } catch (Exception e) {
            LOG.warn("Failed to inject config into UI", e);
            return html;
        }
    }
}
