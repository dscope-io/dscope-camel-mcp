# Camel MCP Postman Project

This directory contains an import-ready Postman collection and environment for driving the sample MCP service exposed by this module.

## Files

- `Camel-MCP-Sample.postman_collection.json` — four JSON-RPC requests (`ping`, `resources/get`, `tools/list`, `tools/call`).
- `Camel-MCP-Sample.postman_environment.json` — defines `base_url` (`http://localhost:8080`) and `resource_name` (`example-resource`).

## Usage

1. Start the sample service: `mvn -f samples/mcp-service/pom.xml exec:java`.
2. In Postman, import the collection and environment from this folder.
3. Select the **Camel MCP Sample** environment and execute the requests. Adjust the `resource_name` environment variable to fetch other payloads.
