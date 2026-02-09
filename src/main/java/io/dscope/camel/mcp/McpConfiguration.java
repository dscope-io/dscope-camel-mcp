package io.dscope.camel.mcp;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

public class McpConfiguration {

    @UriPath(description = "The target MCP server URI (e.g. http://localhost:8080/mcp). "
            + "For consumers this is the listen address; for producers the remote server address.")
    @Metadata(required = true)
    private String uri;

    @UriParam(label = "producer", defaultValue = "tools/list",
            description = "The MCP JSON-RPC method to invoke. "
                    + "Supported: initialize, ping, tools/list, tools/call, resources/list, resources/read, "
                    + "resources/get, ui/initialize, ui/message, ui/update-model-context, ui/tools/call.",
            enums = "initialize,ping,tools/list,tools/call,resources/list,resources/read,"
                    + "resources/get,health,stream,ui/initialize,ui/message,ui/update-model-context,ui/tools/call")
    private String method = "tools/list";

    @UriParam(label = "consumer", defaultValue = "false",
            description = "When true the consumer creates a WebSocket endpoint instead of HTTP.")
    private boolean websocket = false;

    @UriParam(label = "consumer", defaultValue = "false",
            description = "For WebSocket consumers, whether to broadcast messages to all connected clients.")
    private boolean sendToAll = false;

    @UriParam(label = "consumer", defaultValue = "*",
            description = "Comma-separated list of allowed CORS origins. Use * for any origin.")
    private String allowedOrigins = "*";

    @UriParam(label = "consumer", defaultValue = "POST",
            description = "HTTP methods allowed by the consumer endpoint.")
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
