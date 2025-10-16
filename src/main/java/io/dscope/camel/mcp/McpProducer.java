package io.dscope.camel.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dscope.camel.mcp.model.McpRequest;
import io.dscope.camel.mcp.model.McpResponse;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;

import java.util.Map;
import java.util.UUID;

public class McpProducer extends DefaultProducer {
    private final McpEndpoint endpoint;
    private final ObjectMapper mapper = new ObjectMapper();
    public McpProducer(McpEndpoint endpoint) { super(endpoint); this.endpoint = endpoint; }

    @Override
    public void process(Exchange exchange) throws Exception {
        McpConfiguration cfg = endpoint.getConfiguration();
        McpRequest req = new McpRequest();
        req.setJsonrpc("2.0");
        req.setId(UUID.randomUUID().toString());
        req.setMethod(cfg.getMethod());
        req.setParams(exchange.getIn().getBody(Map.class));
        String json = mapper.writeValueAsString(req);
        String result = endpoint.getCamelContext()
            .createProducerTemplate()
            .requestBody(cfg.getUri(), json, String.class);
        McpResponse resp = mapper.readValue(result, McpResponse.class);
        exchange.getMessage().setBody(resp);
    }
}
