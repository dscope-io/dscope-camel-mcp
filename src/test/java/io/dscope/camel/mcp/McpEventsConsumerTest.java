package io.dscope.camel.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.events.McpEventsProvider;
import io.dscope.camel.mcp.events.McpCallbackException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpEventsConsumerTest {
    @Test void registeredProviderEnablesDiscoveryDispatchAndSafeErrorsOverHttp() throws Exception {
        int port;
        try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        String url = "http://localhost:" + port + "/mcp";
        try (var context = new DefaultCamelContext(); var client = HttpClient.newHttpClient()) {
            context.getRegistry().bind("events", new McpEventsProvider() {
                public Map<String,Object> list(Exchange e, Map<String,Object> p) { return Map.of("events", List.of()); }
                public Map<String,Object> subscribe(Exchange e, Map<String,Object> p) { throw new McpCallbackException("timeout"); }
                public void unsubscribe(Exchange e, Map<String,Object> p) { }
            });
            context.addRoutes(new RouteBuilder() {
                public void configure() {
                    from("mcp:" + url).process(e -> fail("Events must dispatch to registered provider"));
                }
            });
            context.start();
            var json = new ObjectMapper();
            for (String method : List.of("server/discover", "events/list", "events/subscribe", "events/unsubscribe")) {
                var params = new java.util.LinkedHashMap<String,Object>();
                params.put("_meta", Map.of("io.modelcontextprotocol/protocolVersion", "2026-07-28",
                        "io.modelcontextprotocol/clientCapabilities", Map.of()));
                if (method.equals("events/subscribe") || method.equals("events/unsubscribe")) {
                    params.put("name", "comment.created");
                    params.put("arguments", Map.of());
                    params.put("delivery", Map.of("mode", "webhook", "url", "https://receiver.example",
                            "secret", "whsec_" + java.util.Base64.getEncoder().encodeToString(new byte[32])));
                }
                var request = HttpRequest.newBuilder(URI.create(url))
                        .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                        .header("MCP-Protocol-Version", "2026-07-28").header("Mcp-Method", method)
                        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                                Map.of("jsonrpc", "2.0", "id", 7, "method", method, "params", params)))).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                var body = json.readTree(response.body());
                assertEquals(7, body.path("id").asInt());
                if (method.equals("events/subscribe")) {
                    assertEquals(-32015, body.path("error").path("code").asInt(), response.body());
                    assertEquals("timeout", body.path("error").path("data").path("reason").asText());
                } else {
                    assertEquals(200, response.statusCode(), response.body());
                    assertEquals("complete", body.path("result").path("resultType").asText());
                    if (method.equals("server/discover")) assertTrue(body.path("result").path("capabilities").has("events"));
                }
            }
        }
    }
}
