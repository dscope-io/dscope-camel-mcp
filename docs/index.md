# 🧩 Apache Camel MCP Component

![Build](https://github.com/dscope-io/camel-mcp/actions/workflows/build.yml/badge.svg)
![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)
![Java](https://img.shields.io/badge/java-21+-green.svg)

The **Camel MCP Component** brings the [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) to [Apache Camel](https://camel.apache.org/). It provides both **producer** (client) and **consumer** (server) modes, enabling routes to call remote MCP servers or expose MCP endpoints directly.

## Highlights

- **Consumer mode**: `from("mcp:http://0.0.0.0:3000/mcp")` — built-in request validation, JSON-RPC parsing, rate limiting
- **Producer mode**: `to("mcp:http://host/mcp?method=tools/list")` — send MCP requests to remote servers
- 20+ built-in processors for tools, resources, UI Bridge, and notifications
- Apache Karavan visual designer integration
- HTTP and WebSocket transports

## Docs
- [Quickstart](quickstart.md) — Build, run samples, and test with curl
- [Development Guide](development.md) — Build your own MCP services with YAML, Java, or consumer URIs
- [Architecture](architecture.md) — Component model, processor pipeline, transport layer, and generated artifacts
- [Publish Guide](PUBLISH_GUIDE.md) — Release to Maven Central via GitHub Actions

## Samples
- **mcp-service** — Full-featured server using Kamelets/YAML routes (port 8080/8090)
- **mcp-consumer** — Minimal server using direct `from("mcp:...")` consumer (port 3000/3001)
