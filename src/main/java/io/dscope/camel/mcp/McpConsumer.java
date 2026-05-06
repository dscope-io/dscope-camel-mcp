package io.dscope.camel.mcp;

import org.apache.camel.Processor;
import org.apache.camel.Exchange;
import org.apache.camel.component.undertow.UndertowConsumer;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.processor.McpJsonRpcEnvelopeProcessor;
import io.dscope.camel.mcp.processor.McpRequestSizeGuardProcessor;
import io.dscope.camel.mcp.processor.McpRateLimitProcessor;
import io.dscope.camel.mcp.processor.McpHttpValidatorProcessor;

/**
 * Camel consumer-side implementation for MCP server calls.
 * <p>
 * It creates an Undertow HTTP/WebSocket listener, applies MCP pre-processing
 * (size guard, HTTP validation, rate limiting, JSON-RPC envelope parsing),
 * delegates to the route processor, and normalizes JSON responses.
 */
public class McpConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(McpConsumer.class);
    private static final int JSON_RPC_INVALID_REQUEST = -32600;
    private static final int JSON_RPC_INTERNAL_ERROR = -32603;
    
    private final McpEndpoint endpoint;
    private final McpRequestSizeGuardProcessor requestSizeGuard;
    private final McpRateLimitProcessor rateLimit;
    private final McpJsonRpcEnvelopeProcessor jsonRpcEnvelope;
    private final McpHttpValidatorProcessor httpValidator;
    private final ObjectMapper objectMapper;
    private UndertowConsumer undertowConsumer;
    
    public McpConsumer(McpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.requestSizeGuard = new McpRequestSizeGuardProcessor();
        this.rateLimit = new McpRateLimitProcessor();
        this.jsonRpcEnvelope = new McpJsonRpcEnvelopeProcessor();
        this.httpValidator = new McpHttpValidatorProcessor();
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.info("Starting MCP consumer for endpoint: {}", endpoint.getEndpointUri());
        
        McpConfiguration config = endpoint.getConfiguration();
        LOG.info("Configuration URI before processing: {}", config.getUri());
        
        String undertowUri = buildUndertowUri(config);
        
        LOG.info("Creating MCP server with Undertow URI: {}", undertowUri);
        
        // Create an Undertow endpoint from the normalized URI.
        String fullUndertowUri = "undertow:" + undertowUri;
        LOG.info("Full Undertow URI with component prefix: {}", fullUndertowUri);
        
        UndertowEndpoint undertowEndpoint = (UndertowEndpoint) endpoint.getCamelContext().getEndpoint(fullUndertowUri);
        
        // Build processor chain: MCP guards/parsing -> user processor -> response normalization.
        Processor mcpProcessor = exchange -> {
            long startedAtNanos = System.nanoTime();
            try {
                // 1) Validate request size first to protect resources.
                requestSizeGuard.process(exchange);
                
                // 2) Validate HTTP headers for HTTP transport only.
                if (!config.isWebsocket()) {
                    httpValidator.process(exchange);
                }
                
                // 3) Apply rate limiting and parse JSON-RPC envelope metadata.
                rateLimit.process(exchange);
                jsonRpcEnvelope.process(exchange);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Incoming MCP {} method={} id={}",
                            exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE),
                            exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD),
                            exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID));
                }
                
                // 4) Delegate business handling to the route processor.
                getProcessor().process(exchange);
                
                // 5) Serialize non-string response payloads to JSON.
                Object body = exchange.getMessage().getBody();
                if (body != null && !(body instanceof String) && !(body instanceof byte[])) {
                    try {
                        String json = objectMapper.writeValueAsString(body);
                        exchange.getMessage().setBody(json);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Serialized MCP response body type={} size={}B",
                                    body.getClass().getName(), json.length());
                        }
                    } catch (JsonProcessingException e) {
                        String bodyType = body.getClass().getName();
                        String bodyPreview = body.toString();
                        if (bodyPreview.length() > 100) {
                            bodyPreview = bodyPreview.substring(0, 100) + "...";
                        }
                        throw new IllegalStateException(
                            String.format("Failed to serialize response body to JSON. Type: %s, Preview: %s", 
                                bodyType, bodyPreview), e);
                    }
                }
                
                // 6) Ensure JSON content type is present when not explicitly set.
                if (exchange.getMessage().getHeader("Content-Type") == null) {
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                }

                if (LOG.isDebugEnabled()) {
                    long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                    LOG.debug("Completed MCP request method={} id={} durationMs={} outBodyType={}",
                            exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD),
                            exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID),
                            durationMs,
                            exchange.getMessage().getBody() != null
                                    ? exchange.getMessage().getBody().getClass().getName()
                                    : "<null>");
                }
            } catch (IllegalArgumentException e) {
                long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                LOG.warn("Rejected MCP request type={} method={} id={} endpoint={} durationMs={} reason={}",
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE),
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD),
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID),
                        endpoint.getEndpointUri(),
                        durationMs,
                        e.getMessage());
                writeJsonRpcError(exchange, JSON_RPC_INVALID_REQUEST, e.getMessage(), 400);
            } catch (Exception e) {
                long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
                LOG.error("Error processing MCP request type={} method={} id={} endpoint={} durationMs={}",
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE),
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD),
                        exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID),
                        endpoint.getEndpointUri(),
                        durationMs,
                        e);
                writeJsonRpcError(exchange, JSON_RPC_INTERNAL_ERROR, "An unexpected error occurred", 500);
            }
        };
        
        // Start Undertow consumer with the composed MCP processing pipeline.
        undertowConsumer = (UndertowConsumer) undertowEndpoint.createConsumer(mcpProcessor);
        undertowConsumer.start();
        
        LOG.info("MCP consumer started successfully on {}", undertowUri);
    }
    
    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping MCP consumer for endpoint: {}", endpoint.getEndpointUri());
        
        // Stop transport listener first, then invoke parent lifecycle stop.
        if (undertowConsumer != null) {
            try {
                undertowConsumer.stop();
                LOG.info("MCP consumer stopped successfully");
            } catch (Exception e) {
                LOG.warn("Error stopping MCP Undertow consumer", e);
            }
        }
        
        super.doStop();
    }
    
    /**
     * Builds the Undertow component URI based on configuration.
     */
    private String buildUndertowUri(McpConfiguration config) {
        String baseUri = config.getUri();
        
        if (baseUri == null || baseUri.isBlank()) {
            throw new IllegalArgumentException("URI must be specified for MCP consumer");
        }
        
        // The URI from config might already have http://, or it might just be host:port/path
        // Normalize it to ensure it has a scheme
        if (!baseUri.startsWith("http://") && !baseUri.startsWith("https://") && 
            !baseUri.startsWith("ws://") && !baseUri.startsWith("wss://")) {
            baseUri = "http://" + baseUri;
        }
        
        StringBuilder uri = new StringBuilder();
        
        if (config.isWebsocket()) {
            // Convert http:// to ws:// for WebSocket
            String wsUri = baseUri.replace("http://", "ws://").replace("https://", "wss://");
            uri.append(wsUri);
            appendQueryParam(uri, "sendToAll", String.valueOf(config.isSendToAll()));
            appendQueryParam(uri, "allowedOrigins", config.getAllowedOrigins());
            appendQueryParam(uri, "exchangePattern", "InOut");
        } else {
            // HTTP endpoint
            uri.append(baseUri);
            appendQueryParam(uri, "httpMethodRestrict", config.getHttpMethodRestrict());
        }
        
        return uri.toString();
    }
    
    /**
     * Helper method to append query parameters to URI.
     */
    private void appendQueryParam(StringBuilder uri, String param, String value) {
        String currentUri = uri.toString();
        uri.append(currentUri.contains("?") ? "&" : "?");
        uri.append(param).append("=").append(value);
    }

    private void writeJsonRpcError(Exchange exchange, int code, String message, int statusCode) {
        try {
            Object id = exchange.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID);

            java.util.Map<String, Object> error = new java.util.LinkedHashMap<>();
            error.put("code", code);
            error.put("message", message);

            java.util.Map<String, Object> envelope = new java.util.LinkedHashMap<>();
            envelope.put("jsonrpc", "2.0");
            envelope.put("id", id);
            envelope.put("error", error);

            exchange.getMessage().setBody(objectMapper.writeValueAsString(envelope));
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, statusCode);
            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
            exchange.getMessage().setHeader("Cache-Control", "no-store");

            Object protocolVersion = exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION);
            if (protocolVersion == null) {
                protocolVersion = McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION;
            }
            exchange.getMessage().setHeader("MCP-Protocol-Version", protocolVersion.toString());
        } catch (JsonProcessingException serializationError) {
            LOG.error("Failed to serialize MCP error response endpoint={} code={}", endpoint.getEndpointUri(), code, serializationError);
            throw new RuntimeException(serializationError);
        }
    }
}
