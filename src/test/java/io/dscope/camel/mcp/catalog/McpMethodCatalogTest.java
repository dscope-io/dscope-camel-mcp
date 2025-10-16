package io.dscope.camel.mcp.catalog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class McpMethodCatalogTest {

    @Test
    void retainsDefinitionsByName() {
        McpMethodDefinition alpha = new McpMethodDefinition();
        alpha.setName("alpha");
        McpMethodDefinition beta = new McpMethodDefinition();
        beta.setName("beta");
        McpMethodDefinition blank = new McpMethodDefinition();

    List<McpMethodDefinition> definitions = new ArrayList<>();
    definitions.add(alpha);
    definitions.add(beta);
    definitions.add(blank);
    definitions.add(null);

    McpMethodCatalog catalog = new McpMethodCatalog(definitions);

        assertEquals(2, catalog.list().size());
        assertTrue(catalog.findByName("alpha").isPresent());
        assertTrue(catalog.findByName("beta").isPresent());
        assertTrue(catalog.findByName("missing").isEmpty());
    }
}
