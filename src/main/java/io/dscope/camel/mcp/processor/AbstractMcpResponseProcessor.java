package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;

/**
 * Base class for MCP response processors. It offers convenience helpers to
 * produce JSON-RPC result or error envelopes and to apply the standard HTTP
 * response headers used by the component.
 */
public abstract class AbstractMcpResponseProcessor extends AbstractMcpProcessor {

    @Override
    protected final void doProcess(Exchange exchange) throws Exception {
        handleResponse(exchange);
    }

    /**
     * Implement response-specific logic.
     */
    protected abstract void handleResponse(Exchange exchange) throws Exception;

    protected final void writeResult(Exchange exchange, Map<String, Object> result) {
        Map<String, Object> envelope = createEnvelopeSkeleton();
        envelope.put("id", getJsonRpcId(exchange));
        envelope.put("result", result == null ? Map.of() : result);
        writeJson(exchange, envelope);
        applyJsonResponseHeaders(exchange, 200);
    }

    protected final void writeError(Exchange exchange, Map<String, Object> error, int statusCode) {
        Map<String, Object> envelope = createEnvelopeSkeleton();
        envelope.put("id", getJsonRpcId(exchange));
        envelope.put("error", error == null ? Map.of() : error);
        writeJson(exchange, envelope);
        applyJsonResponseHeaders(exchange, statusCode);
    }

    protected final Map<String, Object> newResultMap() {
        return new LinkedHashMap<>();
    }

    protected final void applyNoContentResponse(Exchange exchange, int statusCode) {
        exchange.getIn().setBody(null);
        exchange.getIn().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        setProtocolHeaders(exchange, resolveProtocolVersion(exchange));
    }
}
