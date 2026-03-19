package io.dscope.camel.mcp;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

@Component("mcp")
public class McpComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(McpComponent.class);

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        McpConfiguration config = new McpConfiguration();
        config.setUri(remaining);  // Set the URI from the remaining part
        setProperties(config, parameters);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating MCP endpoint uri={} targetUri={} method={} websocket={} paramKeys={}",
                    uri, config.getUri(), config.getMethod(), config.isWebsocket(), parameters.keySet());
        }
        return new McpEndpoint(uri, this, config);
    }
}
