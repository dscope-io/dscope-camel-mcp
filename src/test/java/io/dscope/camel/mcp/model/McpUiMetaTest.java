package io.dscope.camel.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class McpUiMetaTest {

    @Test
    void constructorSetsFields() {
        McpUiMeta meta = new McpUiMeta("ui://app", "default-src 'self'", "dark");
        
        assertEquals("ui://app", meta.getResourceUri());
        assertEquals("default-src 'self'", meta.getCsp());
        assertEquals("dark", meta.getTheme());
    }

    @Test
    void defaultConstructorInitializesNull() {
        McpUiMeta meta = new McpUiMeta();
        
        assertNull(meta.getResourceUri());
        assertNull(meta.getCsp());
        assertNull(meta.getTheme());
    }

    @Test
    void settersAndGettersWork() {
        McpUiMeta meta = new McpUiMeta();
        meta.setResourceUri("ui://test");
        meta.setCsp("default-src 'none'");
        meta.setTheme("light");
        
        assertEquals("ui://test", meta.getResourceUri());
        assertEquals("default-src 'none'", meta.getCsp());
        assertEquals("light", meta.getTheme());
    }
}
