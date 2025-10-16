package io.dscope.camel.mcp.processor;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the notification type and parameters from an exchange populated by
 * {@link McpJsonRpcEnvelopeProcessor} so downstream routes can branch on the
 * notification metadata. The processor assumes JSON-RPC notifications use the
 * {@code notifications/<type>} naming convention defined by the MCP spec.
 */
public class McpNotificationProcessor extends AbstractMcpRequestProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(McpNotificationProcessor.class);

    @Override
    protected void handleRequest(Exchange exchange, Map<String, Object> parameters) {
        String method = getJsonRpcMethod(exchange);
        if (method == null || !method.startsWith("notifications/")) {
            throw new IllegalArgumentException("Exchange does not hold an MCP notification");
        }

        String type = method.substring("notifications/".length()).trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("Notification method must include a type segment");
        }

        Message message = exchange.getIn();
        Map<String, Object> params = parameters;
        if (params == null || params.isEmpty()) {
            params = extractParams(message);
        }

        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_NOTIFICATION_PARAMS, params);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_NOTIFICATION_TYPE, type);

        if (LOG.isInfoEnabled()) {
            LOG.info("Received MCP notification '{}' with params {}", type, params);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractParams(Message message) {
        Map<String, Object> params = message.getBody(Map.class);
        if (params == null) {
            params = Map.of();
            message.setBody(params);
        }
        return params;
    }
}
