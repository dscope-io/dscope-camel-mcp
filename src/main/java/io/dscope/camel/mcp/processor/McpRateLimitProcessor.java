package io.dscope.camel.mcp.processor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

/**
 * Token-bucket rate limiter for MCP requests with system property configuration.
 */
@BindToRegistry("mcpRateLimit")
public class McpRateLimitProcessor implements Processor {

    private final boolean enabled;
    private final int capacity;
    private final double refillPerMillis;
    private final AtomicLong lastRefillTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicInteger availableTokens;
    private double fractionalTokens;

    public McpRateLimitProcessor() {
        this.enabled = Boolean.parseBoolean(System.getProperty("mcp.rate.enabled", "true"));
        this.capacity = Math.max(1, Integer.getInteger("mcp.rate.bucketCapacity", 50));
        double refillPerSecond = Double.parseDouble(System.getProperty("mcp.rate.refillPerSecond", String.valueOf(capacity)));
        if (refillPerSecond <= 0D) {
            this.refillPerMillis = 0D;
        } else {
            this.refillPerMillis = refillPerSecond / 1000D;
        }
        this.availableTokens = new AtomicInteger(capacity);
    }

    @Override
    public void process(Exchange exchange) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        refillTokens(now);
        int remaining;
        synchronized (this) {
            int tokens = availableTokens.get();
            if (tokens <= 0) {
                throw new IllegalArgumentException("Rate limit exceeded: no tokens available (capacity " + capacity + ")");
            }
            remaining = availableTokens.decrementAndGet();
        }
        exchange.setProperty("mcp.rate.tokensRemaining", remaining);
    }

    private void refillTokens(long now) {
        if (refillPerMillis <= 0) {
            return;
        }
        long last = lastRefillTime.get();
        if (now <= last) {
            return;
        }
        long elapsed = now - last;
        double tokensToAdd = elapsed * refillPerMillis;
        if (tokensToAdd <= 0D) {
            return;
        }
        if (lastRefillTime.compareAndSet(last, now)) {
            synchronized (this) {
                int current = availableTokens.get();
                double total = current + tokensToAdd + fractionalTokens;
                int tokensToApply = (int) Math.min(capacity - current, Math.floor(total - current));
                if (tokensToApply > 0) {
                    availableTokens.set(Math.min(capacity, current + tokensToApply));
                }
                double newTotal = Math.min(capacity, total);
                fractionalTokens = Math.max(0D, newTotal - availableTokens.get());
                if (availableTokens.get() >= capacity) {
                    fractionalTokens = 0D;
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCapacity() {
        return capacity;
    }

    public double getRefillPerSecond() {
        return refillPerMillis * 1000D;
    }

    public int getAvailableTokens() {
        return availableTokens.get();
    }

    public long getLastRefillTime() {
        return lastRefillTime.get();
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("enabled", enabled);
        snapshot.put("capacity", capacity);
        snapshot.put("availableTokens", availableTokens.get());
        snapshot.put("refillPerSecond", getRefillPerSecond());
        snapshot.put("lastRefillEpochMillis", lastRefillTime.get());
        return snapshot;
    }
}
