package io.dscope.camel.mcp.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.model.McpUiNotification;

/**
 * Service for sending notifications to MCP App UIs via WebSocket.
 * 
 * This service maintains mappings between sessions and their WebSocket
 * connections, enabling push notifications for tool input/result events.
 */
public class McpWebSocketNotifier {

    private static final Logger LOG = LoggerFactory.getLogger(McpWebSocketNotifier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpUiSessionRegistry sessionRegistry;
    private final CamelContext camelContext;
    private final String webSocketEndpoint;
    private final Map<String, String> sessionToConnectionId = new ConcurrentHashMap<>();

    private ProducerTemplate producerTemplate;

    public McpWebSocketNotifier(McpUiSessionRegistry sessionRegistry, CamelContext camelContext) {
        this(sessionRegistry, camelContext, "undertow:ws://0.0.0.0:8090/mcp");
    }

    public McpWebSocketNotifier(McpUiSessionRegistry sessionRegistry, CamelContext camelContext, String webSocketEndpoint) {
        this.sessionRegistry = sessionRegistry;
        this.camelContext = camelContext;
        this.webSocketEndpoint = webSocketEndpoint;
    }

    /**
     * Starts the notifier and creates the producer template.
     */
    public void start() {
        if (camelContext != null) {
            producerTemplate = camelContext.createProducerTemplate();
            LOG.info("WebSocket notifier started with endpoint: {}", webSocketEndpoint);
        }
    }

    /**
     * Stops the notifier and releases resources.
     */
    public void stop() {
        if (producerTemplate != null) {
            try {
                producerTemplate.stop();
            } catch (Exception e) {
                LOG.warn("Error stopping producer template", e);
            }
        }
        sessionToConnectionId.clear();
    }

    /**
     * Associates a WebSocket connection ID with a session.
     * 
     * @param sessionId    the session ID
     * @param connectionId the WebSocket connection ID
     */
    public void registerConnection(String sessionId, String connectionId) {
        sessionToConnectionId.put(sessionId, connectionId);
        LOG.debug("Registered WebSocket connection {} for session {}", connectionId, sessionId);
    }

    /**
     * Removes a WebSocket connection association.
     * 
     * @param sessionId the session ID
     */
    public void unregisterConnection(String sessionId) {
        String removed = sessionToConnectionId.remove(sessionId);
        if (removed != null) {
            LOG.debug("Unregistered WebSocket connection for session {}", sessionId);
        }
    }

    /**
     * Sends a notification to a specific session.
     * 
     * @param sessionId    the session to notify
     * @param notification the notification to send
     * @return true if the notification was sent
     */
    public boolean sendNotification(String sessionId, McpUiNotification notification) {
        if (producerTemplate == null) {
            LOG.warn("WebSocket notifier not started, cannot send notification");
            return false;
        }

        // Check session exists
        if (!sessionRegistry.exists(sessionId)) {
            LOG.debug("Session {} not found, skipping notification", sessionId);
            return false;
        }

        String connectionId = sessionToConnectionId.get(sessionId);
        if (connectionId == null) {
            LOG.debug("No WebSocket connection for session {}, notification queued", sessionId);
            // Could queue for later delivery if needed
            return false;
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(notification.toMap());
            sendToConnection(connectionId, json);
            LOG.debug("Sent notification to session {}: {}", sessionId, notification.getMethod());
            return true;
        } catch (JsonProcessingException e) {
            LOG.error("Failed to serialize notification", e);
            return false;
        }
    }

    /**
     * Sends a tool input notification to a session.
     * 
     * @param sessionId the session to notify
     * @param toolName  the tool being called
     * @param arguments the tool arguments
     * @return true if sent
     */
    public boolean notifyToolInput(String sessionId, String toolName, Object arguments) {
        return sendNotification(sessionId, McpUiNotification.toolInput(toolName, arguments));
    }

    /**
     * Sends a tool result notification to a session.
     * 
     * @param sessionId the session to notify
     * @param toolName  the tool that was called
     * @param result    the tool result
     * @return true if sent
     */
    public boolean notifyToolResult(String sessionId, String toolName, Object result) {
        return sendNotification(sessionId, McpUiNotification.toolResult(toolName, result));
    }

    /**
     * Sends a tool error notification to a session.
     * 
     * @param sessionId the session to notify
     * @param toolName  the tool that failed
     * @param error     the error message
     * @return true if sent
     */
    public boolean notifyToolError(String sessionId, String toolName, String error) {
        return sendNotification(sessionId, McpUiNotification.toolError(toolName, error));
    }

    /**
     * Broadcasts a notification to all connected sessions.
     * 
     * @param notification the notification to broadcast
     */
    public void broadcast(McpUiNotification notification) {
        if (producerTemplate == null) {
            LOG.warn("WebSocket notifier not started, cannot broadcast");
            return;
        }

        try {
            String json = OBJECT_MAPPER.writeValueAsString(notification.toMap());
            // Use sendToAll header for broadcast
            producerTemplate.sendBodyAndHeader(webSocketEndpoint, json, "CamelWebSocketSendToAll", "true");
            LOG.debug("Broadcast notification: {}", notification.getMethod());
        } catch (Exception e) {
            LOG.error("Failed to broadcast notification", e);
        }
    }

    private void sendToConnection(String connectionId, String message) {
        try {
            // Send to specific connection
            producerTemplate.sendBodyAndHeaders(webSocketEndpoint, message,
                    Map.of(
                            "CamelWebSocketSendToAll", "false",
                            "CamelWebSocketConnectionKey", connectionId));
        } catch (Exception e) {
            LOG.error("Failed to send WebSocket message to connection {}", connectionId, e);
        }
    }
}
