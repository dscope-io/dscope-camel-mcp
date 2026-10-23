package io.dscope.camel.mcp.events;

import java.util.concurrent.ThreadLocalRandom;

/** Receipt classification only. Persistence, live authorization and scheduling are provider-owned. */
public final class McpWebhookDeliveryPolicy {
    public record Decision(long retryDelayMs,String failure) {}
    private final int maximumAttempts;
    public McpWebhookDeliveryPolicy(int maximumAttempts) {
        if(maximumAttempts<1)throw new IllegalArgumentException("Attempt limit must be positive");
        this.maximumAttempts=maximumAttempts;
    }
    public int maximumAttempts(){return maximumAttempts;}
    public Decision receipt(int status,int attempts) {
        if(status>=200&&status<300)return new Decision(-1,null);
        boolean retry=status==408||status==429||status>=500;
        return new Decision(retry&&attempts<maximumAttempts?retryDelay(attempts):-1,
            retry?"remote_transient":"remote_rejected");
    }
    public long unavailableDelay(int attempts){return attempts<maximumAttempts?retryDelay(attempts):-1;}
    public long retryDelay(int attempts) {
        long base=Math.min(3600000L,5000L*(1L<<Math.min(20,Math.max(0,attempts-1))));
        return Math.min(3600000L,(long)(base*ThreadLocalRandom.current().nextDouble(0.8,1.2)));
    }
}
