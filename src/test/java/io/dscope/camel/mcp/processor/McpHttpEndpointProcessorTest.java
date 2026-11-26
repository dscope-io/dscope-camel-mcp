package io.dscope.camel.mcp.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpHttpEndpointProcessorTest {
    private final ObjectMapper json=new ObjectMapper();
    private static class Provider implements McpHttpEndpointProcessor.Provider {
        int calls;boolean allowed=true,fail;
        public List<Map<String,Object>> tools(Exchange e){return List.of(Map.of("name","example.read","inputSchema",Map.of("type","object")));}
        public boolean authorize(Exchange e,String name,Map<String,Object> args){return allowed;}
        public Map<String,Object> invoke(Exchange e,String name,Map<String,Object> args){
            calls++;if(fail)throw new IllegalStateException("private secret in backend failure");return Map.of("isError",false,"structuredContent",args);
        }
    }
    private Exchange request(Provider provider,String body,boolean modern) throws Exception {
        return request(provider,body,modern,"",null,null);
    }
    private Exchange request(Provider provider,String body,boolean modern,String allowedOrigins,String origin,String protocolVersion) throws Exception {
        var context=new DefaultCamelContext();var e=new DefaultExchange(context);e.getMessage().setBody(body);
        e.getMessage().setHeader(Exchange.HTTP_METHOD,"POST");
        e.getMessage().setHeader("Content-Type","application/json");e.getMessage().setHeader("Accept","application/json, text/event-stream");
        if(modern) {
            Map<?,?> rpc=json.readValue(body,Map.class);
            e.getMessage().setHeader("MCP-Protocol-Version","2026-07-28");
            e.getMessage().setHeader("Mcp-Method",rpc.get("method"));
            Object params=rpc.get("params");
            if(params instanceof Map<?,?> map && map.get("name") instanceof String name)e.getMessage().setHeader("Mcp-Name",name);
        }
        if(origin!=null)e.getMessage().setHeader("Origin",origin);
        if(protocolVersion!=null)e.getMessage().setHeader("MCP-Protocol-Version",protocolVersion);
        new McpHttpEndpointProcessor(allowedOrigins,provider).process(e);return e;
    }
    private Map<?,?> rpc(Exchange e) throws Exception{return json.readValue(e.getMessage().getBody(String.class),Map.class);}
    private String call(Map<String,Object> arguments,boolean modern) throws Exception {
        Map<String,Object> params=new LinkedHashMap<>();params.put("name","example.read");params.put("arguments",arguments);
        if(modern)params.put("_meta",Map.of("io.modelcontextprotocol/protocolVersion","2026-07-28","io.modelcontextprotocol/clientCapabilities",Map.of()));
        return json.writeValueAsString(Map.of("jsonrpc","2.0","id",42,"method","tools/call","params",params));
    }
    @Test void invokesAuthorizedProviderAndDecoratesModernResponses() throws Exception {
        for(boolean modern:new boolean[]{false,true}) {
            var provider=new Provider();var exchange=request(provider,call(Map.of("recipient","a"),modern),modern);
            Map<?,?> response=rpc(exchange);
            assertEquals(42,response.get("id"));Map<?,?> result=(Map<?,?>)response.get("result");
            assertEquals(Map.of("recipient","a"),result.get("structuredContent"));assertEquals(1,provider.calls);
            assertEquals(modern,result.containsKey("resultType"));
            assertEquals(modern?"2026-07-28":McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION,
                exchange.getMessage().getHeader("MCP-Protocol-Version"));
            assertEquals("no-store",exchange.getMessage().getHeader("Cache-Control"));
        }
    }
    @Test void deniedRecipientsAndNotificationsNeverInvokeTools() throws Exception {
        var provider=new Provider();provider.allowed=false;
        var denied=request(provider,call(Map.of(),false),false);
        assertEquals(403,denied.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));assertEquals(-32003,((Map<?,?>)rpc(denied).get("error")).get("code"));
        var notification=request(provider,"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}",false);
        assertEquals(202,notification.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));assertEquals("",notification.getMessage().getBody());assertEquals(0,provider.calls);
    }
    @Test void validatesLegacyTransportAndRejectsNullIdToolNotifications() throws Exception {
        var provider=new Provider();
        var deniedOrigin=request(provider,call(Map.of(),false),false,"","https://untrusted.example",null);
        assertEquals(403,deniedOrigin.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        var unsupportedVersion=request(provider,call(Map.of(),false),false,"",null,"2099-01-01");
        assertEquals(400,unsupportedVersion.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        var notification=request(provider,"{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/call\",\"params\":{\"name\":\"example.read\"}}",false);
        assertEquals(202,notification.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("",notification.getMessage().getBody());
        assertEquals(0,provider.calls);
    }
    @Test void rejectsMalformedEnvelopesAndArguments() throws Exception {
        var provider=new Provider();
        for(String body:List.of("not-json","[]","{\"jsonrpc\":\"2.0\",\"id\":true,\"method\":\"ping\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"example.read\",\"arguments\":[]}}"))
            assertTrue(rpc(request(provider,body,false)).containsKey("error"));
        assertEquals(0,provider.calls);
    }
    @Test void backendFailuresDoNotExposeExceptionText() throws Exception {
        var provider=new Provider();provider.fail=true;var response=request(provider,call(Map.of(),false),false);
        assertEquals(500,response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertFalse(response.getMessage().getBody(String.class).contains("private secret"));
    }
    @Test void initializePingCatalogAndExplicitExtensionsShareTheSameEnvelope() throws Exception {
        var provider=new Provider();
        for(String method:List.of("initialize","ping","tools/list")) {
            var response=rpc(request(provider,json.writeValueAsString(Map.of("jsonrpc","2.0","id","same-id","method",method,"params",Map.of())),false));
            assertEquals("same-id",response.get("id"));assertTrue(response.containsKey("result"));
        }
        var extension=new Provider(){
            int calls;
            @Override public boolean supportsExtension(String method){return method.equals("server/discover");}
            @Override public Map<String,Object> extension(Exchange e,Map<String,Object> request){
                calls++;
                assertEquals("server/discover",e.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD));
                assertEquals("REQUEST",e.getProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE));
                return Map.of("jsonrpc","2.0","id",request.get("id"),"result",Map.of("capabilities",Map.of("events",Map.of())));
            }
        };
        assertTrue(rpc(request(extension,"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"server/discover\"}",false)).containsKey("result"));
        assertEquals(0,provider.calls);assertEquals(1,extension.calls);
    }
    @Test void supportedExtensionsRunOnlyAfterCommonValidation() throws Exception {
        var extension=new Provider(){
            int calls;
            @Override public boolean supportsExtension(String method){return method.equals("example/extension");}
            @Override public Map<String,Object> extension(Exchange e,Map<String,Object> request){
                calls++;
                return Map.of("jsonrpc","2.0","id",request.get("id"),"result",Map.of("custom",true));
            }
        };
        String valid="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"example/extension\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\",\"io.modelcontextprotocol/clientCapabilities\":{}}}}";
        assertTrue(rpc(request(extension,valid,true)).containsKey("result"));
        String invalid="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"example/extension\",\"params\":{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}}";
        assertTrue(rpc(request(extension,invalid,true)).containsKey("error"));
        assertEquals(1,extension.calls);
    }
    @Test void supportedNotificationExtensionsRunWithoutWritingAResponse() throws Exception {
        var extension=new Provider(){
            int calls;
            @Override public boolean supportsExtension(String method){return method.equals("ui/notifications/tool-input");}
            @Override public Map<String,Object> extension(Exchange e,Map<String,Object> request){
                calls++;
                return Map.of("jsonrpc","2.0","id",1,"result",Map.of("custom",true));
            }
        };
        for(String id:List.of("",",\"id\":null")) {
            var response=request(extension,"{\"jsonrpc\":\"2.0\""+id+",\"method\":\"ui/notifications/tool-input\",\"params\":{}}",false);
            assertEquals(202,response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("",response.getMessage().getBody());
        }
        assertEquals(2,extension.calls);
    }
    @Test void rejectsNonObjectMetadataBeforeProviderDispatch() throws Exception {
        var provider=new Provider();
        for(String meta:List.of("[]","\"invalid\"")) {
            String body="{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"example.read\",\"_meta\":"+meta+"}}";
            Map<?,?> response=rpc(request(provider,body,false));
            assertEquals(-32602,((Map<?,?>)response.get("error")).get("code"));
        }
        assertEquals(0,provider.calls);
    }
    @Test void initializeAdvertisesOnlyProviderSupportedCapabilities() throws Exception {
        var provider=new Provider();
        Map<?,?> response=rpc(request(provider,"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",false));
        Map<?,?> result=(Map<?,?>)response.get("result");
        assertEquals(Map.of("tools",Map.of()),result.get("capabilities"));
        var extendedProvider=new Provider(){
            @Override public boolean supportsExtension(String method){return method.equals("resources/list") || method.equals("resources/read");}
        };
        Map<?,?> extendedResponse=rpc(request(extendedProvider,"{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}",false));
        Map<?,?> extendedResult=(Map<?,?>)extendedResponse.get("result");
        assertEquals(Map.of("tools",Map.of(),"resources",Map.of()),extendedResult.get("capabilities"));
    }
}
