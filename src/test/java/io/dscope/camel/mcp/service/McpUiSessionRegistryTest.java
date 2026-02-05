package io.dscope.camel.mcp.service;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.model.McpUiSession;

import static org.junit.jupiter.api.Assertions.*;

class McpUiSessionRegistryTest {

    private McpUiSessionRegistry registry;

    @BeforeEach
    void setUp() {
        // Use a short timeout for testing
        registry = new McpUiSessionRegistry(1000L); // 1 second
    }

    @AfterEach
    void tearDown() {
        if (registry != null) {
            registry.stop();
        }
    }

    @Test
    void shouldRegisterSession() {
        McpUiSession session = registry.register("ui://test.com/app");
        
        assertNotNull(session);
        assertNotNull(session.getSessionId());
        assertEquals("ui://test.com/app", session.getResourceUri());
        
        assertTrue(registry.exists(session.getSessionId()));
        assertEquals(1, registry.size());
    }

    @Test
    void shouldRegisterSessionWithToolName() {
        McpUiSession session = registry.register("ui://test.com/app", "search");
        
        assertNotNull(session);
        assertEquals("search", session.getToolName());
    }

    @Test
    void shouldRetrieveSession() {
        McpUiSession session = registry.register("ui://test.com/app");
        
        Optional<McpUiSession> retrieved = registry.get(session.getSessionId());
        
        assertTrue(retrieved.isPresent());
        assertEquals(session.getSessionId(), retrieved.get().getSessionId());
    }

    @Test
    void shouldReturnEmptyForNonExistentSession() {
        Optional<McpUiSession> retrieved = registry.get("non-existent");
        
        assertFalse(retrieved.isPresent());
    }

    @Test
    void shouldReturnEmptyForNullSessionId() {
        Optional<McpUiSession> retrieved = registry.get(null);
        
        assertFalse(retrieved.isPresent());
    }

    @Test
    void shouldRemoveSession() {
        McpUiSession session = registry.register("ui://test.com/app");
        String sessionId = session.getSessionId();
        
        assertTrue(registry.exists(sessionId));
        
        boolean removed = registry.remove(sessionId);
        
        assertTrue(removed);
        assertFalse(registry.exists(sessionId));
    }

    @Test
    void shouldNotRemoveNonExistentSession() {
        boolean removed = registry.remove("non-existent");
        
        assertFalse(removed);
    }

    @Test
    void shouldTrackMultipleSessions() {
        McpUiSession session1 = registry.register("ui://test.com/app1");
        McpUiSession session2 = registry.register("ui://test.com/app2");
        McpUiSession session3 = registry.register("ui://test.com/app3");
        
        assertEquals(3, registry.size());
        assertTrue(registry.exists(session1.getSessionId()));
        assertTrue(registry.exists(session2.getSessionId()));
        assertTrue(registry.exists(session3.getSessionId()));
    }

    @Test
    void shouldTouchSessionOnGetAndTouch() {
        McpUiSession session = registry.register("ui://test.com/app");
        java.time.Instant initialActivity = session.getLastActivityAt();
        
        // Wait a bit
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        registry.getAndTouch(session.getSessionId());
        
        assertTrue(session.getLastActivityAt().isAfter(initialActivity));
    }

    @Test
    void shouldExpireSessions() throws InterruptedException {
        // Create a registry with 100ms timeout
        McpUiSessionRegistry shortRegistry = new McpUiSessionRegistry(100L);
        shortRegistry.start();
        
        try {
            McpUiSession session = shortRegistry.register("ui://test.com/app");
            String sessionId = session.getSessionId();
            
            // Session should exist initially
            assertTrue(shortRegistry.exists(sessionId));
            
            // Wait for expiration (session timeout + cleanup interval)
            Thread.sleep(200);
            
            // Session should be expired (get returns empty for expired)
            assertFalse(shortRegistry.get(sessionId).isPresent());
        } finally {
            shortRegistry.stop();
        }
    }

    @Test
    void shouldReturnDefaultTimeout() {
        McpUiSessionRegistry defaultRegistry = new McpUiSessionRegistry();
        assertEquals(McpUiSessionRegistry.DEFAULT_SESSION_TIMEOUT_MS, defaultRegistry.getSessionTimeoutMs());
    }
}
