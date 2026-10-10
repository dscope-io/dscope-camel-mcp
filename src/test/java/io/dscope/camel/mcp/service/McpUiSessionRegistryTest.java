package io.dscope.camel.mcp.service;

import java.util.Optional;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.dscope.camel.mcp.model.McpUiSession;

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
        assertEquals(McpUiSessionRegistry.DEFAULT_CONTEXT_MAX_BYTES, defaultRegistry.getContextMaxBytes());
        assertEquals(McpUiSessionRegistry.DEFAULT_CONTEXT_MAX_ENTRIES, defaultRegistry.getContextMaxEntries());
    }

    @Test
    void shouldKeepConcurrentMergesWithinEntryLimit() throws Exception {
        McpUiSessionRegistry limitedRegistry = new McpUiSessionRegistry(60_000L, 1024, 1);
        McpUiSession session = limitedRegistry.register("ui://test.com/app");
        session.setModelContext(Map.of());
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> mergeAfter(start, session, "first"));
            Future<Boolean> second = executor.submit(() -> mergeAfter(start, session, "second"));
            start.countDown();

            int successfulUpdates = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, successfulUpdates);
            assertEquals(1, ((Map<?, ?>) session.getModelContext()).size());
        }
    }

    @Test
    void shouldApplyLimitsToDirectContextSetter() {
        McpUiSessionRegistry limitedRegistry = new McpUiSessionRegistry(60_000L, 16, 1);
        McpUiSession session = limitedRegistry.register("ui://test.com/app");

        assertThrows(McpUiSession.ContextValidationException.class,
                () -> session.setModelContext(Map.of("large", "x".repeat(32))));
        assertEquals(null, session.getModelContext());
    }

    @Test
    void shouldPreventNestedMutationAfterValidation() {
        McpUiSession session = registry.register("ui://test.com/app");
        session.setModelContext(Map.of("nested", Map.of("items", java.util.List.of("value"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) session.getModelContext();
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) context.get("nested");
        @SuppressWarnings("unchecked")
        java.util.List<Object> items = (java.util.List<Object>) nested.get("items");

        assertThrows(UnsupportedOperationException.class, () -> nested.put("bypass", "value"));
        assertThrows(UnsupportedOperationException.class, () -> items.add("bypass"));
    }

    private static boolean mergeAfter(CountDownLatch start, McpUiSession session, String key) throws Exception {
        start.await();
        try {
            session.updateModelContext(Map.of(key, "value"), true);
            return true;
        } catch (McpUiSession.ContextValidationException expected) {
            return false;
        }
    }
}
