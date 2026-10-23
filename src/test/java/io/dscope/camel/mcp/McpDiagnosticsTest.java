package io.dscope.camel.mcp;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class McpDiagnosticsTest {
    @Test void retainsOnlyStructureAndHandlesCauseCycles() {
        var outer=new IllegalStateException("https://user:password@example.test/?token=secret");
        var inner=new java.sql.SQLException("private database details","08001");outer.initCause(inner);inner.initCause(outer);
        String result=McpDiagnostics.failureSummary(outer);
        assertEquals("IllegalStateException <- SQLException[sqlState=08001]",result);
        assertFalse(result.contains("secret"));assertFalse(result.contains("password"));
    }
}
