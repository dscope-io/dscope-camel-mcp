package io.dscope.camel.mcp.events;

import java.util.Map;
import org.apache.camel.Exchange;

/**
 * Application-owned event catalog and subscription lifecycle. Implementations must
 * authorize using the exchange, validate arguments against their catalog, derive
 * stable identities using canonical arguments, verify callbacks before activation,
 * and persist ownership, filters, secrets and granted expiration. Refresh, replay,
 * verification caching by principal/URL, rotation and retries belong to the provider.
 * Register one provider in Camel's registry to enable consumer discovery/dispatch,
 * or explicitly route requests through McpEventsProcessor(provider).
 */
public interface McpEventsProvider {
    /** Return events and optionally nextCursor. Only expose authorized events. */
    Map<String, Object> list(Exchange exchange, Map<String, Object> parameters) throws Exception;

    /**
     * Return id, refreshBefore (ISO timestamp or null), cursor and truncated.
     * Call McpWebhookClient.verify before activating a new callback. Parameters
     * include name, arguments, delivery, optional cursor and optional ttlMs;
     * absence of ttlMs and an explicitly null ttlMs have different semantics.
     */
    Map<String, Object> subscribe(Exchange exchange, Map<String, Object> parameters) throws Exception;

    /** Idempotently stop the authorized identity described by name/arguments/URL. */
    void unsubscribe(Exchange exchange, Map<String, Object> parameters) throws Exception;
}
