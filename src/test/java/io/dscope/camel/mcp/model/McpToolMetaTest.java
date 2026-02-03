package io.dscope.camel.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;

class McpToolMetaTest {

    @Test
    void constructorSetsFields() {
        McpUiMeta ui = new McpUiMeta("ui://app", null, "dark");
        Map<String, Object> hints = Map.of("displayOrder", 1, "category", "tools");
        
        McpToolMeta meta = new McpToolMeta(ui, hints);
        
        assertNotNull(meta.getUi());
        assertEquals("ui://app", meta.getUi().getResourceUri());
        assertEquals("dark", meta.getUi().getTheme());
        assertNotNull(meta.getHints());
        assertEquals(1, meta.getHints().get("displayOrder"));
        assertEquals("tools", meta.getHints().get("category"));
    }

    @Test
    void defaultConstructorInitializesNull() {
        McpToolMeta meta = new McpToolMeta();
        
        assertNull(meta.getUi());
        assertNull(meta.getHints());
    }

    @Test
    void settersAndGettersWork() {
        McpToolMeta meta = new McpToolMeta();
        McpUiMeta ui = new McpUiMeta("ui://test", null, null);
        Map<String, Object> hints = Map.of("key", "value");
        
        meta.setUi(ui);
        meta.setHints(hints);
        
        assertEquals(ui, meta.getUi());
        assertEquals(hints, meta.getHints());
    }
}
