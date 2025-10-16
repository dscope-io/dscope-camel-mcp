package io.dscope.camel.mcp;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import java.util.Map;

@Component("mcp")
public class McpComponent extends DefaultComponent {
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        McpConfiguration config = new McpConfiguration();
        setProperties(config, parameters);
        return new McpEndpoint(uri, this, config);
    }
}
