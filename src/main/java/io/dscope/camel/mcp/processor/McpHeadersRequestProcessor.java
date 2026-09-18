package io.dscope.camel.mcp.processor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

@BindToRegistry("mcpHeadersRequestProcessor")
public class McpHeadersRequestProcessor extends AbstractMcpRequestProcessor {

    /** Exchange property containing tool arguments previously exposed as headers. */
    public static final String EXCHANGE_PROPERTY_ARGUMENTS = "mcp.request.arguments";

    @Override
    protected void handleRequest(Exchange exchange, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return;
        }
        // Tool input is untrusted.  Mapping it to headers allows it to alter Camel
        // routing and transport behaviour (for example CamelHttpUri).
        exchange.setProperty(EXCHANGE_PROPERTY_ARGUMENTS,
                Collections.unmodifiableMap(new LinkedHashMap<>(parameters)));
    }
}
