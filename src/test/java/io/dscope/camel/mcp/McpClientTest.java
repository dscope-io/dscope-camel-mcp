package io.dscope.camel.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class McpClientTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String MCP_ENDPOINT = "mcp:direct:mcp-server?method=initialize";

    @Test
    public void shouldUseEndpointConfiguredMethodByDefault() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            McpResponse response = McpClient.call(template, MCP_ENDPOINT, Map.of("client", "test"));
            assertNotNull(response);
            assertTrue(response.getResult() instanceof Map<?, ?>);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertEquals("initialize", result.get("method"));

            @SuppressWarnings("unchecked")
            Map<String, Object> params = (Map<String, Object>) result.get("params");
            assertEquals("test", params.get("client"));
        }
    }

    @Test
    public void shouldOverrideMethodFromHeaderViaHelper() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            Object resultObject = McpClient.callResult(template, MCP_ENDPOINT, "tools/list", Map.of());
            assertTrue(resultObject instanceof Map<?, ?>);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) resultObject;
            assertEquals("tools/list", result.get("method"));
            assertTrue(result.get("tools") instanceof List<?>);
        }
    }

    @Test
    public void shouldProvidePingAndToolsListHelpers() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            Object pingResultObject = McpClient.pingResult(template, MCP_ENDPOINT);
            assertTrue(pingResultObject instanceof Map<?, ?>);
            @SuppressWarnings("unchecked")
            Map<String, Object> pingResult = (Map<String, Object>) pingResultObject;
            assertEquals("ping", pingResult.get("method"));
            assertEquals(Boolean.TRUE, pingResult.get("pong"));

            Object toolsResultObject = McpClient.toolsListResult(template, MCP_ENDPOINT);
            assertTrue(toolsResultObject instanceof Map<?, ?>);
            @SuppressWarnings("unchecked")
            Map<String, Object> toolsResult = (Map<String, Object>) toolsResultObject;
            assertEquals("tools/list", toolsResult.get("method"));
            assertTrue(toolsResult.get("tools") instanceof List<?>);
        }
    }

    @Test
    public void shouldReturnJsonObjectResults() throws Exception {
        try (CamelContext context = createContext()) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            JsonNode pingResult = McpClient.pingResultJson(template, MCP_ENDPOINT);
            assertNotNull(pingResult);
            assertTrue(pingResult.isObject());
            assertEquals("ping", pingResult.path("method").asText());
            assertTrue(pingResult.path("pong").asBoolean());

            JsonNode toolsResult = McpClient.toolsListResultJson(template, MCP_ENDPOINT);
            assertNotNull(toolsResult);
            assertTrue(toolsResult.isObject());
            assertEquals("tools/list", toolsResult.path("method").asText());
            assertTrue(toolsResult.path("tools").isArray());
            assertFalse(toolsResult.path("tools").isEmpty());
        }
    }

    private CamelContext createContext() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("mcp", new McpComponent());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:mcp-server")
                        .process(exchange -> {
                            String requestJson = exchange.getMessage().getBody(String.class);
                            Map<String, Object> request = MAPPER.readValue(
                                    requestJson,
                                    new TypeReference<>() {
                                    }
                            );

                            String method = String.valueOf(request.get("method"));
                            Object id = request.get("id");

                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("method", method);
                            result.put("params", request.get("params"));
                            if ("ping".equals(method)) {
                                result.put("pong", true);
                            }
                            if ("tools/list".equals(method)) {
                                result.put("tools", List.of(Map.of("name", "echo")));
                            }

                            Map<String, Object> response = Map.of(
                                    "jsonrpc", "2.0",
                                    "id", id,
                                    "result", result
                            );
                            exchange.getMessage().setBody(MAPPER.writeValueAsString(response));
                        });
            }
        });

        return context;
    }
}
