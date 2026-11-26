package io.dscope.camel.mcp;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Structural failure summaries: never exception messages, endpoint URIs, headers or payloads. */
public final class McpDiagnostics {
    private McpDiagnostics() {}
    public static String failureSummary(Throwable failure) {
        Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        StringBuilder result=new StringBuilder();int depth=0;
        for(Throwable cause=failure;cause!=null&&seen.add(cause)&&depth++<8;cause=cause.getCause()) {
            if(!result.isEmpty())result.append(" <- ");
            result.append(cause.getClass().getSimpleName());
            if(cause instanceof java.sql.SQLException sql) {
                String state=sql.getSQLState();
                result.append("[sqlState=").append(state!=null&&state.matches("[A-Z0-9]{5}")?state:"unknown").append(']');
            }
        }
        return result.toString();
    }
}
