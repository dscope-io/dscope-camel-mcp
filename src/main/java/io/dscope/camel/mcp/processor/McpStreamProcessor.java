package io.dscope.camel.mcp.processor;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Provides a minimal Server-Sent Events handshake body so MCP clients can subscribe even if
 * no events are emitted yet.
 */
@BindToRegistry("mcpStream")
public class McpStreamProcessor implements Processor {

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
        exchange.getIn().setHeader(Exchange.CONTENT_TYPE, "text/event-stream");
        exchange.getIn().setHeader("Cache-Control", "no-store");
        exchange.getIn().setHeader("Connection", "keep-alive");
        exchange.getIn().setBody(":ok\n\n");
    }
}
