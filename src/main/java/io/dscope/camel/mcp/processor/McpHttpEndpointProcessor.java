package io.dscope.camel.mcp.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/** Reusable JSON-RPC HTTP orchestration. Authenticate upstream; the provider authorizes every tool. */
public final class McpHttpEndpointProcessor implements Processor {
    private static final ObjectMapper JSON=new ObjectMapper();
    public interface Provider {
        List<Map<String,Object>> tools(Exchange exchange) throws Exception;
        boolean authorize(Exchange exchange,String name,Map<String,Object> arguments) throws Exception;
        Map<String,Object> invoke(Exchange exchange,String name,Map<String,Object> arguments) throws Exception;
        default boolean supportsExtension(String method){return false;}
        default Map<String,Object> extension(Exchange exchange,Map<String,Object> request) throws Exception {
            throw new McpProtocolException(-32601,"Method not found",200,Map.of());
        }
        default void observe(Exchange exchange,Map<String,Object> response) {}
    }
    private final Provider provider;
    private final String allowedOrigins;
    public McpHttpEndpointProcessor(String allowedOrigins,Provider provider) {
        this.allowedOrigins=allowedOrigins;this.provider=Objects.requireNonNull(provider);
    }
    @Override public void process(Exchange exchange) throws Exception {
        Map<String,Object> request;
        try {
            Object raw=JSON.readValue(exchange.getMessage().getBody(String.class),Object.class);
            if(!(raw instanceof Map<?,?> map)) {error(exchange,null,-32600,"Invalid request");return;}
            request=copy(map);
        } catch(Exception invalid) {error(exchange,null,-32700,"Parse error");return;}
        Object id=request.get("id");
        if(!"2.0".equals(request.get("jsonrpc")) || !(request.get("method") instanceof String candidateMethod) || candidateMethod.isBlank()
            || request.containsKey("result") || request.containsKey("error") || id!=null && !(id instanceof String || id instanceof Number)) {
            error(exchange,null,-32600,"Invalid request");return;
        }
        Object rawParams=request.get("params");
        if(request.containsKey("params") && !(rawParams instanceof Map<?,?>)) {
            error(exchange,id,-32602,"params must be an object");return;
        }
        try {
            Map<String,Object> params=rawParams instanceof Map<?,?> map?copy(map):Map.of();
            boolean modern="2026-07-28".equals(exchange.getMessage().getHeader("MCP-Protocol-Version",String.class))
                || params.get("_meta") instanceof Map<?,?> meta && meta.containsKey("io.modelcontextprotocol/protocolVersion");
            exchange.getMessage().setBody(request);
            if(modern)new McpHttpValidatorProcessor(allowedOrigins).process(exchange);
            new McpJsonRpcEnvelopeProcessor().process(exchange);
            if(!request.containsKey("id")) {
                exchange.getMessage().setBody("");exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,202);return;
            }
            String method=(String)request.get("method");
            if(provider.supportsExtension(method)) {respond(exchange,provider.extension(exchange,request));return;}
            switch(method) {
                case "initialize" -> {
                    new McpInitializeProcessor().process(exchange);
                    respond(exchange,copy(JSON.readValue(exchange.getMessage().getBody(String.class),Map.class)));
                }
                case "ping" -> result(exchange,id,Map.of());
                case "tools/list" -> result(exchange,id,Map.of("tools",provider.tools(exchange)));
                case "tools/call" -> {
                    Object rawName=params.get("name");
                    if(!(rawName instanceof String name) || provider.tools(exchange).stream().noneMatch(tool->name.equals(tool.get("name")))) {
                        error(exchange,id,-32602,"Tool is not available on this MCP endpoint");return;
                    }
                    Object rawArgs=params.get("arguments");
                    if(params.containsKey("arguments") && !(rawArgs instanceof Map<?,?>)) {
                        error(exchange,id,-32602,"arguments must be an object");return;
                    }
                    Map<String,Object> arguments=rawArgs instanceof Map<?,?> map?copy(map):Map.of();
                    if(!provider.authorize(exchange,name,arguments)) {
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,403);
                        error(exchange,id,-32003,"Recipient or permission is not authorized for this tool");return;
                    }
                    exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TOOL_NAME,name);
                    exchange.getMessage().setBody(arguments);
                    result(exchange,id,provider.invoke(exchange,name,arguments));
                }
                default -> error(exchange,id,-32601,"Method not found");
            }
        } catch(McpProtocolException invalid) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,invalid.getHttpStatus());
            Map<String,Object> response=errorResponse(id,invalid.getCode(),invalid.getMessage());
            if(!invalid.getData().isEmpty())((Map<String,Object>)response.get("error")).put("data",invalid.getData());
            respond(exchange,response);
        } catch(IllegalArgumentException invalid) {error(exchange,id,-32602,"Invalid MCP parameters or headers");}
        catch(Exception unavailable) {
            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,500);
            error(exchange,id,-32603,"MCP service unavailable");
        }
    }
    private static Map<String,Object> copy(Map<?,?> source) {
        Map<String,Object> result=new LinkedHashMap<>();source.forEach((key,value)->result.put(String.valueOf(key),value));return result;
    }
    private static Map<String,Object> envelope(Object id) {
        Map<String,Object> response=new LinkedHashMap<>();response.put("jsonrpc","2.0");response.put("id",id);return response;
    }
    private static Map<String,Object> errorResponse(Object id,int code,String message) {
        Map<String,Object> response=envelope(id),error=new LinkedHashMap<>();error.put("code",code);error.put("message",message);response.put("error",error);return response;
    }
    private void error(Exchange exchange,Object id,int code,String message) throws Exception {respond(exchange,errorResponse(id,code,message));}
    private void result(Exchange exchange,Object id,Map<String,Object> data) throws Exception {
        Map<String,Object> response=envelope(id);response.put("result",data);respond(exchange,response);
    }
    private void respond(Exchange exchange,Map<String,Object> response) throws Exception {
        provider.observe(exchange,response);writeResponse(exchange,response);
    }
    public static void writeResponse(Exchange exchange,Map<String,Object> response) throws Exception {
        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE,"application/json");
        exchange.getMessage().setBody(JSON.writeValueAsString(McpProtocolSupport.decorateJsonRpcResponse(exchange,response)));
    }
}
