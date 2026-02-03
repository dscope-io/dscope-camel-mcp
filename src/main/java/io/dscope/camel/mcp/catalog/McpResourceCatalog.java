package io.dscope.camel.mcp.catalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.camel.BindToRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.dscope.camel.mcp.model.McpResource;

/**
 * Loads MCP resource definitions from configuration for use by processors.
 */
@BindToRegistry("mcpResourceCatalog")
public class McpResourceCatalog {

    private static final String DEFAULT_RESOURCE = "classpath:mcp/resources.yaml";

    private final Map<String, McpResourceDefinition> resources;

    public McpResourceCatalog() {
        this(loadDefinitionsFromClasspath());
    }

    public McpResourceCatalog(Collection<McpResourceDefinition> definitions) {
        Map<String, McpResourceDefinition> map = new LinkedHashMap<>();
        if (definitions != null) {
            for (McpResourceDefinition definition : definitions) {
                if (definition == null || definition.getUri() == null || definition.getUri().isBlank()) {
                    continue;
                }
                map.put(definition.getUri(), definition);
            }
        }
        this.resources = Collections.unmodifiableMap(map);
    }

    /**
     * Lists all resources as McpResource objects for the resources/list response.
     */
    public List<McpResource> listResources() {
        return resources.values().stream()
                .map(McpResourceDefinition::toResource)
                .toList();
    }

    /**
     * Returns all resource definitions.
     */
    public Collection<McpResourceDefinition> list() {
        return resources.values();
    }

    /**
     * Finds a resource definition by URI.
     */
    public Optional<McpResourceDefinition> findByUri(String uri) {
        if (uri == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(resources.get(uri));
    }

    /**
     * Checks if a resource with the given URI exists.
     */
    public boolean hasResource(String uri) {
        return uri != null && resources.containsKey(uri);
    }

    private static List<McpResourceDefinition> loadDefinitionsFromClasspath() {
        String path = DEFAULT_RESOURCE.substring("classpath:".length());
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = McpResourceCatalog.class.getClassLoader();
        }
        try (InputStream in = cl.getResourceAsStream(path)) {
            if (in == null) {
                return List.of();
            }
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
            McpResourceDefinitions holder = mapper.readValue(in, McpResourceDefinitions.class);
            if (holder.resources == null) {
                return List.of();
            }
            return holder.resources;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load MCP resource definitions from " + DEFAULT_RESOURCE, e);
        }
    }

    private static class McpResourceDefinitions {
        public List<McpResourceDefinition> resources;
    }
}
