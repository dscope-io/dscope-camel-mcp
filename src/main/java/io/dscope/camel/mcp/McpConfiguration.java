package io.dscope.camel.mcp;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

public class McpConfiguration {
    @UriPath
    private String uri;
    @UriParam(label = "operation")
    private String method = "tools/list";
    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
}
