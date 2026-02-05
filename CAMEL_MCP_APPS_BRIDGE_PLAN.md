# Camel MCP Library - MCP Apps Bridge Implementation Plan

## Overview

Add **MCP Apps UI Bridge** support to `io.dscope:camel-mcp` to enable the library to act as a host for MCP Apps UIs, implementing the standard `ui/*` JSON-RPC methods defined in the MCP Apps specification.

**Reference Specifications:**
- https://developers.openai.com/apps-sdk/mcp-apps-in-chatgpt
- https://modelcontextprotocol.io/docs/extensions/apps
- https://github.com/modelcontextprotocol/ext-apps/blob/main/specification/draft/apps.mdx

---

## Current State

### Already Implemented ✅

| Feature | Method | Status |
|---------|--------|--------|
| Tool metadata with UI reference | `tools/list` with `_meta.ui.resourceUri` | ✅ |
| Resource listing | `resources/list` | ✅ |
| Resource reading (HTML) | `resources/read` | ✅ |
| Tool execution | `tools/call` | ✅ |
| WebSocket transport | `ws://host:port/mcp/ws` | ✅ |

### Missing for Full Host Bridge ❌

| Feature | Method | Purpose |
|---------|--------|---------|
| UI initialization | `ui/initialize` | Establish connection with embedded iframe |
| Tool input notification | `ui/notifications/tool-input` | Push tool input to app before execution |
| Tool result notification | `ui/notifications/tool-result` | Push tool results to app after execution |
| Send message | `ui/message` | App sends follow-up messages to host |
| Update model context | `ui/update-model-context` | App updates host's model context |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           MCP Apps Host Bridge                               │
│                         (New in camel-mcp 1.2.0)                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────┐    postMessage    ┌──────────────────────────────────┐│
│  │   MCP App UI     │◄─────────────────►│       McpAppsBridgeProcessor     ││
│  │   (iframe)       │    ui/* methods   │                                  ││
│  └──────────────────┘                   │  - Session management            ││
│                                         │  - Message routing               ││
│                                         │  - Tool call proxying            ││
│                                         └──────────────────────────────────┘│
│                                                      │                       │
│                                                      ▼                       │
│                                         ┌──────────────────────────────────┐│
│                                         │       Existing MCP Stack         ││
│                                         │  - McpToolsCallProcessor         ││
│                                         │  - McpResourcesReadProcessor     ││
│                                         └──────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Model Classes

### 1.1 McpUiSession

**File:** `io/dscope/camel/mcp/model/McpUiSession.java`

```java
package io.dscope.camel.mcp.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents an active MCP Apps UI session.
 */
public class McpUiSession {
    private String sessionId;
    private String resourceUri;      // ui://... resource this session displays
    private String toolName;         // Tool that triggered this UI
    private Instant createdAt;
    private Instant lastActivityAt;
    private Object transport;        // WebSocket session or postMessage channel
    
    public static McpUiSession create(String resourceUri, String toolName) {
        McpUiSession session = new McpUiSession();
        session.sessionId = UUID.randomUUID().toString();
        session.resourceUri = resourceUri;
        session.toolName = toolName;
        session.createdAt = Instant.now();
        session.lastActivityAt = Instant.now();
        return session;
    }
    
    // Getters, setters
}
```

### 1.2 McpUiInitializeParams

**File:** `io/dscope/camel/mcp/model/McpUiInitializeParams.java`

```java
package io.dscope.camel.mcp.model;

/**
 * Parameters for ui/initialize request from embedded app.
 */
public class McpUiInitializeParams {
    private String resourceUri;      // The UI resource being initialized
    private McpClientInfo clientInfo; // App's name/version
    
    // Nested class
    public static class McpClientInfo {
        private String name;
        private String version;
    }
}
```

### 1.3 McpUiInitializeResult

**File:** `io/dscope/camel/mcp/model/McpUiInitializeResult.java`

```java
package io.dscope.camel.mcp.model;

import java.util.List;

/**
 * Result of ui/initialize - tells app what capabilities host supports.
 */
public class McpUiInitializeResult {
    private String sessionId;
    private McpHostInfo hostInfo;
    private List<String> capabilities;  // e.g., ["tools/call", "ui/message"]
    
    public static class McpHostInfo {
        private String name;    // "camel-mcp"
        private String version; // "1.2.0"
    }
}
```

### 1.4 McpUiNotification

**File:** `io/dscope/camel/mcp/model/McpUiNotification.java`

```java
package io.dscope.camel.mcp.model;

/**
 * Notification sent from host to app.
 */
public class McpUiNotification {
    private String method;  // "ui/notifications/tool-input" or "ui/notifications/tool-result"
    private Object params;  // Tool input or result payload
    
    public static McpUiNotification toolInput(String toolName, Object arguments) {
        McpUiNotification n = new McpUiNotification();
        n.method = "ui/notifications/tool-input";
        n.params = new ToolInputParams(toolName, arguments);
        return n;
    }
    
    public static McpUiNotification toolResult(Object result) {
        McpUiNotification n = new McpUiNotification();
        n.method = "ui/notifications/tool-result";
        n.params = result;
        return n;
    }
}
```

---

## Phase 2: Session Management

### 2.1 McpUiSessionRegistry

**File:** `io/dscope/camel/mcp/service/McpUiSessionRegistry.java`

```java
package io.dscope.camel.mcp.service;

import io.dscope.camel.mcp.model.McpUiSession;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for active MCP Apps UI sessions.
 */
public class McpUiSessionRegistry {
    
    private final Map<String, McpUiSession> sessions = new ConcurrentHashMap<>();
    private final long sessionTimeoutMs;
    
    public McpUiSessionRegistry(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }
    
    public McpUiSession register(String resourceUri, String toolName) {
        McpUiSession session = McpUiSession.create(resourceUri, toolName);
        sessions.put(session.getSessionId(), session);
        return session;
    }
    
    public Optional<McpUiSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId))
            .filter(this::isNotExpired);
    }
    
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }
    
    public void sendNotification(String sessionId, McpUiNotification notification) {
        get(sessionId).ifPresent(session -> {
            // Send via WebSocket or store for postMessage polling
        });
    }
    
    private boolean isNotExpired(McpUiSession session) {
        return System.currentTimeMillis() - session.getLastActivityAt().toEpochMilli() < sessionTimeoutMs;
    }
}
```

---

## Phase 3: Processors

### 3.1 McpUiInitializeProcessor

**File:** `io/dscope/camel/mcp/processor/McpUiInitializeProcessor.java`

```java
package io.dscope.camel.mcp.processor;

import io.dscope.camel.mcp.model.*;
import io.dscope.camel.mcp.service.McpUiSessionRegistry;
import org.apache.camel.Exchange;

/**
 * Handles ui/initialize requests from embedded MCP Apps.
 */
public class McpUiInitializeProcessor extends AbstractMcpResponseProcessor {
    
    private final McpUiSessionRegistry sessionRegistry;
    
    @Override
    public void process(Exchange exchange) throws Exception {
        McpUiInitializeParams params = getParams(exchange, McpUiInitializeParams.class);
        
        // Create session
        McpUiSession session = sessionRegistry.register(
            params.getResourceUri(),
            null  // toolName set later when tool is called
        );
        
        // Build response
        McpUiInitializeResult result = new McpUiInitializeResult();
        result.setSessionId(session.getSessionId());
        result.setHostInfo(new McpHostInfo("camel-mcp", "1.2.0"));
        result.setCapabilities(List.of(
            "tools/call",
            "ui/message",
            "ui/notifications/tool-input",
            "ui/notifications/tool-result"
        ));
        
        setResult(exchange, result);
    }
}
```

### 3.2 McpUiToolsCallProcessor

**File:** `io/dscope/camel/mcp/processor/McpUiToolsCallProcessor.java`

```java
package io.dscope.camel.mcp.processor;

import io.dscope.camel.mcp.service.McpUiSessionRegistry;

/**
 * Handles tools/call requests FROM embedded MCP Apps.
 * Proxies to the actual tool implementation and returns result.
 */
public class McpUiToolsCallProcessor extends AbstractMcpResponseProcessor {
    
    private final McpUiSessionRegistry sessionRegistry;
    private final McpToolsCallProcessor toolsCallProcessor;
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String sessionId = getHeader(exchange, "X-MCP-Session-Id");
        
        // Validate session
        McpUiSession session = sessionRegistry.get(sessionId)
            .orElseThrow(() -> new McpException(-32001, "Invalid session"));
        
        // Update activity timestamp
        session.touch();
        
        // Delegate to existing tools/call processor
        toolsCallProcessor.process(exchange);
    }
}
```

### 3.3 McpUiMessageProcessor

**File:** `io/dscope/camel/mcp/processor/McpUiMessageProcessor.java`

```java
package io.dscope.camel.mcp.processor;

/**
 * Handles ui/message requests - app sending follow-up messages.
 */
public class McpUiMessageProcessor extends AbstractMcpResponseProcessor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String sessionId = getHeader(exchange, "X-MCP-Session-Id");
        String message = getParams(exchange, UiMessageParams.class).getMessage();
        
        // Log or store message for host consumption
        log.info("UI Message from session {}: {}", sessionId, message);
        
        // Acknowledge
        setResult(exchange, Map.of("acknowledged", true));
    }
}
```

### 3.4 McpUiUpdateModelContextProcessor

**File:** `io/dscope/camel/mcp/processor/McpUiUpdateModelContextProcessor.java`

```java
package io.dscope.camel.mcp.processor;

/**
 * Handles ui/update-model-context - app updating host's model context.
 */
public class McpUiUpdateModelContextProcessor extends AbstractMcpResponseProcessor {
    
    @Override
    public void process(Exchange exchange) throws Exception {
        String sessionId = getHeader(exchange, "X-MCP-Session-Id");
        Object context = getParams(exchange, Object.class);
        
        // Store context update for host
        sessionRegistry.get(sessionId).ifPresent(session -> {
            session.setModelContext(context);
        });
        
        setResult(exchange, Map.of("updated", true));
    }
}
```

---

## Phase 4: Route Integration

### 4.1 Updated mcp.camel.yaml

Add `ui/*` method routing:

```yaml
# MCP Apps Bridge Routes
- route:
    id: mcp-ui-initialize
    from:
      uri: direct:mcp-ui-initialize
    steps:
      - process:
          ref: mcpUiInitializeProcessor

- route:
    id: mcp-ui-message
    from:
      uri: direct:mcp-ui-message
    steps:
      - process:
          ref: mcpUiMessageProcessor

- route:
    id: mcp-ui-update-model-context
    from:
      uri: direct:mcp-ui-update-model-context
    steps:
      - process:
          ref: mcpUiUpdateModelContextProcessor
```

### 4.2 Method Dispatcher Update

Update `McpMethodDispatcherProcessor` to route `ui/*` methods:

```java
switch (method) {
    case "ui/initialize":
        return "direct:mcp-ui-initialize";
    case "ui/message":
        return "direct:mcp-ui-message";
    case "ui/update-model-context":
        return "direct:mcp-ui-update-model-context";
    case "tools/call":
        // Check if from UI session - use proxied version
        if (hasUiSession(exchange)) {
            return "direct:mcp-ui-tools-call";
        }
        return "direct:mcp-tools-call";
    // ... existing cases
}
```

---

## Phase 5: Configuration

### 5.1 application.yaml

```yaml
camel:
  mcp:
    apps:
      enabled: true
      session-timeout: 3600000  # 1 hour in ms
      capabilities:
        - tools/call
        - ui/message
        - ui/notifications/tool-input
        - ui/notifications/tool-result
```

### 5.2 Auto-Configuration

**File:** `io/dscope/camel/mcp/config/McpAppsAutoConfiguration.java`

```java
@Configuration
@ConditionalOnProperty(prefix = "camel.mcp.apps", name = "enabled", havingValue = "true")
public class McpAppsAutoConfiguration {
    
    @Bean
    public McpUiSessionRegistry mcpUiSessionRegistry(
            @Value("${camel.mcp.apps.session-timeout:3600000}") long timeout) {
        return new McpUiSessionRegistry(timeout);
    }
    
    @Bean
    public McpUiInitializeProcessor mcpUiInitializeProcessor(McpUiSessionRegistry registry) {
        return new McpUiInitializeProcessor(registry);
    }
    
    @Bean
    public McpUiMessageProcessor mcpUiMessageProcessor(McpUiSessionRegistry registry) {
        return new McpUiMessageProcessor(registry);
    }
    
    // ... other beans
}
```

---

## Phase 6: WebSocket Enhancements

### 6.1 Bidirectional Notifications

For pushing notifications to embedded apps via WebSocket:

```java
public class McpWebSocketNotifier {
    
    private final McpUiSessionRegistry sessionRegistry;
    
    /**
     * Push tool result to connected UI session.
     */
    public void notifyToolResult(String sessionId, Object result) {
        sessionRegistry.get(sessionId).ifPresent(session -> {
            WebSocketSession ws = (WebSocketSession) session.getTransport();
            if (ws != null && ws.isOpen()) {
                McpUiNotification notification = McpUiNotification.toolResult(result);
                ws.sendMessage(new TextMessage(toJson(notification)));
            }
        });
    }
    
    /**
     * Push tool input to connected UI session (streaming).
     */
    public void notifyToolInput(String sessionId, String toolName, Object arguments) {
        sessionRegistry.get(sessionId).ifPresent(session -> {
            McpUiNotification notification = McpUiNotification.toolInput(toolName, arguments);
            // Send via WebSocket
        });
    }
}
```

---

## Implementation Phases

| Phase | Scope | Effort | Priority |
|-------|-------|--------|----------|
| 1 | Model classes | 2 days | High |
| 2 | Session registry | 2 days | High |
| 3 | Core processors (initialize, message) | 3 days | High |
| 4 | Route integration | 2 days | High |
| 5 | Configuration | 1 day | Medium |
| 6 | WebSocket notifications | 3 days | Medium |
| **Total** | | **13 days** | |

---

## Testing Plan

### Unit Tests

```java
class McpUiInitializeProcessorTest {
    @Test
    void shouldCreateSessionOnInitialize() { }
    
    @Test
    void shouldReturnCapabilities() { }
}

class McpUiSessionRegistryTest {
    @Test
    void shouldExpireSessions() { }
    
    @Test
    void shouldTrackMultipleSessions() { }
}
```

### Integration Tests

```java
class McpAppsBridgeIntegrationTest {
    @Test
    void shouldHandleFullUiLifecycle() {
        // 1. ui/initialize
        // 2. tools/call from UI
        // 3. Receive tool result notification
        // 4. ui/message
        // 5. Session cleanup
    }
}
```

---

## Client SDK (Optional)

Provide a Java client for embedded apps:

```java
// For Java-based MCP Apps (rare, but possible)
public class McpAppsClient {
    
    public void connect(String hostUrl) { }
    
    public CompletableFuture<Object> callTool(String name, Object args) { }
    
    public void sendMessage(String message) { }
    
    public void onToolResult(Consumer<Object> handler) { }
}
```

---

## Compatibility Matrix

| Host | Transport | Status |
|------|-----------|--------|
| ChatGPT | postMessage (iframe) | Supported after implementation |
| Claude | postMessage (iframe) | Supported after implementation |
| VS Code Insiders | postMessage (webview) | Supported after implementation |
| Direct HTTP | REST | Already supported |
| WebSocket | WS | Already supported |

---

## Summary

The `io.dscope:camel-mcp` library needs these additions to become a full MCP Apps host:

1. **Session management** for tracking connected UI instances
2. **`ui/initialize` handler** to establish connections
3. **`ui/message` handler** for app-to-host messaging
4. **`ui/update-model-context` handler** for context updates
5. **Notification push** via WebSocket for `ui/notifications/*`

The existing `tools/list`, `resources/list`, `resources/read`, and `tools/call` implementations are already MCP Apps compliant for server mode.
