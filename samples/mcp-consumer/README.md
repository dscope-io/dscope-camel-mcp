# MCP Consumer Sample

A minimal MCP server built with the **Camel MCP Consumer component**.

Unlike the `mcp-service` sample (which uses Kamelets and YAML routes), this sample
demonstrates the **direct consumer approach** — a single `from("mcp:...")` route
with a plain Java processor to dispatch MCP methods.

## What it shows

| Feature | Detail |
|---------|--------|
| HTTP endpoint | `http://localhost:3000/mcp` |
| WebSocket endpoint | `ws://localhost:3001/mcp` |
| MCP methods | `initialize`, `ping`, `tools/list`, `tools/call`, `resources/list` |
| Tools | `echo`, `add`, `greet` |
| Notifications | Accepted with 204 (no-op) |

## Prerequisites

Build the root component first:

```bash
cd ../..
mvn clean install -DskipTests
```

## Run

```bash
cd samples/mcp-consumer
mvn compile exec:java
```

## Quick test

```bash
# Initialize
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"1","method":"initialize","params":{}}' | jq .

# Ping
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"2","method":"ping"}' | jq .

# List tools
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"3","method":"tools/list","params":{}}' | jq .

# Call echo tool
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"4","method":"tools/call","params":{"name":"echo","arguments":{"text":"hello"}}}' | jq .

# Call add tool
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"5","method":"tools/call","params":{"name":"add","arguments":{"a":17,"b":25}}}' | jq .

# Call greet tool
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"6","method":"tools/call","params":{"name":"greet","arguments":{"name":"Camel"}}}' | jq .

# List resources
curl -s -X POST http://localhost:3000/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":"7","method":"resources/list","params":{}}' | jq .
```

## Architecture

```
┌───────────────────────────────────────────────────────────────┐
│  from("mcp:http://0.0.0.0:3000/mcp")                        │
│                                                               │
│  Undertow HTTP ──► Size Guard ──► HTTP Validator ──►         │
│  Rate Limiter ──► JSON-RPC Envelope ──► Your Processor       │
│                                                               │
│  Exchange properties:                                         │
│    mcp.jsonrpc.method  = "tools/call"                        │
│    mcp.jsonrpc.id      = "4"                                  │
│    mcp.jsonrpc.type    = "REQUEST"                            │
│                                                               │
│  Your processor reads these and dispatches accordingly.       │
│  Response Map is auto-serialised to JSON.                     │
└───────────────────────────────────────────────────────────────┘
```
