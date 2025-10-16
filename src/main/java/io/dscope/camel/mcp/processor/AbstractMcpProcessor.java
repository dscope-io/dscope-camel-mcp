package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/**
 * Base MCP processor offering shared utilities for working with JSON-RPC
 * metadata, request payloads, and standard response headers. Resource-specific
 * processors should extend {@link AbstractMcpRequestProcessor} or
 * {@link AbstractMcpResponseProcessor}, which in turn build on this class.
 */
public abstract class AbstractMcpProcessor implements Processor {

    @Override
    public final void process(Exchange exchange) throws Exception {
        doProcess(requireExchange(exchange));
    }

    /**
     * Template method executed once the exchange has been validated.
     */
    protected abstract void doProcess(Exchange exchange) throws Exception;

    protected final Exchange requireExchange(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }
        return exchange;
    }

    protected final Message in(Exchange exchange) {
        return exchange.getIn();
    }

    protected final Object getJsonRpcId(Exchange exchange) {
        return exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID);
    }

    protected final String getJsonRpcMethod(Exchange exchange) {
        return exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD, String.class);
    }

    protected final String getJsonRpcType(Exchange exchange) {
        return exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE, String.class);
    }

    protected final String getToolName(Exchange exchange) {
        return exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TOOL_NAME, String.class);
    }

    protected final String resolveProtocolVersion(Exchange exchange) {
        Object version = exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION);
        if (version == null) {
            version = McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION;
        }
        return version.toString();
    }

    protected final void setProtocolHeaders(Exchange exchange, String protocolVersion) {
        Message message = in(exchange);
        message.setHeader("MCP-Protocol-Version", protocolVersion);
        message.setHeader("Cache-Control", "no-store");
    }

    protected final void applyJsonResponseHeaders(Exchange exchange, int statusCode) {
        Message message = in(exchange);
        if (statusCode > 0) {
            message.setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
        }
        message.setHeader(Exchange.CONTENT_TYPE, "application/json");
        setProtocolHeaders(exchange, resolveProtocolVersion(exchange));
    }

    protected final void writeJson(Exchange exchange, Map<String, Object> payload) {
        McpJsonWriter.writeJson(exchange, payload);
    }

    protected final Map<String, Object> createEnvelopeSkeleton() {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        return envelope;
    }

    @SuppressWarnings("unchecked")
    protected final Map<String, Object> getRequestParameters(Exchange exchange) {
        Message in = in(exchange);
        Map<String, Object> params = in.getBody(Map.class);
        if (params == null) {
            params = Map.of();
        }
        return params;
    }

    @SuppressWarnings("unchecked")
    protected final Map<String, Object> getRequestParameters(Exchange exchange, boolean mutableCopy) {
        Message in = in(exchange);
        Map<String, Object> params = in.getBody(Map.class);
        if (params == null) {
            if (mutableCopy) {
                params = new LinkedHashMap<>();
                in.setBody(params);
            } else {
                params = Map.of();
            }
            return params;
        }
        if (mutableCopy && !(params instanceof LinkedHashMap<?, ?>)) {
            params = new LinkedHashMap<>(params);
            in.setBody(params);
        }
        return params;
    }
}
