package io.dscope.camel.mcp;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.main.Main;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class McpComponentTest {

    @Test
    public void testInitializeResponse() throws Exception {
    Main main = new Main();
    main.configure().setRoutesIncludePattern("file:src/test/resources/routes/*.yaml");
    main.start();

    CamelContext context = main.getCamelContext();
    ProducerTemplate template = context.createProducerTemplate();
        String response = template.requestBody("http://localhost:8080/mcp",
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",
                String.class);

        assertTrue(response.contains("protocolVersion"));
        assertTrue(response.contains("2025-06-18"));
        assertTrue(response.contains("mock-mcp"));

    main.stop();
    }
}
