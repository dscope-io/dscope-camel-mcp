package io.dscope.camel.mcp;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.Metadata;

public class McpConfiguration {
    // Not using @UriPath since we're storing a full URI that may have its own scheme
    @Metadata(required = false)
    private String uri;
    
    @UriParam(label = "operation", defaultValue = "tools/list")
    private String method = "tools/list";
    
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean websocket = false;
    
    @UriParam(label = "consumer", defaultValue = "false")
    private boolean sendToAll = false;
    
    @UriParam(label = "consumer", defaultValue = "*")
    private String allowedOrigins = "*";
    
    @UriParam(label = "consumer", defaultValue = "POST")
    private String httpMethodRestrict = "POST";
    
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public boolean isWebsocket() { return websocket; }
    public void setWebsocket(boolean websocket) { this.websocket = websocket; }
    public boolean isSendToAll() { return sendToAll; }
    public void setSendToAll(boolean sendToAll) { this.sendToAll = sendToAll; }
    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    public String getHttpMethodRestrict() { return httpMethodRestrict; }
    public void setHttpMethodRestrict(String httpMethodRestrict) { this.httpMethodRestrict = httpMethodRestrict; }
}
