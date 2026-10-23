package io.dscope.camel.mcp.events;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded challenge cache. It never replaces live authorization or per-delivery destination checks. */
public final class McpCallbackVerificationCache {
    @FunctionalInterface public interface Verification {void run() throws Exception;}
    private final ConcurrentHashMap<String,Instant> verified=new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration lifetime;
    private final int capacity;
    public McpCallbackVerificationCache(Clock clock,Duration lifetime,int capacity) {
        this.clock=Objects.requireNonNull(clock);this.lifetime=Objects.requireNonNull(lifetime);this.capacity=capacity;
        if(lifetime.isZero()||lifetime.isNegative()||capacity<1)throw new IllegalArgumentException("Invalid cache bounds");
    }
    public void verify(String principal,String url,String secret,Verification action) throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        for(String value:new String[]{principal,url,secret}) {
            byte[] bytes=Objects.requireNonNull(value).getBytes(StandardCharsets.UTF_8);
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());digest.update(bytes);
        }
        String key=HexFormat.of().formatHex(digest.digest());
        Instant now=clock.instant(),expiration=verified.get(key);
        if(expiration!=null&&expiration.isAfter(now))return;
        action.run(); // No secret or URL is retained; failed challenges never enter the cache.
        synchronized(verified) {
            verified.entrySet().removeIf(entry->!entry.getValue().isAfter(now));
            if(verified.size()>=capacity)verified.clear();
            verified.put(key,clock.instant().plus(lifetime));
        }
    }
    public void clear(){verified.clear();}
}
