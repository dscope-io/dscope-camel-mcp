# Code review — 2026-09-11

The highest priorities are stopping tool execution after failed session validation, restricting argument-to-header mapping, closing sample resource traversal, removing DOM injection, and applying the same request protections to every transport. The existing test suite passes, but does not exercise these failure paths.

Reviewed the current working tree, including the user's existing dependency/documentation edits. This is a review and change proposal; no implementation fixes were applied.

**Scope and evidence**

| Area | Coverage |
| --- | --- |
| Core library | Component, configuration, endpoint, consumer, producer, Java client and application bootstrap |
| Protocol processing | All request/response processors, HTTP checks, envelope handling, tools/resources, UI methods and remote proxy/gateway helpers |
| State and models | Session registry, WebSocket notifier, model classes, catalogs and Apps configuration |
| Resources and tooling | Both Kamelets, built-in HTML, generated component metadata, Karavan generator and OpenAPI builder |
| Samples | Service and consumer Java sources, route YAML, catalogs and browser resources; both sample POMs |
| Validation | `mvn test`: **96 tests, 0 failures, 0 errors**. Both samples' Java sources compiled with `javac` against the current built core and its Maven dependency classpath. Separate local probes exercised the defects listed below. |

The root POM is not a reactor containing the samples. The root test result therefore does not establish that either sample's Maven packaging or its Kamelet routes work end to end. Dependency review used the pinned versions and applicable Apache advisories, not a complete transitive vulnerability scan. Performance findings describe allocation, retention and blocking behavior found in code; no production throughput benchmark or destructive load test was run.

**Priority definitions:** P1 — fix before exposing affected functionality to untrusted clients or sustained traffic. P2 — fix in the next maintenance cycle; some security findings require a particular route or deployment. P3 — lower-impact tooling correction.

## Findings and proposed changes

### 1. [P1] Failed UI session validation does not stop tool execution

[McpUiToolsCallProcessor.java:53](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpUiToolsCallProcessor.java:53) and [mcp-rest-service.camel.yaml:105](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/src/main/resources/routes/mcp-rest-service.camel.yaml:105). Both Kamelets and sample HTTP/WS routes invoke the tool processor immediately after the UI validator. The validator writes an error and returns, which only exits that processor; the route continues. A local route using the same processor sequence, an invalid session and `echo` reached the business processor and returned **HTTP 200 with a result**, overwriting the 401.

**Change:** terminate routing on validation failure, or throw a typed rejection handled before business execution. Centralize this convention for all guard processors. Test missing, expired and invalid sessions with a side-effect counter and assert that it remains zero. A UI session should also be bound to the intended authenticated principal/resource when used as an access boundary; knowing a session ID alone is not authorization.

### 2. [P1] Tool arguments can become Camel control headers

[McpHeadersRequestProcessor.java:15](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpHeadersRequestProcessor.java:15) copies every argument key into exchange headers. A probe confirmed that `CamelHttpUri` and `CamelBeanMethodName` survive unchanged. When this helper precedes an HTTP or bean producer, client input can control transport destination or method selection. The impact depends on the downstream route; this helper is not used in the bundled sample dispatch path.

**Change:** allowlist explicit business headers or keep parameters in a namespaced exchange property. Reject reserved Camel, `org.apache.camel.*`, `websocket.*`, authorization and transport-routing headers case-insensitively. Add a route test proving arguments cannot redirect an HTTP call or select another bean method. A transport dependency upgrade alone does not fix headers introduced inside this processor.

### 3. [P1] Sample resource names escape the intended classpath directory

[SampleResourcesGetProcessor.java:67](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/src/main/java/io/dscope/camel/samples/mcp/SampleResourcesGetProcessor.java:67) concatenates `data/` with untrusted resource names. Requesting `../mcp/methods.yaml` successfully returned the sample method catalog with the exploded resources classpath. Other classpath configuration files could be exposed. This was verified for an exploded classpath; JAR classloader behavior may differ.

**Change:** resolve names through an explicit resource catalog/allowlist. If paths remain supported, normalize and require containment under the intended resource root, reject traversal segments and separators not required by the API, and test both packaged and exploded execution. The core `resources/read` catalog lookup already provides a better lookup pattern.

### 4. [P1] Unescaped tool metadata and chart labels reach HTML sinks

[mcp-app.html:325](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/resources/io/dscope/camel/mcp/ui/mcp-app.html:325) and [mcp-app.html:362](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/resources/io/dscope/camel/mcp/ui/mcp-app.html:362) interpolate tool names, descriptions, schema labels/defaults and enum values into `innerHTML`. A malicious tool descriptor can inject markup and event handlers. In [chart-editor.html:92](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/src/main/resources/data/chart-editor.html:92), any sender holding a window reference can supply initialization data without an origin/source check; labels then enter an HTML attribute at line 125. The eventual script privileges depend on the host's iframe sandbox and CSP.

**Change:** construct DOM nodes with `textContent`, `.value`, `.title` and safe attribute setters. Validate message source and origin against the established host, validate the message schema, and use a specific parent target origin where possible. Test hostile text and quote-containing labels in an actual browser. Separately, escape `<` in serialized configuration inserted into an inline script by [McpResourcesReadProcessor.java:138](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpResourcesReadProcessor.java:138) so a configuration string containing a script closing tag cannot terminate the script element.

### 5. [P1] REST Kamelet omits the advertised request guards

[mcp-rest-service.kamelet.yaml:127](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/resources/kamelets/mcp-rest-service.kamelet.yaml:127) parses JSON directly. The template declares size/rate guard beans but never invokes them; it also omits HTTP validation. Consumers switching from the sample route or `mcp:` component to this Kamelet lose these protections.

**Change:** use a shared ingress processor chain on all entry points: transport limits, origin/authentication checks, rate limiting, content checks and parsing. Add Kamelet integration tests for oversized bodies, exhausted rate limits and unacceptable media types.

### 6. [P1] Body-size enforcement happens after full body materialization

[McpRequestSizeGuardProcessor.java:46](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpRequestSizeGuardProcessor.java:46) converts the entire input to a String and then allocates UTF-8 bytes before comparing length. A counting-stream probe consumed **1,048,576 bytes** before the default 32 KiB limit rejected it. Already-buffered HTTP bodies incur this cost even earlier. Thus the guard bounds parsing input, not transport buffering or conversion memory.

**Change:** enforce HTTP entity and WebSocket message limits at Undertow, and use bounded reads for stream bodies (at most limit + 1). Check trustworthy size metadata early without relying on it alone. Return 413 for oversized HTTP requests. Test chunked input without Content-Length and assert the bounded reader stops early.

### 7. [P1] Origin validation is absent and WebSocket origins default to wildcard

[McpHttpValidatorProcessor.java:33](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpHttpValidatorProcessor.java:33) validates media types/version but never Origin. A probe with `Origin: https://untrusted.invalid` passed. [McpConfiguration.java:35](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpConfiguration.java:35) defaults allowed origins to `*`, and WS sample/Kamelet endpoints hardcode it. Samples bind all interfaces. This exposes unauthenticated local/network tools to cross-origin WebSocket access and can permit DNS-rebinding access to HTTP deployments lacking external protection.

**Change:** configure an explicit origin allowlist, reject invalid origins, default development listeners to loopback, and provide an authentication integration point for exposed services. Use separate handling for clients without an Origin header. MCP requires origin validation for Streamable HTTP: [transport specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports).

### 8. [P1] Anyone reaching the WS helper HTTP endpoint can broadcast to clients

[mcp-ws-service.camel.yaml:175](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/src/main/resources/routes/mcp-ws-service.camel.yaml:175) accepts arbitrary POST bodies and forwards them to all WebSocket clients. There is no authentication, payload validation, rate limit or size guard on this route. This is reachable when the WebSocket helper route is enabled; the service's default route selection is HTTP-only.

**Change:** remove the external notification ingress by default, or make it an authenticated administrative endpoint with a strict notification schema, body/rate limits and explicitly scoped recipients. Test that unauthorized callers cannot deliver any message.

### 9. [P1] Producer templates are created per call without being stopped

[McpProducer.java:87](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpProducer.java:87) and [McpProducer.java:110](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpProducer.java:110) create templates for every local/remote call. Each starts producer/cache lifecycle resources and neither branch closes them. Sustained usage causes resource churn and can retain lifecycle/cache resources for the lifetime of the Camel context.

**Change:** create one template per producer in `doStart`, reuse it, and stop it in `doStop`; or use Camel's managed producer cache. Verify that producer/template lifecycle counts remain bounded after repeated calls and context shutdown. Avoid logging an additional complete serialization of large payloads on the hot path.

### 10. [P1] Session storage can retain expired sessions indefinitely

[McpComponentApplicationSupport.java:51](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpComponentApplicationSupport.java:51) constructs a registry but its `run` path never calls `start` or `stop`. The registry is not itself a Camel lifecycle service. `get` ignores expired sessions without removing them, while [McpUiSessionRegistry.java:104](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/service/McpUiSessionRegistry.java:104) accepts unlimited new sessions. The default application path can accumulate expired sessions for its whole lifetime. Repeated context merges also allow one live session to grow beyond the per-request size limit.

**Change:** make the registry a managed Camel service, start/stop it with the context, remove expired entries on lookup, and cap sessions plus aggregate context bytes per session/principal. Make lifecycle restart safe: a start → stop → start probe currently throws `RejectedExecutionException` because the final executor was shut down. Use deterministic expiration tests and a bounded-session capacity test.

### 11. [P2] Pinned Camel version falls in two applicable Undertow advisory ranges

[pom.xml:11](/Users/roman/Projects/DScope/CamelMcpComponent/pom.xml:11), [pom.xml:12](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/pom.xml:12) and [pom.xml:17](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-consumer/pom.xml:17) pin Camel 4.15.0. Apache lists this version as affected by:

- **CVE-2026-78329 (medium):** Undertow-specific header filtering is bypassed by the endpoint's selected strategy. Relevant when untrusted inbound headers can reach WS dispatch. Fixed in 4.14.9, 4.18.4 and 4.22.0. [Apache advisory](https://camel.apache.org/security/CVE-2026-78329.html).
- **CVE-2026-56139 (medium):** unhandled Undertow route failures can disclose stack traces with the default `muteException=false`. Raw sample/Kamelet listeners have paths where non-IllegalArgumentException failures escape. The core consumer catches many exceptions, so exposure varies by route. Fixed in 4.14.8, 4.18.3 and 4.21.0. [Apache advisory](https://camel.apache.org/security/CVE-2026-56139.html).

**Change:** align all modules on a supported patched stream, with **4.18.4 or later in that stream** as a minimum candidate addressing both named advisories. Verify release compatibility and the complete resolved dependency tree before selecting the final version. Configure `muteException=true` and strip untrusted WS dispatch headers at ingress as immediate mitigations. Add dependency scanning/SBOM generation to CI; these two findings are not an exhaustive CVE inventory.

### 12. [P2] Default UI processors each allocate an independent registry

[McpUiMessageProcessor.java:29](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpUiMessageProcessor.java:29) and the other UI no-arg constructors each create a registry. Both Kamelet bean declarations instantiate these classes without constructor injection, alongside a separate registry bean. A probe created a session with the default initialize processor and immediately got **401** from the default message processor.

**Change:** inject one shared lifecycle-managed registry into every processor in each application/Kamelet instance. Do not silently allocate a private fallback. Cover initialize → message → context update → tool call across separate exchanges using the actual Kamelet route.

### 13. [P2] Notification methods report success without delivery

[McpUiSessionRegistry.java:193](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/service/McpUiSessionRegistry.java:193) claims to queue but creates no queue; [McpUiSessionRegistry.java:238](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/service/McpUiSessionRegistry.java:238) has an unimplemented transport sender that returns true. A dummy transport probe returned delivery success. `McpWebSocketNotifier.sendToConnection` also swallows send exceptions, so its caller can report success after failure; the UI processors never use this notifier.

**Change:** connect session notification delivery to a real transport, propagate send results, and provide a bounded queue with a working receive path if polling is supported. Advertise only implemented capabilities. Add a real WS send/receive test and a failed-send assertion. Fix the UI route catch ordering so error properties are set before its post-processor runs.

### 14. [P2] Initialize response uses method names instead of capability objects

[McpInitializeProcessor.java:56](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpInitializeProcessor.java:56) advertises `tools/list: true` and `tools/call: true`, rather than a `tools` object. The consumer sample repeats this. Standards-based clients may conclude that tools are unavailable. Resources are also missing from the core initialization capabilities despite resource support.

**Change:** produce schema-valid `tools`, `resources` and other supported capability objects, and put private extensions in the appropriate extension mechanism. Validate initialization responses against the protocol schema and use an independent client for interoperability testing. [MCP lifecycle specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle).

### 15. [P2] Remote transports do not correctly correlate streamed responses

[McpProducer.java:116](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpProducer.java:116) parses every remote body as a single JSON response although it advertises SSE support. [RemoteMcpToolCallProxyProcessor.java:120](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/RemoteMcpToolCallProxyProcessor.java:120) returns the first nonempty SSE event: a progress-event-then-result probe returned the progress notification and lost the actual result. [WebMcpGatewaySupport.java:174](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/WebMcpGatewaySupport.java:174) concatenates data from different SSE events into one JSON document. Neither helper matches the generated request ID to the selected response.

**Change:** share a content-type-aware incremental SSE parser, dispatch notifications separately, and wait for a result/error with the matching ID. Validate JSON-RPC version and result/error exclusivity. Bound response bytes and in-flight requests. The synchronous `send(...ofString())` paths currently retain complete responses and occupy caller threads until completion; asynchronous processing with bounded concurrency would also improve slow-upstream behavior. MCP permits notifications before the response on an SSE stream: [transport specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports).

### 16. [P2] Built-in standalone UI fails its own server's Accept validation

[mcp-app.html:278](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/resources/io/dscope/camel/mcp/ui/mcp-app.html:278) sends Content-Type but no required Accept value. Fetch's ordinary default does not include both required media types, so `tools/list` is rejected by the component and guarded sample routes.

**Change:** send `Accept: application/json, text/event-stream`, handle the negotiated response format, and establish the protocol lifecycle when connecting directly. Add a standalone browser smoke test against the guarded server.

### 17. [P2] Normal logging exposes full incoming messages

[mcp-rest-service.kamelet.yaml:95](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/resources/kamelets/mcp-rest-service.kamelet.yaml:95) logs `showAll=true` at INFO before validation, including bodies and headers. [McpUiMessageProcessor.java:68](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpUiMessageProcessor.java:68) logs complete messages and session IDs at INFO. Producer DEBUG logging serializes arguments/results, and malformed JSON error logging includes body previews. Credentials or private tool inputs can persist in logs; high-rate malformed requests also amplify log volume.

**Change:** log request IDs, method names, bounded sizes, duration and status by default. Redact authorization, cookies, session IDs and payload content; require explicit opt-in for sanitized diagnostic payloads. Rate-limit repetitive rejection logging. Test with a synthetic secret marker and assert it never appears in normal logs.

### 18. [P2] Transport options cannot be passed through the MCP endpoint

[McpComponent.java:19](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpComponent.java:19) binds every URI parameter to `McpConfiguration`; unknown transport parameters are not preserved. A probe of `mcp:http://localhost:1234/mcp?method=ping&connectTimeout=123` threw `ResolveEndpointFailedException`. This prevents per-endpoint timeout/TLS/auth transport settings through the expected URI shape despite lenient metadata.

**Change:** separate MCP options from validated transport options and preserve the latter on the delegated endpoint, or expose explicit supported configuration fields. Add coverage for timeouts, authentication and TLS settings. Do not forward untrusted exchange control headers indiscriminately as a workaround.

### 19. [P2] Shared session context merges are neither atomic nor bounded

[McpUiUpdateModelContextProcessor.java:63](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpUiUpdateModelContextProcessor.java:63) reads a shared Map and mutates it with `putAll`. `volatile Object modelContext` only publishes the reference; it does not protect the mutable Map. Concurrent updates/serialization can lose data or fail, and an immutable Map supplied through the public session API throws on merge.

**Change:** make merge a registry/session operation using synchronization or atomic immutable snapshots, enforce a total size limit, and define replace/merge behavior for invalid operations and non-object contexts. Test concurrent disjoint merges and immutable initial contexts.

### 20. [P2] Protocol errors and notification statuses lose their meaning

[McpHttpValidatorProcessor.java:48](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpHttpValidatorProcessor.java:48) silently replaces an explicitly invalid version with the default; the local probe confirmed this. The envelope processor coerces types through `toString`, accepts non-scalar IDs and maps unknown methods and parse failures to generic IllegalArgumentException. [McpConsumer.java:139](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/McpConsumer.java:139) consequently emits -32600/400 for parse errors, unknown methods and rate-limit failures alike. [McpNotificationsInitializedProcessor.java:14](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/processor/McpNotificationsInitializedProcessor.java:14) returns 204, whereas accepted HTTP notifications require 202 with no body.

**Change:** validate the declared protocol's field types, reject explicitly unsupported HTTP protocol versions, introduce typed parse/method/parameter/transport errors, use -32700/-32601/-32602 appropriately, and return 429 plus Retry-After for rate exhaustion. Keep missing-version fallback distinct from an invalid explicit version. Add wire-level conformance tests. [MCP transport requirements](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports), [JSON-RPC error definitions](https://www.jsonrpc.org/specification).

### 21. [P2] Resource discovery is not matched by sample resource-read dispatch

[mcp-rest-service.camel.yaml:66](/Users/roman/Projects/DScope/CamelMcpComponent/samples/mcp-service/src/main/resources/routes/mcp-rest-service.camel.yaml:66) handles `resources/list` and `resources/get` but has no `resources/read` branch; the WS sample has the same gap. The consumer sample advertises an `about` resource but provides no read handler. A conforming client discovering a resource cannot retrieve it through the standard method. The Kamelets do include a read branch.

**Change:** wire `resources/read` into both service sample routes and implement the consumer sample's advertised resource, or remove unsupported resource advertisements. Test list → read for every advertised URI.

### 22. [P3] Generated OpenAPI and Karavan metadata do not reliably describe runtime behavior

[McpOpenApiBuilder.java:272](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/camel/mcp/openapi/McpOpenApiBuilder.java:272) quotes YAML only when a value contains a colon. A round-trip probe changed title `Service # Production` to `Service`; numeric-looking versions can also become numbers, and newlines can alter structure. Error responses reference the inner error object rather than the envelope, and generated 429 behavior does not match the current consumer.

[McpKaravanMetadataGenerator.java:218](/Users/roman/Projects/DScope/CamelMcpComponent/src/main/java/io/dscope/tools/karavan/McpKaravanMetadataGenerator.java:218) emits generic `port`, `host`, `path` properties, but the Kamelets use `restPort/restHost/restContextPath` and `wsPort/wsHost/wsPath`. Designer-generated options therefore do not match their consumers.

**Change:** build OpenAPI as typed maps/models and serialize through Jackson YAML; round-trip it and validate its schema. Generate Karavan metadata from the actual component/Kamelet schema rather than duplicating property definitions. Add tests that compare generated property names and example responses against runtime behavior.

## Additional performance and maintainability work

After the P1 fixes, measure the remaining hot paths before introducing broad caching:

- **Catalog rendering:** `tools/list` deep-copies every schema with Jackson on each call; `resources/list` converts every resource to a Map; `resources/read` reads and injects static HTML on every request. Immutable precomputed descriptors/content, with explicit invalidation if reload is added, would reduce allocation. Do not cache session-specific output globally.
- **Rate limiting:** the bucket is shared per processor instance and serializes token operations. A single caller can exhaust the shared allowance. Preserve a global budget but add bounded per-principal budgets where fairness matters; use monotonic elapsed time and perform refill/debit under one lock. Profile contention before changing algorithms.
- **Client failures:** `McpClient.callResult` returns null when the response contains a JSON-RPC error, making upstream errors indistinguishable from no result. Introduce a typed exception or explicit result/error return type.
- **Stream helper:** `McpStreamProcessor` returns a finite `:ok` body, not an ongoing event subscription. Either document it as a placeholder or implement managed subscriptions with disconnect cleanup and backpressure.
- **Catalog/model consistency:** catalogs expose mutable definitions despite an unmodifiable outer map; use immutable snapshots. Align Java DTOs with processor-accepted values (e.g. structured UI messages), preserve unknown extension metadata deliberately, and consolidate duplicated tool-entry construction.
- **Build coverage:** add a reactor or CI matrix for the root and both sample builds, actual Kamelet startup tests, real WebSocket delivery tests, browser security tests and an independent MCP client handshake. Current passing unit tests do not cover these integrations.

## Suggested implementation sequence

1. **Security boundary patch:** findings 1–8 and 17, plus dependency mitigation/upgrade in 11. Keep each boundary change paired with a failing regression case and verify all transport entry points.
2. **Lifecycle and reliability patch:** findings 9–10, 12–13 and 19. Verify bounded resource counts, shutdown/restart and actual notifications.
3. **Interoperability patch:** findings 14–16, 18, 20–21, plus typed client error handling. Use a separate standards-compliant client as the acceptance test.
4. **Tooling/performance patch:** finding 22, sample build coverage, then profile catalog rendering and upstream concurrency under representative workloads.

## Local reproduction results

The probe used only local exchanges, a `direct:` route and bundled sample resources. No external target was attacked or load-tested.

```text
invalidSession businessExecuted=true status=200 (echo result; exception=null)
reservedHeaders={CamelBeanMethodName=dangerous, CamelHttpUri=http://127.0.0.1:9/}
untrustedOriginAccepted version=2025-06-18
traversalRead=true (../mcp/methods.yaml)
sseSelected={jsonrpc=2.0, method=notifications/progress, params={}}
fakeTransportDelivery=true
registryRestart=RejectedExecutionException
yamlTitle=Service (input: Service # Production)
transportOptionRejected=ResolveEndpointFailedException
guardBytesReadBeforeReject=1048576 (default limit: 32768)
defaultRegistryIsolationStatus=401
```

Review severity reflects the affected feature's potential use, not a claim that every deployment is exploitable. External reverse-proxy authentication, network policy, iframe sandbox/CSP and the exact route composition can reduce exposure. Those deployment controls were not available for inspection.
