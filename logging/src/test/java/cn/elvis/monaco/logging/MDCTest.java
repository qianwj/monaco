package cn.elvis.monaco.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MDCTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void putAndGet() {
        MDC.put("clientId", "client-1");
        assertEquals("client-1", MDC.get("clientId"));
    }

    @Test
    void getReturnsNullForMissingKey() {
        assertNull(MDC.get("nonexistent"));
    }

    @Test
    void remove() {
        MDC.put("key", "value");
        MDC.remove("key");
        assertNull(MDC.get("key"));
    }

    @Test
    void clear() {
        MDC.put("a", "1");
        MDC.put("b", "2");
        MDC.clear();
        assertNull(MDC.get("a"));
        assertNull(MDC.get("b"));
    }

    @Test
    void getCopyIsImmutableSnapshot() {
        MDC.put("k", "v");
        Map<String, String> copy = MDC.getCopyOfContextMap();
        assertEquals("v", copy.get("k"));
        assertThrows(UnsupportedOperationException.class, () -> copy.put("x", "y"));
        // modifying MDC after copy doesn't affect the copy
        MDC.put("k", "changed");
        assertEquals("v", copy.get("k"));
    }

    @Test
    void putRejectsNullKey() {
        assertThrows(IllegalArgumentException.class, () -> MDC.put(null, "v"));
    }

    @Test
    void threadIsolation() throws InterruptedException {
        MDC.put("thread", "main");

        var ref = new String[1];
        Thread t = new Thread(() -> {
            ref[0] = MDC.get("thread");
        });
        t.start();
        t.join();

        assertEquals("main", MDC.get("thread"));
        assertNull(ref[0], "Child thread should not see parent MDC");
    }
}
