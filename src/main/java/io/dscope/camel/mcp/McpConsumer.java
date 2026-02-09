package io.dscope.camel.mcp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.undertow.UndertowConsumer;
import org.apache.camel.component.undertow.UndertowEndpoint;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.processor.McpJsonRpcEnvelopeProcessor;
import io.dscope.camel.mcp.processor.McpRequestSizeGuardProcessor;
import io.dscope.camel.mcp.processor.McpRateLimitProcessor;
import io.dscope.camel.mcp.processor.McpHttpValidatorProcessor;

/**
 * MCP Consumer that sets up HTTP or WebSocket server endpoints to receive MCP JSON-RPC requests.
 * <p>
 * The consumer wraps an Undertow consumer to listen for incoming MCP requests,
 * processes them through the JSON-RPC envelope parser, and delegates to the configured processor.
 */
public class McpConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(McpConsumer.class);
    
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
        
        // Create an Undertow endpoint using the CamelContext
        // The URI must include the "undertow:" prefix for the component
        String fullUndertowUri = "undertow:" + undertowUri;
        LOG.info("Full Undertow URI with component prefix: {}", fullUndertowUri);
        
        UndertowEndpoint undertowEndpoint = (UndertowEndpoint) endpoint.getCamelContext().getEndpoint(fullUndertowUri);
        
        // Create a processor chain that includes MCP processing before calling user processor
        Processor mcpProcessor = exchange -> {
            try {
                // Apply MCP processors in sequence
                requestSizeGuard.process(exchange);
                
                if (!config.isWebsocket()) {
                    httpValidator.process(exchange);
                }
                
                rateLimit.process(exchange);
                jsonRpcEnvelope.process(exchange);
                
                // Call the user's processor
                getProcessor().process(exchange);
                
                // Serialize response to JSON if it's a Map or POJO
                Object body = exchange.getMessage().getBody();
                if (body != null && !(body instanceof String) && !(body instanceof byte[])) {
                    String json = objectMapper.writeValueAsString(body);
                    exchange.getMessage().setBody(json);
                }
                
                // Ensure response has JSON content type
                if (exchange.getMessage().getHeader("Content-Type") == null) {
                    exchange.getMessage().setHeader("Content-Type", "application/json");
                }
            } catch (Exception e) {
                LOG.error("Error processing MCP request", e);
                throw e;
            }
        };
        
        // Create and start the Undertow consumer with our processor chain
        undertowConsumer = (UndertowConsumer) undertowEndpoint.createConsumer(mcpProcessor);
        undertowConsumer.start();
        
        LOG.info("MCP consumer started successfully on {}", undertowUri);
    }
    
    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping MCP consumer for endpoint: {}", endpoint.getEndpointUri());
        
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
            uri.append(wsUri.contains("?") ? "&" : "?");
            uri.append("sendToAll=").append(config.isSendToAll());
            uri.append("&allowedOrigins=").append(config.getAllowedOrigins());
            uri.append("&exchangePattern=InOut");
        } else {
            // HTTP endpoint
            uri.append(baseUri);
            uri.append(baseUri.contains("?") ? "&" : "?");
            uri.append("httpMethodRestrict=").append(config.getHttpMethodRestrict());
        }
        
        return uri.toString();
    }
}
