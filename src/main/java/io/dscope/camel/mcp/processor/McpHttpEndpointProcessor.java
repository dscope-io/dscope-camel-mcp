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
            exchange.getMessage().setBody(request);
            new McpHttpValidatorProcessor(allowedOrigins).process(exchange);
            validateCommonEnvelope(exchange,request,params,id,candidateMethod);
            exchange.getMessage().setBody(params);
            if(id==null) {
                exchange.getMessage().setBody("");exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE,202);return;
            }
            String method=(String)request.get("method");
            if(provider.supportsExtension(method)) {respond(exchange,provider.extension(exchange,request));return;}
            switch(method) {
                case "initialize" -> {
                    new McpInitializeProcessor().process(exchange);
                    Map<String,Object> response=copy(JSON.readValue(exchange.getMessage().getBody(String.class),Map.class));
                    Map<String,Object> result=copy((Map<?,?>)response.get("result"));
                    result.put("capabilities",providerCapabilities());
                    response.put("result",result);
                    respond(exchange,response);
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
    private void validateCommonEnvelope(Exchange exchange,Map<String,Object> request,Map<String,Object> params,
        Object id,String method) {
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_RAW_MESSAGE,request);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_METHOD,method);
        exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_TYPE,id==null?"NOTIFICATION":"REQUEST");
        if(id!=null)exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_ID,id);
        if(McpProtocolSupport.isModernProtocol(exchange)) {
            Object inputResponses=params.get("inputResponses");
            if(params.containsKey("inputResponses") && !(inputResponses instanceof Map<?,?>))
                throw new IllegalArgumentException("params.inputResponses must be an object");
            if(params.containsKey("requestState") && !(params.get("requestState") instanceof String))
                throw new IllegalArgumentException("params.requestState must be a string");
            if(inputResponses instanceof Map<?,?> responses)
                exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_INPUT_RESPONSES,responses);
            if(params.containsKey("requestState"))
                exchange.setProperty(McpJsonRpcEnvelopeProcessor.EXCHANGE_PROPERTY_REQUEST_STATE,params.get("requestState"));
        }
    }
    private Map<String,Object> providerCapabilities() {
        Map<String,Object> capabilities=new LinkedHashMap<>();
        capabilities.put("tools",Map.of());
        if(provider.supportsExtension("resources/list") && provider.supportsExtension("resources/read"))
            capabilities.put("resources",Map.of());
        if(provider.supportsExtension("prompts/list") && provider.supportsExtension("prompts/get"))
            capabilities.put("prompts",Map.of());
        if(provider.supportsExtension("logging/setLevel"))capabilities.put("logging",Map.of());
        Map<String,Object> apps=new LinkedHashMap<>();
        Map<String,String> appMethods=Map.of(
            "ui/initialize","ui/initialize",
            "ui/message","ui/message",
            "ui/update-model-context","ui/update-model-context",
            "ui/notifications/tool-input","ui/notifications/tool-input",
            "ui/notifications/tool-result","ui/notifications/tool-result");
        appMethods.forEach((capability,method)->{
            if(provider.supportsExtension(method))apps.put(capability,Boolean.TRUE);
        });
        if(!apps.isEmpty())capabilities.put("experimental",Map.of("camelMcpApps",apps));
        return capabilities;
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
        String protocolVersion=exchange.getProperty(McpHttpValidatorProcessor.EXCHANGE_PROTOCOL_VERSION,String.class);
        exchange.getMessage().setHeader("MCP-Protocol-Version",
            protocolVersion==null?McpHttpValidatorProcessor.DEFAULT_PROTOCOL_VERSION:protocolVersion);
        exchange.getMessage().setHeader("Cache-Control","no-store");
        exchange.getMessage().setBody(JSON.writeValueAsString(McpProtocolSupport.decorateJsonRpcResponse(exchange,response)));
    }
}
