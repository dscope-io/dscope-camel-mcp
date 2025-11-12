package io.dscope.camel.samples.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

import io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor;

/**
 * Sample tools/call processor supporting two built-in demo tools (echo, summarize)
 * and delegating any other tool name to the resource request/response flow
 * (SampleResourceRequestProcessor + SampleResourceResponseProcessor).
 */
@BindToRegistry("sampleToolCallProcessor")
public class SampleToolCallProcessor extends AbstractMcpResponseProcessor {

    private final SampleResourceRequestProcessor resourceRequest = new SampleResourceRequestProcessor();
    private final SampleResourceResponseProcessor resourceResponse = new SampleResourceResponseProcessor();

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        String toolName = getToolName(exchange);
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Tool name is required for tools/call");
        }

        Map<String, Object> params = getRequestParameters(exchange);

        switch (toolName) {
            case "echo" -> writeResult(exchange, handleEcho(params));
            case "summarize", "summary" -> writeResult(exchange, handleSummarize(params)); // support alias
            default -> delegateToResourceProcessors(exchange, params, toolName);
        }
    }

    private void delegateToResourceProcessors(Exchange exchange, Map<String, Object> params, String toolName) throws Exception {
        // Treat any unknown tool as a resource lookup; the specific tool name isn't
        // used by the resource processors, which rely on the 'resource' argument.
        // This allows Kamelet users to define additional resource-oriented tool names
        // without changing Java code.
        resourceRequest.process(exchange); // extracts resource name (defaults internally)
        resourceResponse.process(exchange); // writes MCP result envelope
    }

    private Map<String, Object> handleEcho(Map<String, Object> arguments) {
        Object text = arguments == null ? "" : arguments.getOrDefault("text", "");
        Map<String, Object> content = Map.of(
                "type", "text",
                "text", Objects.toString(text, ""));
        return Map.of("content", List.of(content));
    }

    private Map<String, Object> handleSummarize(Map<String, Object> arguments) {
        String text = Objects.toString(arguments == null ? null : arguments.get("text"), "");
        int maxWords = Integer.parseInt(Objects.toString(arguments == null ? null : arguments.get("maxWords"), "50"));
        String summary = summarize(text, maxWords);
        Map<String, Object> content = Map.of(
                "type", "text",
                "text", summary);
        return Map.of("content", List.of(content));
    }

    private String summarize(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return "(empty input)";
        }
        String[] words = text.trim().split("\\s+");
        if (words.length <= maxWords) {
            return text.trim();
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(words.length, maxWords); i++) {
            if (i > 0) {
                builder.append(' ');
            }
            builder.append(words[i]);
        }
        builder.append(" …");
        return builder.toString();
    }
}
