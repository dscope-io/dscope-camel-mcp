package io.dscope.camel.mcp.model;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents an active MCP Apps UI session.
 * 
 * A session is created when an embedded MCP App calls ui/initialize
 * and tracks the lifecycle of that UI instance.
 */
public class McpUiSession {

    private final String sessionId;
    private final String resourceUri;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;
    private volatile String toolName;
    private volatile Object modelContext;
    private final AtomicReference<Object> transport = new AtomicReference<>();

    private McpUiSession(String sessionId, String resourceUri) {
        this.sessionId = sessionId;
        this.resourceUri = resourceUri;
        this.createdAt = Instant.now();
        this.lastActivityAt = Instant.now();
    }

    /**
     * Creates a new UI session for the given resource.
     * 
     * @param resourceUri the UI resource this session displays (e.g., ui://...)
     * @return a new session with a unique identifier
     */
    public static McpUiSession create(String resourceUri) {
        return new McpUiSession(UUID.randomUUID().toString(), resourceUri);
    }

    /**
     * Creates a new UI session with a specific tool name.
     * 
     * @param resourceUri the UI resource this session displays
     * @param toolName    the tool that triggered this UI
     * @return a new session with a unique identifier
     */
    public static McpUiSession create(String resourceUri, String toolName) {
        McpUiSession session = create(resourceUri);
        session.toolName = toolName;
        return session;
    }

    /**
     * Updates the last activity timestamp to the current time.
     */
    public void touch() {
        this.lastActivityAt = Instant.now();
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public String getToolName() {
        return toolName;
    }

    public void setToolName(String toolName) {
        this.toolName = toolName;
    }

    public Object getModelContext() {
        return modelContext;
    }

    public void setModelContext(Object modelContext) {
        this.modelContext = modelContext;
    }

    /**
     * Gets the transport channel (WebSocket session or similar).
     * 
     * @return the transport object, or null if not set
     */
    public Object getTransport() {
        return transport.get();
    }

    /**
     * Sets the transport channel for this session.
     * 
     * @param transport the transport object (e.g., WebSocket session)
     */
    public void setTransport(Object transport) {
        this.transport.set(transport);
    }

    /**
     * Checks if this session has an active transport.
     * 
     * @return true if transport is set
     */
    public boolean hasTransport() {
        return transport.get() != null;
    }
}
