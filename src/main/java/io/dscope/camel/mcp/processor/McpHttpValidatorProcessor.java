package io.dscope.camel.mcp.processor;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates HTTP transport headers for MCP requests.
 */
@BindToRegistry("mcpHttpValidator")
public class McpHttpValidatorProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(McpHttpValidatorProcessor.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final Pattern BASE64_SENTINEL = Pattern.compile("^=\\?base64\\?.*\\?=$");

    public static final String EXCHANGE_PROTOCOL_VERSION = "mcp.http.protocolVersion";
    public static final String EXCHANGE_REQUEST_META = "mcp.request.meta";
    public static final String EXCHANGE_REQUEST_PARAMS = "mcp.request.params";
    public static final String EXCHANGE_IS_MODERN_PROTOCOL = "mcp.protocol.modern";
    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    public static final String MODERN_PROTOCOL_VERSION = "2026-07-28";
    public static final String META_PROTOCOL_VERSION = "io.modelcontextprotocol/protocolVersion";
    public static final String META_CLIENT_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
    public static final String SUPPORTED_VERSIONS = "mcp.protocol.supportedVersions";

    private static final Set<String> SUPPORTED_VERSION_SET = Set.of(DEFAULT_PROTOCOL_VERSION, MODERN_PROTOCOL_VERSION);

    @Override
    public void process(Exchange exchange) {
        if (exchange == null) {
            throw new IllegalArgumentException("Exchange must not be null");
        }

        Message in = exchange.getIn();
        String accept = in.getHeader("Accept", String.class);
        String contentType = in.getHeader("Content-Type", String.class);
        try {
            Map<String, Object> request = readRequest(in);
            Object id = request.get("id");
            if (id != null) {
                exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID, id);
            }
            String method = stringValue(request.get("method"));
            Map<String, Object> params = objectValue(request.get("params"));
            Map<String, Object> meta = objectValue(params.get("_meta"));
            exchange.setProperty(EXCHANGE_REQUEST_PARAMS, params);
            exchange.setProperty(EXCHANGE_REQUEST_META, meta);

            if (!containsAllMediaTypes(accept, "application/json", "text/event-stream")) {
                throw new IllegalArgumentException(
                    "Accept header must include application/json and text/event-stream for MCP Streamable HTTP transport");
            }

            if (!containsAnyMediaType(contentType, "application/json")) {
                throw new IllegalArgumentException("Content-Type must be application/json for MCP requests");
            }

            String headerVersion = normalize(in.getHeader("MCP-Protocol-Version", String.class)).orElse(null);
            String bodyVersion = stringValue(meta.get(META_PROTOCOL_VERSION));
            boolean modern = MODERN_PROTOCOL_VERSION.equals(headerVersion) || bodyVersion != null;
            String protocolVersion = bodyVersion != null
                    ? bodyVersion
                    : Optional.ofNullable(headerVersion).orElse(DEFAULT_PROTOCOL_VERSION);
            exchange.setProperty(EXCHANGE_PROTOCOL_VERSION, protocolVersion);
            exchange.setProperty(EXCHANGE_IS_MODERN_PROTOCOL, MODERN_PROTOCOL_VERSION.equals(protocolVersion));
            exchange.setProperty(SUPPORTED_VERSIONS, SUPPORTED_VERSION_SET.stream().sorted().toList());

            if (modern && bodyVersion == null) {
                throw invalidParams("Request metadata must include " + META_PROTOCOL_VERSION);
            }
            if (modern && headerVersion == null) {
                throw headerMismatch("MCP-Protocol-Version", bodyVersion, null);
            }
            if (headerVersion != null && bodyVersion != null && !headerVersion.equals(bodyVersion)) {
                throw headerMismatch("MCP-Protocol-Version", bodyVersion, headerVersion);
            }

            if (!SUPPORTED_VERSION_SET.contains(protocolVersion)) {
                throw new McpProtocolException(
                        -32022,
                        "Unsupported protocol version",
                        400,
                        Map.of("supported", SUPPORTED_VERSION_SET.stream().sorted().toList(), "requested", protocolVersion));
            }
            if (MODERN_PROTOCOL_VERSION.equals(protocolVersion)) {
                validateModernMetadata(meta);
                validateModernHeaders(in, method, params);
            }

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

    private Map<String, Object> readRequest(Message message) {
        Object body = message.getBody();
        if (body instanceof Map<?, ?> rawMap) {
            return copyMap(rawMap);
        }
        try {
            String json = message.getBody(String.class);
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to parse JSON-RPC request for HTTP validation", e);
        }
    }

    private void validateModernMetadata(Map<String, Object> meta) {
        if (meta.isEmpty()) {
            throw invalidParams("Modern MCP requests must include params._meta");
        }
        if (!(meta.get(META_CLIENT_CAPABILITIES) instanceof Map<?, ?>)) {
            throw invalidParams("Request metadata must include an object at " + META_CLIENT_CAPABILITIES);
        }
    }

    private void validateModernHeaders(Message message, String method, Map<String, Object> params) {
        String httpMethod = message.getHeader(Exchange.HTTP_METHOD, String.class);
        if (!"POST".equalsIgnoreCase(httpMethod)) {
            throw new McpProtocolException(-32600, "MCP HTTP requests must use POST", 405, Map.of());
        }

        String methodHeader = normalize(message.getHeader("Mcp-Method", String.class)).orElse(null);
        if (!java.util.Objects.equals(method, methodHeader)) {
            throw headerMismatch("Mcp-Method", method, methodHeader);
        }

        String name = null;
        if ("tools/call".equals(method)) {
            name = identifierValue(params.get("name"));
        } else if ("resources/read".equals(method) || "prompts/get".equals(method)) {
            name = identifierValue(params.get("uri"));
        }
        if (name != null) {
            String nameHeader = normalize(message.getHeader("Mcp-Name", String.class)).orElse(null);
            String decodedName = decodeHeaderValue(nameHeader);
            if (!name.equals(decodedName)) {
                throw headerMismatch("Mcp-Name", name, nameHeader);
            }
        }
    }

    public static String encodeHeaderValue(String value) {
        if (value == null) {
            return null;
        }
        boolean unsafe = value.startsWith(" ") || value.startsWith("\t")
                || value.endsWith(" ") || value.endsWith("\t")
                || value.chars().anyMatch(character -> character != '\t' && (character < 0x20 || character > 0x7e))
                || BASE64_SENTINEL.matcher(value).matches();
        if (!unsafe) {
            return value;
        }
        return "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private String decodeHeaderValue(String value) {
        if (value == null || !BASE64_SENTINEL.matcher(value).matches()) {
            return value;
        }
        String encoded = value.substring("=?base64?".length(), value.length() - "?=".length());
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (IllegalArgumentException | CharacterCodingException e) {
            throw headerMismatch("Mcp-Name", null, value);
        }
    }

    private McpProtocolException headerMismatch(String header, String expected, String actual) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("header", header);
        data.put("expected", expected);
        data.put("actual", actual);
        return new McpProtocolException(-32020, "HTTP header does not match JSON-RPC request", 400, data);
    }

    private McpProtocolException invalidParams(String message) {
        return new McpProtocolException(-32602, message, 400, Map.of());
    }

    private String stringValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text.trim() : null;
    }

    private String identifierValue(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private Map<String, Object> objectValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return copyMap(map);
    }

    private Map<String, Object> copyMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
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
