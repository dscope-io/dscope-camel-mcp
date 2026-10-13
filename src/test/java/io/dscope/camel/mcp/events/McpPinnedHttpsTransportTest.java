package io.dscope.camel.mcp.events;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpPinnedHttpsTransportTest {
    @Test void terminalStatusesSkipThrowingAndOversizedBodiesAndCloseImmediately() throws Exception {
        for (int status : new int[]{410, 413}) {
            for (boolean oversized : new boolean[]{false, true}) {
                var reads = new java.util.concurrent.atomic.AtomicInteger();
                var closed = new java.util.concurrent.atomic.AtomicBoolean();
                java.io.InputStream body = new java.io.InputStream() {
                    @Override public int read() throws java.io.IOException {
                        reads.incrementAndGet();
                        if (oversized) return 'x'; // Never-ending body exceeds any limit.
                        throw new java.io.IOException("Unreadable terminal response body");
                    }
                    @Override public void close() { closed.set(true); }
                };
                var raw = new BasicClassicHttpResponse(status);
                raw.setEntity(new InputStreamEntity(body,
                        oversized ? McpWebhookClient.MAX_BODY_BYTES + 1L : -1L, ContentType.APPLICATION_JSON));
                var closeMode = new java.util.concurrent.atomic.AtomicReference<CloseMode>();
                var response = CloseableHttpResponse.create(raw, (ignored, mode) -> {
                    closeMode.set(mode);
                    body.close();
                    // Cleanup failures must not mask the terminal status either.
                    throw new java.io.IOException("Connection close failure");
                });
                var result = McpPinnedHttpsTransport.readResponse(new HttpPost("https://receiver.example"), response);
                assertEquals(status, result.status());
                assertEquals(0, result.body().length);
                assertEquals(0, reads.get(), "Terminal response entity must not be consumed");
                assertTrue(closed.get());
                assertEquals(CloseMode.IMMEDIATE, closeMode.get());
            }
        }
    }

    @Test void successfulVerificationResponseRetainsChallengeBody() throws Exception {
        byte[] challenge = "{\"challenge\":\"fresh-value\"}".getBytes(StandardCharsets.UTF_8);
        var raw = new BasicClassicHttpResponse(200);
        raw.setEntity(new InputStreamEntity(new java.io.ByteArrayInputStream(challenge), challenge.length, ContentType.APPLICATION_JSON));
        var closeMode = new java.util.concurrent.atomic.AtomicReference<CloseMode>();
        var response = CloseableHttpResponse.create(raw, (ignored, mode) -> closeMode.set(mode));
        var result = McpPinnedHttpsTransport.readResponse(new HttpPost("https://receiver.example"), response);
        assertEquals(200, result.status());
        assertArrayEquals(challenge, result.body());
        assertEquals(CloseMode.IMMEDIATE, closeMode.get());
    }

    @Test void deadlineInterruptionAbortsAnInFlightTlsConnection() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            var connected = new java.util.concurrent.CountDownLatch(1);
            FutureTask<byte[]> peerClosed = new FutureTask<>(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = socket.getInputStream();
                    byte[] header = input.readNBytes(5);
                    int size = ((header[3] & 255) << 8) | (header[4] & 255);
                    input.readNBytes(size);
                    connected.countDown();
                    try {
                        return input.readNBytes(1000);
                    } catch (java.net.SocketException reset) {
                        // Aborting a TLS handshake may close with TCP RST rather
                        // than EOF; either proves the connection was terminated.
                        return new byte[0];
                    }
                }
            });
            Thread.ofVirtual().start(peerClosed);
            var completed = new java.util.concurrent.CompletableFuture<Exception>();
            Thread request = Thread.ofVirtual().start(() -> {
                try {
                    new McpPinnedHttpsTransport().post(URI.create("https://callback-pin-test.invalid:" + server.getLocalPort()),
                            new InetAddress[]{InetAddress.getLoopbackAddress()}, Map.of(), new byte[0]);
                    completed.complete(null);
                } catch (Exception e) { completed.complete(e); }
            });
            assertTrue(connected.await(5, TimeUnit.SECONDS));
            request.interrupt();
            Exception error = completed.get(3, TimeUnit.SECONDS);
            assertInstanceOf(McpCallbackException.class, error);
            assertEquals("timeout", ((McpCallbackException) error).getReason());
            peerClosed.get(3, TimeUnit.SECONDS); // TCP connection must close promptly.
        }
    }

    @Test void actualConnectionUsesPinnedAddressAndOriginalTlsHostname() throws Exception {
        // A TLS-record capture fixture deliberately cannot finish the handshake.
        // Calling the transport directly allows loopback solely for this fixture;
        // the public client rejects loopback before invoking it.
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(5000);
            FutureTask<byte[]> handshake = new FutureTask<>(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = socket.getInputStream();
                    byte[] header = input.readNBytes(5);
                    assertEquals(5, header.length);
                    assertEquals(22, header[0] & 255); // TLS handshake record
                    int size = ((header[3] & 255) << 8) | (header[4] & 255);
                    assertTrue(size < 16384);
                    return input.readNBytes(size);
                }
            });
            Thread.ofVirtual().start(handshake);
            URI destination = URI.create("https://callback-pin-test.invalid:" + server.getLocalPort() + "/webhook");
            assertThrows(java.io.IOException.class, () -> new McpPinnedHttpsTransport().post(destination,
                    new InetAddress[]{InetAddress.getLoopbackAddress()}, Map.of("webhook-id", "test"), new byte[]{1}));
            String hello = new String(handshake.get(5, TimeUnit.SECONDS), StandardCharsets.ISO_8859_1);
            assertTrue(hello.contains("callback-pin-test.invalid"), "Original hostname must be preserved as TLS SNI");
        }
    }
}
