package io.dscope.camel.mcp.processor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates HTTP transport headers for MCP requests.
 */
@BindToRegistry("mcpHttpValidator")
public class McpHttpValidatorProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(McpHttpValidatorProcessor.class);

    public static final String EXCHANGE_PROTOCOL_VERSION = "mcp.http.protocolVersion";
    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";

    private static final Set<String> SUPPORTED_VERSIONS = Set.of("2025-06-18");
    private final Set<String> allowedOrigins;

    public McpHttpValidatorProcessor() {
        this(System.getProperty("mcp.allowedOrigins", "http://localhost,http://127.0.0.1"));
    }

    public McpHttpValidatorProcessor(String allowedOrigins) {
        this(Arrays.stream((allowedOrigins == null ? "" : allowedOrigins).split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    McpHttpValidatorProcessor(Collection<String> allowedOrigins) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
    }

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }

        Message in = exchange.getIn();
        String accept = in.getHeader("Accept", String.class);
        String contentType = in.getHeader("Content-Type", String.class);
        try {
            if (!containsAllMediaTypes(accept, "application/json", "text/event-stream")) {
                throw new IllegalArgumentException(
                    "Accept header must include application/json and text/event-stream for MCP Streamable HTTP transport");
            }

            if (!containsAnyMediaType(contentType, "application/json")) {
                throw new IllegalArgumentException("Content-Type must be application/json for MCP requests");
            }

            String origin = normalize(in.getHeader("Origin", String.class)).orElse(null);
            if (origin != null && !isAllowedOrigin(origin)) {
                throw new IllegalArgumentException("Origin is not allowed");
            }

            Optional<String> requestedVersion = normalize(in.getHeader("MCP-Protocol-Version", String.class));
            if (requestedVersion.isPresent() && !SUPPORTED_VERSIONS.contains(requestedVersion.get())) {
                throw new IllegalArgumentException("Unsupported MCP protocol version: " + requestedVersion.get());
            }
            String protocolVersion = requestedVersion.orElse(DEFAULT_PROTOCOL_VERSION);
            exchange.setProperty(EXCHANGE_PROTOCOL_VERSION, protocolVersion);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Validated HTTP MCP headers protocolVersion={} accept={} contentType={}",
                        protocolVersion, accept, contentType);
            }
        } catch (IllegalArgumentException e) {
            LOG.error("Invalid HTTP MCP headers accept={} contentType={} reason={}",
                    accept, contentType, e.getMessage());
            throw e;
        }
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

    private boolean isAllowedOrigin(String origin) {
        if (allowedOrigins.contains(origin)) {
            return true;
        }
        return (allowedOrigins.contains("http://localhost") && origin.startsWith("http://localhost:"))
                || (allowedOrigins.contains("http://127.0.0.1") && origin.startsWith("http://127.0.0.1:"));
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
