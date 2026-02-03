package io.dscope.camel.mcp.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class McpResourceContentTest {

    @Test
    void textFactoryMethodSetsFields() {
        McpResourceContent content = McpResourceContent.text("ui://app", "text/html", "<html></html>");
        
        assertEquals("ui://app", content.getUri());
        assertEquals("text/html", content.getMimeType());
        assertEquals("<html></html>", content.getText());
        assertNull(content.getBlob());
    }

    @Test
    void blobFactoryMethodEncodesData() {
        byte[] data = "test data".getBytes();
        McpResourceContent content = McpResourceContent.blob("ui://binary", "application/octet-stream", data);
        
        assertEquals("ui://binary", content.getUri());
        assertEquals("application/octet-stream", content.getMimeType());
        assertNull(content.getText());
        assertNotNull(content.getBlob());
        assertEquals(Base64.getEncoder().encodeToString(data), content.getBlob());
    }

    @Test
    void blobWithNullDataReturnsNullBlob() {
        McpResourceContent content = McpResourceContent.blob("ui://null", "application/octet-stream", null);
        
        assertNull(content.getBlob());
    }

    @Test
    void settersAndGettersWork() {
        McpResourceContent content = new McpResourceContent();
        content.setUri("ui://test");
        content.setMimeType("text/plain");
        content.setText("Hello");
        content.setBlob("encoded");
        
        assertEquals("ui://test", content.getUri());
        assertEquals("text/plain", content.getMimeType());
        assertEquals("Hello", content.getText());
        assertEquals("encoded", content.getBlob());
    }
}
