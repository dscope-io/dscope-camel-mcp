package io.dscope.camel.mcp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.model.McpResource;

class McpResourceDefinitionTest {

    @Test
    void settersAndGettersWork() {
        McpResourceDefinition def = new McpResourceDefinition();
        def.setUri("ui://app/main");
        def.setName("Main App");
        def.setDescription("The main application");
        def.setMimeType("text/html");
        def.setSource("builtin:mcp-app");
        def.setConfig(java.util.Map.of("title", "My App", "theme", "dark"));
        
        assertEquals("ui://app/main", def.getUri());
        assertEquals("Main App", def.getName());
        assertEquals("The main application", def.getDescription());
        assertEquals("text/html", def.getMimeType());
        assertEquals("builtin:mcp-app", def.getSource());
        assertEquals(2, def.getConfig().size());
        assertEquals("My App", def.getConfig().get("title"));
    }

    @Test
    void toResourceCreatesValidMcpResource() {
        McpResourceDefinition def = new McpResourceDefinition();
        def.setUri("ui://test");
        def.setName("Test Resource");
        def.setDescription("A test resource");
        def.setMimeType("text/plain");
        
        McpResource resource = def.toResource();
        
        assertNotNull(resource);
        assertEquals("ui://test", resource.getUri());
        assertEquals("Test Resource", resource.getName());
        assertEquals("A test resource", resource.getDescription());
        assertEquals("text/plain", resource.getMimeType());
    }
}
