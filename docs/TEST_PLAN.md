# MCP Service Test Plan

This document provides a comprehensive test plan for validating the `camel-mcp` library features using the sample service.

## Prerequisites

### Environment Setup
1. **Java 21+**: Required for running the sample service
2. **Maven 3.9+**: For building and running
3. **HTTP Client**: curl, httpie, or Postman
4. **WebSocket Client**: websocat, wscat, or Postman WebSocket

### Build & Start
```bash
# Build the main library
cd /path/to/CamelMcpComponent
mvn clean install

# Start the sample service
cd samples/mcp-service
mvn exec:java

# Alternative: run with specific route
mvn exec:java -Dcamel.main.routesIncludePattern=classpath:routes/mcp-ws-service.camel.yaml
```

---

## Test Categories

### 1. Core MCP Protocol Tests

#### 1.1 Initialize
**Endpoint**: `POST http://localhost:8080/mcp`

**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "init-1",
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": {
      "name": "test-client",
      "version": "1.0.0"
    }
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "init-1",
  "result": {
    "protocolVersion": "2025-06-18",
    "serverInfo": {
      "name": "camel-mcp",
      "version": "1.2.0"
    },
    "capabilities": {
      "tools": { "listChanged": true },
      "resources": { "subscribe": true, "listChanged": true },
      "prompts": { "listChanged": true },
      "logging": {},
      "ui": {
        "tools/call": true,
        "message": true,
        "updateModelContext": true,
        "notifications/toolInput": true,
        "notifications/toolResult": true
      }
    }
  }
}
```

**Validation**:
- [ ] Response returns 200 OK
- [ ] `protocolVersion` matches request
- [ ] `serverInfo` contains valid name and version
- [ ] `capabilities.ui` section present (MCP Apps Bridge)

---

#### 1.2 Ping
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "ping-1",
  "method": "ping"
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ping-1",
  "result": {}
}
```

**Validation**:
- [ ] Response is immediate (< 100ms)
- [ ] Empty result object returned

---

#### 1.3 Tools List
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "tools-1",
  "method": "tools/list"
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "tools-1",
  "result": {
    "tools": [
      {
        "name": "echo",
        "description": "Returns the provided text as MCP content.",
        "inputSchema": { "type": "object", "properties": { "text": { "type": "string" } } }
      },
      {
        "name": "summarize",
        "description": "Generates a short summary of the supplied text payload."
      },
      {
        "name": "chart-editor",
        "description": "Interactive chart editor with embedded UI for configuring data visualization.",
        "annotations": { "ui": { "outputUri": "mcp://resource/chart-editor.html" } }
      }
    ]
  }
}
```

**Validation**:
- [ ] All three tools listed: echo, summarize, chart-editor
- [ ] chart-editor has UI annotations

---

#### 1.4 Tools Call - Echo
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "method": "tools/call",
  "params": {
    "name": "echo",
    "arguments": {
      "text": "Hello, MCP!"
    }
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "call-1",
  "result": {
    "content": [
      { "type": "text", "text": "Hello, MCP!" }
    ]
  }
}
```

**Validation**:
- [ ] Text echoed back unchanged
- [ ] Content type is "text"

---

#### 1.5 Tools Call - Summarize
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "call-2",
  "method": "tools/call",
  "params": {
    "name": "summarize",
    "arguments": {
      "text": "The quick brown fox jumps over the lazy dog. This is a sample text that should be truncated.",
      "maxWords": 5
    }
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "call-2",
  "result": {
    "content": [
      { "type": "text", "text": "The quick brown fox jumps …" }
    ]
  }
}
```

**Validation**:
- [ ] Text truncated to maxWords
- [ ] Ellipsis appended

---

#### 1.6 Resources List
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "res-1",
  "method": "resources/list"
}
```

**Validation**:
- [ ] Returns list of available resources
- [ ] chart-editor.html resource present

---

#### 1.7 Resources Get
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "res-2",
  "method": "resources/get",
  "params": {
    "uri": "resource://data/chart-editor.html"
  }
}
```

**Validation**:
- [ ] Returns HTML content
- [ ] mimeType is text/html

---

### 2. MCP Apps Bridge Tests (ui/* methods)

#### 2.1 UI Initialize
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-init-1",
  "method": "ui/initialize",
  "params": {
    "clientInfo": {
      "name": "test-ui-client",
      "version": "1.0.0"
    },
    "resourceUri": "mcp://resource/chart-editor.html",
    "toolName": "chart-editor"
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-init-1",
  "result": {
    "sessionId": "<UUID>",
    "hostInfo": {
      "name": "camel-mcp-host",
      "version": "1.2.0"
    },
    "capabilities": {
      "tools/call": true,
      "ui/message": true,
      "ui/update-model-context": true,
      "ui/notifications/tool-input": true,
      "ui/notifications/tool-result": true
    }
  }
}
```

**Validation**:
- [ ] `sessionId` is valid UUID
- [ ] `hostInfo` populated
- [ ] All UI capabilities present
- [ ] Save sessionId for subsequent tests

---

#### 2.2 UI Message
**Prerequisite**: Use sessionId from ui/initialize

**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-msg-1",
  "method": "ui/message",
  "params": {
    "sessionId": "<sessionId-from-init>",
    "type": "user-action",
    "payload": {
      "action": "chart-type-changed",
      "newValue": "line"
    }
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-msg-1",
  "result": {
    "acknowledged": true
  }
}
```

**Validation**:
- [ ] Message acknowledged
- [ ] No error for valid session

---

#### 2.3 UI Update Model Context
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-ctx-1",
  "method": "ui/update-model-context",
  "params": {
    "sessionId": "<sessionId-from-init>",
    "context": {
      "chartConfig": {
        "type": "bar",
        "data": [10, 20, 30]
      }
    },
    "mode": "merge"
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-ctx-1",
  "result": {
    "acknowledged": true,
    "mode": "merge"
  }
}
```

**Validation**:
- [ ] Context update acknowledged
- [ ] Mode reflected in response

---

#### 2.4 UI Tools Call
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-call-1",
  "method": "ui/tools/call",
  "params": {
    "sessionId": "<sessionId-from-init>",
    "name": "echo",
    "arguments": {
      "text": "Called from UI"
    }
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-call-1",
  "result": {
    "content": [
      { "type": "text", "text": "Called from UI" }
    ]
  }
}
```

**Validation**:
- [ ] Tool executed successfully
- [ ] Session validated before execution

---

#### 2.5 Invalid Session Handling
**Request** (invalid sessionId):
```json
{
  "jsonrpc": "2.0",
  "id": "ui-invalid-1",
  "method": "ui/message",
  "params": {
    "sessionId": "00000000-0000-0000-0000-000000000000",
    "type": "test",
    "payload": {}
  }
}
```

**Expected Response**:
```json
{
  "jsonrpc": "2.0",
  "id": "ui-invalid-1",
  "error": {
    "code": -32600,
    "message": "Invalid or expired UI session"
  }
}
```

**Validation**:
- [ ] Error returned for invalid session
- [ ] Appropriate error code

---

### 3. WebSocket Tests

**Endpoint**: `ws://localhost:8090/mcp`

#### 3.1 WebSocket Connection
1. Connect to `ws://localhost:8090/mcp`
2. Send initialize request
3. Verify response

**Validation**:
- [ ] Connection established
- [ ] Messages sent and received correctly

#### 3.2 WebSocket UI Session
1. Send ui/initialize via WebSocket
2. Send ui/message with sessionId
3. Verify session maintained

**Validation**:
- [ ] Session persists across messages
- [ ] Notifications can be received

#### 3.3 Notification Flow
1. Initialize UI session
2. Call ui/tools/call
3. Monitor for tool-input and tool-result notifications

---

### 4. Error Handling Tests

#### 4.1 Invalid Method
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "err-1",
  "method": "invalid/method"
}
```

**Expected Error**: `-32601 Method not found`

#### 4.2 Invalid JSON
**Request**: `{invalid json}`

**Expected Error**: `-32700 Parse error`

#### 4.3 Missing Required Parameters
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "err-2",
  "method": "tools/call",
  "params": {}
}
```

**Expected Error**: `-32602 Invalid params` (missing tool name)

#### 4.4 Unknown Tool
**Request**:
```json
{
  "jsonrpc": "2.0",
  "id": "err-3",
  "method": "tools/call",
  "params": {
    "name": "nonexistent-tool",
    "arguments": {}
  }
}
```

**Expected Error**: `-32603 Internal error` (Unknown tool)

---

### 5. Rate Limiting Tests

#### 5.1 Normal Load
- Send 10 requests per second
- All should succeed

#### 5.2 Burst Load
- Send 100 requests rapidly
- Some may be rate-limited (429 response)

---

### 6. Health & Monitoring

#### 6.1 Health Endpoint
**Request**: `GET http://localhost:8080/mcp/health`

**Expected Response**:
```json
{
  "status": "UP",
  "timestamp": "...",
  "version": "1.2.0"
}
```

---

## Postman Collection

Pre-built Postman collections are available in:
- `samples/mcp-service/postman/Camel-MCP-Sample.postman_collection.json`
- `samples/mcp-service/postman/Camel-MCP-WebSocket.postman_collection.json`

Import these for easy testing.

---

## Automated Test Commands (curl)

### Initialize and get tools
```bash
# Initialize
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{"protocolVersion":"2025-06-18","clientInfo":{"name":"curl","version":"1.0"}}}'

# List tools
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"2","method":"tools/list"}'

# Call echo tool
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"3","method":"tools/call","params":{"name":"echo","arguments":{"text":"Hello!"}}}'
```

### UI Session Flow
```bash
# Initialize UI session
UI_RESP=$(curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"ui1","method":"ui/initialize","params":{"clientInfo":{"name":"test"},"resourceUri":"mcp://chart","toolName":"chart-editor"}}')

echo "$UI_RESP"

# Extract sessionId (requires jq)
SESSION_ID=$(echo "$UI_RESP" | jq -r '.result.sessionId')

# Send UI message
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":\"ui2\",\"method\":\"ui/message\",\"params\":{\"sessionId\":\"$SESSION_ID\",\"type\":\"test\",\"payload\":{}}}"

# Update model context
curl -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -d "{\"jsonrpc\":\"2.0\",\"id\":\"ui3\",\"method\":\"ui/update-model-context\",\"params\":{\"sessionId\":\"$SESSION_ID\",\"context\":{\"key\":\"value\"},\"mode\":\"merge\"}}"
```

---

## Test Results Template

| Test Case | Status | Notes |
|-----------|--------|-------|
| 1.1 Initialize | ✅ | Returns protocolVersion, serverInfo, capabilities including ui |
| 1.2 Ping | ✅ | Returns empty result |
| 1.3 Tools List | ✅ | Returns echo, summarize, chart-editor with UI annotations |
| 1.4 Tools Call - Echo | ✅ | Returns text content |
| 1.5 Tools Call - Summarize | ✅ | Truncates and adds ellipsis |
| 1.6 Resources List | ✅ | Returns chart-editor.html and other resources |
| 1.7 Resources Get | ⬜ | |
| 2.1 UI Initialize | ✅ | Returns sessionId, hostInfo, capabilities |
| 2.2 UI Message | ✅ | Returns acknowledged: true |
| 2.3 UI Update Model Context | ✅ | Returns acknowledged: true with mode |
| 2.4 UI Tools Call | ✅ | Fixed in v1.2.0 - returns tool result correctly |
| 2.5 Invalid Session | ⬜ | |
| 3.1 WebSocket Connection | ⬜ | |
| 3.2 WebSocket UI Session | ⬜ | |
| 3.3 Notification Flow | ⬜ | |
| 4.1 Invalid Method | ⬜ | |
| 4.2 Invalid JSON | ⬜ | |
| 4.3 Missing Parameters | ⬜ | |
| 4.4 Unknown Tool | ⬜ | |
| 5.1 Normal Load | ⬜ | |
| 5.2 Burst Load | ⬜ | |
| 6.1 Health Endpoint | ⬜ | |

Legend: ✅ Pass | ❌ Fail | ⬜ Not Tested

---

## Changelog

### v1.2.0 (2025-01-xx)
- **Bug Fix**: Fixed `ui/tools/call` returning empty text response
  - **Root Cause**: `McpJsonRpcEnvelopeProcessor.handleUiToolsCall()` was setting property `"mcp.uiSessionId"` but processors looked for `"mcp.ui.sessionId"` (via `EXCHANGE_PROPERTY_UI_SESSION_ID` constant)
  - **Solution**: Updated to use the constant `McpUiInitializeProcessor.EXCHANGE_PROPERTY_UI_SESSION_ID` consistently
- Added chart-editor tool with embedded UI support
- Added UI annotations to tools in methods.yaml

---

## Troubleshooting

### Common Issues

1. **Port already in use**
   ```bash
   lsof -i :8080
   kill -9 <PID>
   ```

2. **Build failures**
   ```bash
   mvn clean install -DskipTests
   ```

3. **Session expired**
   - Default session timeout is 30 minutes
   - Re-initialize UI session if expired

4. **WebSocket connection refused**
   - Ensure WS route is loaded (port 8090)
   - Check firewall settings

---

## Consumer Component Tests

The MCP Consumer allows creating MCP servers programmatically through Camel routes. These tests validate the consumer functionality.

### 7.1 HTTP Consumer Basic Operation

**Test**: `McpConsumerTest.testHttpConsumerStartsAndResponds`

```java
from("mcp:http://localhost:9876/test")
    .process(exchange -> {
        Map<String, Object> response = Map.of(
            "jsonrpc", "2.0",
            "id", exchange.getProperty("mcp.jsonrpc.id"),
            "result", Map.of("echo", "pong")
        );
        exchange.getMessage().setBody(response);
    });
```

**Request**:
```bash
curl -X POST http://localhost:9876/test \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"test-1","method":"ping"}'
```

**Validation**:
- [ ] Consumer starts and binds to port
- [ ] Request is received and processed
- [ ] Response contains JSON-RPC 2.0 envelope
- [ ] Consumer stops cleanly on shutdown

---

### 7.2 WebSocket Consumer Configuration

**Test**: `McpConsumerTest.testWebSocketConsumerConfiguration`

```java
from("mcp:http://localhost:9877/ws?websocket=true")
    .process(exchange -> {
        Map<String, Object> response = Map.of(
            "jsonrpc", "2.0",
            "result", Map.of("status", "ok")
        );
        exchange.getMessage().setBody(response);
    });
```

**Validation**:
- [ ] WebSocket consumer starts without errors
- [ ] Route context is active
- [ ] Can accept WebSocket connections

---

### 7.3 JSON-RPC Envelope Parsing

**Test**: `McpConsumerTest.testConsumerWithJsonRpcParsing`

Validates that the consumer:
- Extracts `method` from JSON-RPC request
- Sets exchange property `mcp.jsonrpc.method`
- Makes it available to user processor

**Validation**:
- [ ] Method extracted: `tools/list`
- [ ] Exchange property set correctly
- [ ] User processor can access the method

---

### 7.4 Consumer Lifecycle Management

**Test**: `McpConsumerTest.testConsumerStopsCleanly`

**Validation**:
- [ ] Consumer starts successfully
- [ ] Consumer stops without exceptions
- [ ] No resource leaks (ports, connections)
- [ ] Undertow server shuts down properly

---

## Integration Test Scenarios

### 8.1 End-to-End Consumer Flow

**Setup**:
1. Start consumer route with custom processor
2. Send MCP initialize request
3. Send tools/list request
4. Send tools/call request
5. Verify all responses

**Expected Results**:
- All requests processed successfully
- Responses match MCP specification
- Exchange properties correctly populated

---

### 8.2 Consumer Error Handling

**Test missing headers**:
```bash
curl -X POST http://localhost:9876/test \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"1","method":"ping"}'
```

**Expected**: HTTP 400 - Missing Accept header

**Test invalid JSON**:
```bash
curl -X POST http://localhost:9876/test \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{invalid json}'
```

**Expected**: HTTP 400 - Parse error

---

### 8.3 Consumer Rate Limiting

Send 100+ rapid requests to consumer endpoint.

**Validation**:
- [ ] Rate limit processor invoked
- [ ] Appropriate throttling applied
- [ ] Error responses for rate-limited requests

---

## Test Automation

All consumer tests are automated in:
- `src/test/java/io/dscope/camel/mcp/McpConsumerTest.java`

Run consumer tests:
```bash
mvn test -Dtest=McpConsumerTest
```

Run all tests including consumer:
```bash
mvn test
```

Current status: **86 tests passing** (including 4 consumer tests)
