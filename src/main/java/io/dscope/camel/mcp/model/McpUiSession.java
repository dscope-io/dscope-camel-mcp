package io.dscope.camel.mcp.model;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents an active MCP Apps UI session.
 * 
 * A session is created when an embedded MCP App calls ui/initialize
 * and tracks the lifecycle of that UI instance.
 */
public class McpUiSession {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final int DEFAULT_CONTEXT_MAX_BYTES = 256 * 1024;
    public static final int DEFAULT_CONTEXT_MAX_ENTRIES = 512;

    private final String sessionId;
    private final String resourceUri;
    private final Instant createdAt;
    private volatile Instant lastActivityAt;
    private volatile String toolName;
    private volatile Object modelContext;
    private final int contextMaxBytes;
    private final int contextMaxEntries;
    private final AtomicReference<Object> transport = new AtomicReference<>();

    private McpUiSession(String sessionId, String resourceUri, int contextMaxBytes, int contextMaxEntries) {
        this.sessionId = sessionId;
        this.resourceUri = resourceUri;
        this.contextMaxBytes = contextMaxBytes;
        this.contextMaxEntries = contextMaxEntries;
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
        return create(resourceUri, null,
                DEFAULT_CONTEXT_MAX_BYTES, DEFAULT_CONTEXT_MAX_ENTRIES);
    }

    /**
     * Creates a new UI session with a specific tool name.
     * 
     * @param resourceUri the UI resource this session displays
     * @param toolName    the tool that triggered this UI
     * @return a new session with a unique identifier
     */
    public static McpUiSession create(String resourceUri, String toolName) {
        return create(resourceUri, toolName,
                DEFAULT_CONTEXT_MAX_BYTES, DEFAULT_CONTEXT_MAX_ENTRIES);
    }

    /**
     * Creates a new UI session with explicit model-context limits.
     */
    public static McpUiSession create(String resourceUri, String toolName,
            int contextMaxBytes, int contextMaxEntries) {
        McpUiSession session = new McpUiSession(UUID.randomUUID().toString(), resourceUri,
                contextMaxBytes, contextMaxEntries);
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

    public synchronized void setModelContext(Object modelContext) {
        updateModelContext(modelContext, false);
    }

    public synchronized void updateModelContext(Object context, boolean merge) {
        Object candidate;
        if (merge && modelContext instanceof Map<?, ?> existing && context instanceof Map<?, ?> updates) {
            Map<String, Object> merged = new LinkedHashMap<>();
            existing.forEach((key, value) -> merged.put(String.valueOf(key), value));
            updates.forEach((key, value) -> merged.put(String.valueOf(key), value));
            candidate = merged;
        } else {
            candidate = context;
        }

        modelContext = normalizeAndValidate(candidate);
        touch();
    }

    private Object normalizeAndValidate(Object candidate) {
        try {
            byte[] serialized = OBJECT_MAPPER.writeValueAsBytes(candidate);
            if (serialized.length > contextMaxBytes) {
                throw new ContextValidationException(
                        "Model context exceeds the maximum serialized size of " + contextMaxBytes + " bytes");
            }
            Object normalized = OBJECT_MAPPER.readValue(serialized, Object.class);
            if (normalized instanceof Map<?, ?> map) {
                if (map.size() > contextMaxEntries) {
                    throw new ContextValidationException(
                            "Model context exceeds the maximum of " + contextMaxEntries + " top-level entries");
                }
            }
            return immutableJsonValue(normalized);
        } catch (IOException | IllegalArgumentException e) {
            if (e instanceof ContextValidationException validationException) {
                throw validationException;
            }
            throw new ContextValidationException("Model context must be JSON serializable", e);
        }
    }

    private Object immutableJsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> immutable = new LinkedHashMap<>();
            map.forEach((key, nested) -> immutable.put(String.valueOf(key), immutableJsonValue(nested)));
            return Collections.unmodifiableMap(immutable);
        }
        if (value instanceof List<?> list) {
            List<Object> immutable = new ArrayList<>(list.size());
            list.forEach(nested -> immutable.add(immutableJsonValue(nested)));
            return Collections.unmodifiableList(immutable);
        }
        return value;
    }

    public static final class ContextValidationException extends IllegalArgumentException {
        public ContextValidationException(String message) {
            super(message);
        }

        public ContextValidationException(String message, Throwable cause) {
            super(message, cause);
        }
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
