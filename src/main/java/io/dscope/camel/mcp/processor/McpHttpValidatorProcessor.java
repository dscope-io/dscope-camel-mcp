package io.dscope.camel.mcp.processor;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

/**
 * Validates HTTP transport headers for MCP requests.
 */
@BindToRegistry("mcpHttpValidator")
public class McpHttpValidatorProcessor implements Processor {

    public static final String EXCHANGE_PROTOCOL_VERSION = "mcp.http.protocolVersion";
    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2025-06-18");

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }

        Message in = exchange.getIn();
        String accept = in.getHeader("Accept", String.class);
        if (!containsAllMediaTypes(accept, "application/json", "text/event-stream")) {
            throw new IllegalArgumentException(
                "Accept header must include application/json and text/event-stream for MCP Streamable HTTP transport");
        }

        String contentType = in.getHeader("Content-Type", String.class);
        if (!containsAnyMediaType(contentType, "application/json")) {
            throw new IllegalArgumentException("Content-Type must be application/json for MCP requests");
        }

        String protocolVersion = normalize(in.getHeader("MCP-Protocol-Version", String.class))
                .filter(SUPPORTED_VERSIONS::contains)
                .orElse(DEFAULT_PROTOCOL_VERSION);
        exchange.setProperty(EXCHANGE_PROTOCOL_VERSION, protocolVersion);
    }

    private Optional<String> normalize(String header) {
        if (header == null) {
            return Optional.empty();
        }
        String normalized = header.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private boolean containsAllMediaTypes(String header, String... required) {
        if (header == null || header.isBlank()) {
            return false;
        }
        Set<String> values = Stream.of(header.split(","))
                .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(required)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .allMatch(values::contains);
    }

    private boolean containsAnyMediaType(String header, String... expected) {
        if (header == null || header.isBlank()) {
            return false;
        }
        Set<String> values = Stream.of(header.split(","))
                .map(value -> value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
        return Arrays.stream(expected)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(values::contains);
    }
}
