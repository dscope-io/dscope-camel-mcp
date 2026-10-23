package io.dscope.camel.mcp.events;

import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpCallbackVerificationCacheTest {
    private static class Time extends Clock {
        Instant now=Instant.parse("2026-10-20T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void cacheIsBoundToPrincipalUrlSecretAndExpires() throws Exception {
        var clock=new Time();var cache=new McpCallbackVerificationCache(clock,Duration.ofMinutes(5),10);
        var calls=new AtomicInteger();
        cache.verify("a","https://example.test/a","secret-a",calls::incrementAndGet);
        cache.verify("a","https://example.test/a","secret-a",calls::incrementAndGet);assertEquals(1,calls.get());
        cache.verify("b","https://example.test/a","secret-a",calls::incrementAndGet);
        cache.verify("a","https://example.test/b","secret-a",calls::incrementAndGet);
        cache.verify("a","https://example.test/a","secret-b",calls::incrementAndGet);assertEquals(4,calls.get());
        clock.now=clock.now.plusSeconds(300);
        cache.verify("a","https://example.test/a","secret-a",calls::incrementAndGet);assertEquals(5,calls.get());
        cache.clear();cache.verify("a","https://example.test/a","secret-a",calls::incrementAndGet);assertEquals(6,calls.get());
    }
    @Test void failedChallengesAreNotCachedAndCapacityIsBounded() throws Exception {
        var cache=new McpCallbackVerificationCache(new Time(),Duration.ofMinutes(5),1);
        var calls=new AtomicInteger();
        assertThrows(Exception.class,()->cache.verify("a","url","secret",()->{throw new Exception("challenge");}));
        cache.verify("a","url","secret",calls::incrementAndGet);
        cache.verify("b","url","secret",calls::incrementAndGet);
        cache.verify("a","url","secret",calls::incrementAndGet);assertEquals(3,calls.get());
        assertThrows(IllegalArgumentException.class,()->new McpCallbackVerificationCache(new Time(),Duration.ZERO,1));
    }
}
