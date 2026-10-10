package io.dscope.camel.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.openapi.McpOpenApiBuilder;

class McpSecurityHardeningTest {

    @Test
    void builtInUiUsesDomApisInsteadOfHtmlInjectionSinks() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/io/dscope/camel/mcp/ui/mcp-app.html")) {
            assertNotNull(stream);
            String html = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertFalse(html.contains("innerHTML"));
            assertFalse(html.contains("escapeHtml"));
            assertTrue(html.contains("document.createElement"));
            assertTrue(html.contains("textContent"));
            assertTrue(html.contains("replaceChildren"));
        }
    }

    @Test
    void generatedOpenApiDoesNotAdvertiseNotificationBroadcastEndpoint() {
        String yaml = new McpOpenApiBuilder()
                .includeWebSocketHelpers(true)
                .buildYaml();

        assertFalse(yaml.contains("/mcp/notifications"));
        assertFalse(yaml.contains("postMcpNotifications"));
        assertTrue(yaml.contains("/mcp:"));
    }

    @Test
    void sampleNotificationBroadcastIsInProcessOnly() throws IOException {
        String route = Files.readString(Path.of(
                "samples/mcp-service/src/main/resources/routes/mcp-ws-service.camel.yaml"));

        assertTrue(route.contains("direct:mcp-server-notifications"));
        assertFalse(route.contains("undertow:http://127.0.0.1:8090/mcp/notifications"));
    }
}
