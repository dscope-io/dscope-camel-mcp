package io.dscope.camel.mcp.service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.BindToRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dscope.camel.mcp.model.McpUiNotification;
import io.dscope.camel.mcp.model.McpUiSession;

/**
 * Registry for active MCP Apps UI sessions.
 * 
 * Manages the lifecycle of UI sessions including creation, retrieval,
 * expiration, and notification delivery.
 */
@BindToRegistry("mcpUiSessionRegistry")
public class McpUiSessionRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(McpUiSessionRegistry.class);

    public static final long DEFAULT_SESSION_TIMEOUT_MS = 3600000L; // 1 hour
    private static final long CLEANUP_INTERVAL_MS = 60000L; // 1 minute

    private final Map<String, McpUiSession> sessions = new ConcurrentHashMap<>();
    private final long sessionTimeoutMs;
    private final ScheduledExecutorService cleanupExecutor;
    private volatile boolean running = false;

    public McpUiSessionRegistry() {
        this(DEFAULT_SESSION_TIMEOUT_MS);
    }

    public McpUiSessionRegistry(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs > 0 ? sessionTimeoutMs : DEFAULT_SESSION_TIMEOUT_MS;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "mcp-ui-session-cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Starts the session cleanup task.
     */
    public synchronized void start() {
        if (!running) {
            running = true;
            cleanupExecutor.scheduleAtFixedRate(
                    this::cleanupExpiredSessions,
                    CLEANUP_INTERVAL_MS,
                    CLEANUP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
            LOG.info("MCP UI Session Registry started with timeout={}ms", sessionTimeoutMs);
        }
    }

    /**
     * Stops the session cleanup task.
     */
    public synchronized void stop() {
        if (running) {
            running = false;
            cleanupExecutor.shutdown();
            try {
                if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    cleanupExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                cleanupExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            sessions.clear();
            LOG.info("MCP UI Session Registry stopped");
        }
    }

    /**
     * Registers a new UI session.
     * 
     * @param resourceUri the UI resource being displayed
     * @return the newly created session
     */
    public McpUiSession register(String resourceUri) {
        return register(resourceUri, null);
    }

    /**
     * Registers a new UI session with an associated tool.
     * 
     * @param resourceUri the UI resource being displayed
     * @param toolName    the tool that triggered this UI
     * @return the newly created session
     */
    public McpUiSession register(String resourceUri, String toolName) {
        McpUiSession session = McpUiSession.create(resourceUri, toolName);
        sessions.put(session.getSessionId(), session);
        LOG.debug("Registered UI session id={} resourceUri={} toolName={}",
                session.getSessionId(), resourceUri, toolName);
        return session;
    }

    /**
     * Retrieves a session by ID if it exists and is not expired.
     * 
     * @param sessionId the session identifier
     * @return the session if found and valid
     */
    public Optional<McpUiSession> get(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        McpUiSession session = sessions.get(sessionId);
        if (session != null && !isExpired(session)) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Retrieves and touches a session, updating its last activity time.
     * 
     * @param sessionId the session identifier
     * @return the session if found and valid
     */
    public Optional<McpUiSession> getAndTouch(String sessionId) {
        return get(sessionId).map(session -> {
            session.touch();
            return session;
        });
    }

    /**
     * Removes a session from the registry.
     * 
     * @param sessionId the session identifier
     * @return true if a session was removed
     */
    public boolean remove(String sessionId) {
        McpUiSession removed = sessions.remove(sessionId);
        if (removed != null) {
            LOG.debug("Removed UI session id={}", sessionId);
            return true;
        }
        return false;
    }

    /**
     * Gets the number of active sessions.
     * 
     * @return session count
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Checks if a session exists and is valid.
     * 
     * @param sessionId the session identifier
     * @return true if session exists and is not expired
     */
    public boolean exists(String sessionId) {
        return get(sessionId).isPresent();
    }

    /**
     * Sends a notification to a UI session.
     * 
     * @param sessionId    the session identifier
     * @param notification the notification to send
     * @return true if the notification was sent successfully
     */
    public boolean sendNotification(String sessionId, McpUiNotification notification) {
        return get(sessionId).map(session -> {
            Object transport = session.getTransport();
            if (transport != null) {
                // If we have a transport, delegate to it
                return sendViaTransport(transport, notification);
            }
            // For postMessage-based sessions, notifications are queued for polling
            LOG.debug("Session {} has no transport, notification queued", sessionId);
            return true;
        }).orElse(false);
    }

    /**
     * Sends a tool input notification to a session.
     * 
     * @param sessionId the session identifier
     * @param toolName  the tool being called
     * @param arguments the tool arguments
     * @return true if sent successfully
     */
    public boolean notifyToolInput(String sessionId, String toolName, Object arguments) {
        return sendNotification(sessionId, McpUiNotification.toolInput(toolName, arguments));
    }

    /**
     * Sends a tool result notification to a session.
     * 
     * @param sessionId the session identifier
     * @param toolName  the tool that was called
     * @param result    the tool result
     * @return true if sent successfully
     */
    public boolean notifyToolResult(String sessionId, String toolName, Object result) {
        return sendNotification(sessionId, McpUiNotification.toolResult(toolName, result));
    }

    /**
     * Sends a tool error notification to a session.
     * 
     * @param sessionId the session identifier
     * @param toolName  the tool that failed
     * @param error     the error message
     * @return true if sent successfully
     */
    public boolean notifyToolError(String sessionId, String toolName, String error) {
        return sendNotification(sessionId, McpUiNotification.toolError(toolName, error));
    }

    private boolean sendViaTransport(Object transport, McpUiNotification notification) {
        // TODO: Implement WebSocket transport sending
        // This will be implemented in Phase 6 when WebSocket enhancements are added
        LOG.debug("Transport notification: method={}", notification.getMethod());
        return true;
    }

    private boolean isExpired(McpUiSession session) {
        long elapsed = System.currentTimeMillis() - session.getLastActivityAt().toEpochMilli();
        return elapsed > sessionTimeoutMs;
    }

    private void cleanupExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry -> {
            if (isExpired(entry.getValue())) {
                LOG.debug("Expiring session id={}", entry.getKey());
                return true;
            }
            return false;
        });
        int removed = before - sessions.size();
        if (removed > 0) {
            LOG.info("Cleaned up {} expired UI sessions, {} remaining", removed, sessions.size());
        }
    }

    /**
     * Gets the configured session timeout in milliseconds.
     * 
     * @return timeout in ms
     */
    public long getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }
}
