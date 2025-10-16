package io.dscope.camel.mcp.catalog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class McpMethodDefinitionTest {

    @Test
    void fallsBackToNameWhenTitleMissing() {
        McpMethodDefinition definition = new McpMethodDefinition();
        definition.setName("echo");

        assertEquals("echo", definition.getTitle());
    }

    @Test
    void extractsRequiredArgumentsFromInputSchema() {
        McpMethodDefinition definition = new McpMethodDefinition();
        definition.setInputSchema(Map.of(
                "type", "object",
                "required", List.of("subject", "message")));

        List<String> required = definition.getRequiredArguments();
        assertEquals(List.of("subject", "message"), required);
        assertTrue(required instanceof List<?>);
    }

    @Test
    void toToolEntryProvidesUnmodifiableViews() {
        McpMethodDefinition definition = new McpMethodDefinition();
        definition.setName("echo");
        definition.setTitle("Echo");
        definition.setDescription("Returns input");
        definition.setInputSchema(Map.of("type", "object"));
        definition.setOutputSchema(Map.of("type", "string"));
        definition.setAnnotations(Map.of("category", "utility"));

        Map<String, Object> entry = definition.toToolEntry();
        assertEquals("echo", entry.get("name"));
        assertEquals("Echo", entry.get("title"));
        assertEquals("Returns input", entry.get("description"));
        assertEquals(Map.of("type", "object"), entry.get("inputSchema"));
        assertEquals(Map.of("type", "string"), entry.get("outputSchema"));
        assertEquals(Map.of("category", "utility"), entry.get("annotations"));
    }
}
