package io.dscope.camel.mcp;

import io.dscope.camel.mcp.model.McpRequest;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class McpProducerLocalDispatchTest {

    @Test
    public void shouldDispatchToLocalCamelRouteByUriStructure() throws Exception {
        AtomicReference<McpRequest> seenRequest = new AtomicReference<>();

        try (CamelContext context = createContextForMcpResponse(seenRequest)) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            McpResponse response = template.requestBody(
                    "mcp:camel:direct:local-mcp?method=ping",
                    Map.of("scope", "local"),
                    McpResponse.class
            );

            assertNotNull(response);
            assertTrue(response.getResult() instanceof Map<?, ?>);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertEquals("ping", result.get("method"));
            assertEquals(Boolean.TRUE, result.get("local"));

            McpRequest request = seenRequest.get();
            assertNotNull(request);
            assertEquals("ping", request.getMethod());
            assertEquals("local", request.getParams().get("scope"));
        }
    }

    @Test
    public void shouldNormalizeMapResponseFromLocalCamelRoute() throws Exception {
        try (CamelContext context = createContextForMapResponse()) {
            context.start();
            ProducerTemplate template = context.createProducerTemplate();

            McpResponse response = template.requestBody(
                    "mcp:camel:direct:local-map-response?method=tools/list",
                    Map.of(),
                    McpResponse.class
            );

            assertNotNull(response);
            assertTrue(response.getResult() instanceof Map<?, ?>);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) response.getResult();
            assertEquals("tools/list", result.get("method"));
            assertEquals(1, result.get("count"));
        }
    }

    private CamelContext createContextForMcpResponse(AtomicReference<McpRequest> seenRequest) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("mcp", new McpComponent());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:local-mcp")
                        .process(exchange -> {
                            McpRequest request = exchange.getMessage().getBody(McpRequest.class);
                            seenRequest.set(request);

                            McpResponse response = new McpResponse();
                            response.setJsonrpc("2.0");
                            response.setId(request.getId());
                            response.setResult(Map.of(
                                    "method", request.getMethod(),
                                    "local", true
                            ));
                            exchange.getMessage().setBody(response);
                        });
            }
        });

        return context;
    }

    private CamelContext createContextForMapResponse() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("mcp", new McpComponent());

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:local-map-response")
                        .process(exchange -> {
                            McpRequest request = exchange.getMessage().getBody(McpRequest.class);

                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("method", request.getMethod());
                            result.put("count", 1);

                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("jsonrpc", "2.0");
                            response.put("id", request.getId());
                            response.put("result", result);
                            exchange.getMessage().setBody(response);
                        });
            }
        });

        return context;
    }
}
