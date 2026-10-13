# MCP Events API (1.5.0 local build)

Contract: [OpenAI MCP Events](https://developers.openai.com/plugins/build/mcp-events),
retrieved 2026-10-12. Events require MCP protocol `2026-07-28`.

## Application routes (including platform-http)

Parse and validate requests with the existing HTTP validator and JSON-RPC envelope
processors. For discovery use `new McpServerDiscoverProcessor(true)`. For the three
Events methods use `new McpEventsProcessor(provider)`. These processors write
JSON-RPC envelopes, preserve request IDs and add modern result metadata. Discovery
defaults to events disabled. For a component Undertow consumer, registering exactly
one `McpEventsProvider` in the Camel registry enables discovery and automatic dispatch.

`io.dscope.camel.mcp.events.McpEventsProvider` defines:

```java
Map<String, Object> list(Exchange exchange, Map<String, Object> parameters) throws Exception;
Map<String, Object> subscribe(Exchange exchange, Map<String, Object> parameters) throws Exception;
void unsubscribe(Exchange exchange, Map<String, Object> parameters) throws Exception;
```

`list` returns `events` and optional `nextCursor`. Event definitions contain `name`,
`description`, `delivery: ["webhook"]`, `inputSchema` and `payloadSchema`.
Only expose authorized events. Subscribe/unsubscribe receive `name`, optional object
`arguments`, and `delivery` containing `mode: "webhook"`, `url`, and (subscribe only)
`secret`. The processor validates these shapes and the secret but the provider must
validate the event and arguments against its own catalog and authorize the principal
from the exchange **before** verification or persistence.

Subscribe returns `id`, `refreshBefore` (ISO timestamp or null), `cursor` (null if
replay is unsupported), and `truncated`. Requests preserve optional `cursor` and
`ttlMs`, including the distinction between omitted ttlMs (default lifetime) and
explicit null (request no expiration). Grant lifetimes according to the official
contract. Unsubscribe returns an empty result decorated with modern result metadata.
Invalid parameters raise `McpProtocolException` with code `-32602`. Callback exceptions
propagate for existing consumer error handling; application routes should serialize
`McpProtocolException.getCode()/getData()` in their JSON-RPC error handler.

## Webhook client

`io.dscope.camel.mcp.events.McpWebhookClient` provides:

```java
new McpWebhookClient();
void verify(String url, String subscriptionId, String secret);
int send(String url, String subscriptionId, String secret,
         String previousSecret, Map<String, Object> event);
```

Call `verify` after authorization and catalog validation, before activating a new
callback. It sends a signed fresh single-use random challenge and requires a 2xx
response echoing that challenge, compared using `MessageDigest.isEqual`. A request
is limited to ten seconds. The provider may cache success by authenticated principal
and URL for a bounded period; the client always generates a new challenge.

`send` serializes once and signs those exact UTF-8 bytes. The event must contain
non-empty `eventId` and `name`, an ISO timestamp with timezone, and object `data`;
it may include `cursor`. Application fields belong inside `data`; top-level `type`
is reserved for protocol control notifications. The provider validates name and data
against the subscription's payload schema. Pass null for `previousSecret` normally;
during a bounded rotation window it adds a second space-separated signature.

Secrets must be `whsec_` followed by standard base64 encoding 24–64 bytes. The
HMAC-SHA256 input is `eventId.timestamp.exactBody`; signatures are `v1,<base64>`.
Both verification and delivery include `Content-Type: application/json`,
`webhook-id`, `webhook-timestamp` (Unix seconds), `webhook-signature`, and
`X-MCP-Subscription-Id`.

Every attempt resolves the callback hostname, rejects any nonpublic answer
(including IPv6 and IPv4 embedded in IPv6), then connects using only that validated
address set while retaining the original hostname for TLS validation/SNI. The
default transport uses a fresh direct Apache HTTP client connection, prohibits
redirects, disables automatic retries and bounds request/response bodies at 256 KiB.
The overall ten-second deadline includes DNS, connection and response reading.

`send` returns the HTTP status; a 2xx response acknowledges receipt. The provider
owns bounded exponential retry and must preserve eventId while each call generates
a fresh signing timestamp/signature. Do not retry 410 or 413.

`McpCallbackException` extends `McpProtocolException`, with safe message
`CallbackEndpointError`, code `-32015`, `getReason()`, and `getData()` containing
`reason`: `invalid_url`, `dns_error`, `address_blocked`, `redirect`, `timeout`,
`transport_error`, `payload_too_large`, `http_error` (verification non-2xx), or
`challenge_failed`. Error messages never contain the destination, secret or response
body. Invalid signing keys and event structure raise `IllegalArgumentException`.

The injectable constructor is `McpWebhookClient(Transport, Resolver, Clock)`.
Transport's `post(URI, InetAddress[], Map<String,String>, byte[])` returns
`McpWebhookClient.Response(int status, byte[] body)`. Resolver's
`resolve(String hostname)` returns `InetAddress[]`. Test transports must honor
the supplied pinned addresses and never follow redirects. The production defaults
perform validation independently of the test transport.

The application owns canonical argument comparison and deterministic subscription
IDs from principal/URL/name/arguments, persistent owner/filter/secret/expiration
storage, idempotent refresh and unsubscribe, verification caching, revocation checks,
replay/cursors/truncation, payload schema validation, retry scheduling and secret
rotation windows. The component does not store subscriptions or authorize users.
