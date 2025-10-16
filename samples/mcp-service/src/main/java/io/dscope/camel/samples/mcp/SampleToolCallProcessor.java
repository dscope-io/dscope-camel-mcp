package io.dscope.camel.samples.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;

import io.dscope.camel.mcp.processor.AbstractMcpResponseProcessor;

/**
 * Handles sample MCP tools/call invocations for the sample service.
 */
@BindToRegistry("sampleToolCallProcessor")
public class SampleToolCallProcessor extends AbstractMcpResponseProcessor {

    @Override
    protected void handleResponse(Exchange exchange) throws Exception {
        String toolName = getToolName(exchange);
        if (toolName == null) {
            throw new IllegalArgumentException("Tool name is required for tools/call");
        }

        Map<String, Object> arguments = getRequestParameters(exchange);

        Map<String, Object> toolResult = switch (toolName) {
            case "echo" -> handleEcho(arguments);
            case "summarize" -> handleSummarize(arguments);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };

        writeResult(exchange, toolResult);
    }

    private Map<String, Object> handleEcho(Map<String, Object> arguments) {
        Object text = arguments.getOrDefault("text", "");
        Map<String, Object> content = Map.of(
                "type", "text",
                "text", text.toString());
        return Map.of("content", List.of(content));
    }

    private Map<String, Object> handleSummarize(Map<String, Object> arguments) {
        String text = Objects.toString(arguments.get("text"), "");
        int maxWords = Integer.parseInt(Objects.toString(arguments.getOrDefault("maxWords", "50")));
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
