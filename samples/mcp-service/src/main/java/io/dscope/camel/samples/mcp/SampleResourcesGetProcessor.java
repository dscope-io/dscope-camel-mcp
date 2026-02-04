package io.dscope.camel.samples.mcp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.camel.BindToRegistry;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.dscope.camel.mcp.processor.McpResourcesGetProcessor;

/**
 * Sample resources/get processor that loads resources from the classpath.
 * 
 * <p>This demonstrates the recommended pattern: extend or configure the core
 * {@link McpResourcesGetProcessor} with a custom resource provider.
 * 
 * <p>Supported resource types (auto-detected by extension):
 * <ul>
 *   <li><b>Binary</b> - images (jpg, png, gif, webp, svg), PDFs, fonts, etc.</li>
 *   <li><b>Text</b> - html, css, js, ts, md, xml, yaml, source code, etc.</li>
 *   <li><b>JSON</b> - files without extension loaded as {@code data/{name}.json}</li>
 * </ul>
 * 
 * <p>Uses the static helper methods from {@link McpResourcesGetProcessor} for
 * automatic content type detection and response formatting.
 */
@BindToRegistry("sampleResourcesGet")
public class SampleResourcesGetProcessor extends McpResourcesGetProcessor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    public SampleResourcesGetProcessor() {
        // Configure the resource provider to load from classpath with auto-detection
        setResourceProvider(this::loadFromClasspath);
    }

    private Map<String, Object> loadFromClasspath(String resourceName) {
        // Use static helpers from parent class to detect content type
        if (isBinaryResource(resourceName)) {
            return loadBinaryResource(resourceName);
        }
        if (isTextResource(resourceName)) {
            return loadTextResource(resourceName);
        }
        // Default: treat as JSON (append .json extension)
        return loadJsonResource(resourceName);
    }

    private Map<String, Object> loadJsonResource(String resourceName) {
        String resourcePath = "data/" + resourceName + ".json";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return errorResource("Resource not found: " + resourcePath);
            }
            return OBJECT_MAPPER.readValue(stream, MAP_TYPE);
        } catch (IOException e) {
            return errorResource("Failed to load JSON resource: " + e.getMessage());
        }
    }

    private Map<String, Object> loadTextResource(String resourceName) {
        String resourcePath = "data/" + resourceName;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return errorResource("Resource not found: " + resourcePath);
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String mimeType = getMimeType(resourceName);
            return textResource("resource://data/" + resourceName, mimeType, content);
        } catch (IOException e) {
            return errorResource("Failed to load text resource: " + e.getMessage());
        }
    }

    private Map<String, Object> loadBinaryResource(String resourceName) {
        String resourcePath = "data/" + resourceName;
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return errorResource("Resource not found: " + resourcePath);
            }
            byte[] bytes = stream.readAllBytes();
            String mimeType = getMimeType(resourceName);
            return blobResource("resource://data/" + resourceName, mimeType, bytes);
        } catch (IOException e) {
            return errorResource("Failed to load binary resource: " + e.getMessage());
        }
    }
}
