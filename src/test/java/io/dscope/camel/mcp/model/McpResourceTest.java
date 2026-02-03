package io.dscope.camel.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class McpResourceTest {

    @Test
    void constructorSetsAllFields() {
        McpResource resource = new McpResource("ui://app/main", "Main App", "Description", "text/html");
        
        assertEquals("ui://app/main", resource.getUri());
        assertEquals("Main App", resource.getName());
        assertEquals("Description", resource.getDescription());
        assertEquals("text/html", resource.getMimeType());
    }

    @Test
    void settersAndGettersWork() {
        McpResource resource = new McpResource();
        resource.setUri("ui://test");
        resource.setName("Test");
        resource.setDescription("Test desc");
        resource.setMimeType("application/json");
        
        assertEquals("ui://test", resource.getUri());
        assertEquals("Test", resource.getName());
        assertEquals("Test desc", resource.getDescription());
        assertEquals("application/json", resource.getMimeType());
    }

    @Test
    void equalsBasedOnUri() {
        McpResource r1 = new McpResource("ui://app", "App1", null, null);
        McpResource r2 = new McpResource("ui://app", "App2", "Different", "text/html");
        McpResource r3 = new McpResource("ui://other", "App1", null, null);
        
        assertEquals(r1, r2);
        assertNotEquals(r1, r3);
    }

    @Test
    void hashCodeBasedOnUri() {
        McpResource r1 = new McpResource("ui://app", "App1", null, null);
        McpResource r2 = new McpResource("ui://app", "App2", null, null);
        
        assertEquals(r1.hashCode(), r2.hashCode());
    }
}
