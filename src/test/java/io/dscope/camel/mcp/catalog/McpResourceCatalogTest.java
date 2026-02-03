package io.dscope.camel.mcp.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.model.McpResource;

class McpResourceCatalogTest {

    @Test
    void retainsDefinitionsByUri() {
        McpResourceDefinition r1 = new McpResourceDefinition();
        r1.setUri("ui://app1");
        r1.setName("App 1");
        
        McpResourceDefinition r2 = new McpResourceDefinition();
        r2.setUri("ui://app2");
        r2.setName("App 2");
        
        McpResourceDefinition blank = new McpResourceDefinition();
        
        List<McpResourceDefinition> definitions = new ArrayList<>();
        definitions.add(r1);
        definitions.add(r2);
        definitions.add(blank);
        definitions.add(null);
        
        McpResourceCatalog catalog = new McpResourceCatalog(definitions);
        
        assertEquals(2, catalog.list().size());
        assertTrue(catalog.findByUri("ui://app1").isPresent());
        assertTrue(catalog.findByUri("ui://app2").isPresent());
        assertTrue(catalog.findByUri("ui://missing").isEmpty());
    }

    @Test
    void hasResourceReturnsCorrectly() {
        McpResourceDefinition r1 = new McpResourceDefinition();
        r1.setUri("ui://exists");
        
        McpResourceCatalog catalog = new McpResourceCatalog(List.of(r1));
        
        assertTrue(catalog.hasResource("ui://exists"));
        assertFalse(catalog.hasResource("ui://missing"));
        assertFalse(catalog.hasResource(null));
    }

    @Test
    void listResourcesReturnsMcpResourceObjects() {
        McpResourceDefinition r1 = new McpResourceDefinition();
        r1.setUri("ui://test");
        r1.setName("Test");
        r1.setDescription("Description");
        r1.setMimeType("text/html");
        
        McpResourceCatalog catalog = new McpResourceCatalog(List.of(r1));
        
        List<McpResource> resources = catalog.listResources();
        
        assertEquals(1, resources.size());
        assertEquals("ui://test", resources.get(0).getUri());
        assertEquals("Test", resources.get(0).getName());
        assertEquals("Description", resources.get(0).getDescription());
        assertEquals("text/html", resources.get(0).getMimeType());
    }

    @Test
    void emptyCatalogFromNull() {
        McpResourceCatalog catalog = new McpResourceCatalog(null);
        
        assertEquals(0, catalog.list().size());
        assertEquals(0, catalog.listResources().size());
    }
}
