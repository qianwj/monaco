package cn.elvis.monaco.testkit.probe;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventProbeTest {

    @Test
    void recordsConcurrentEventsWithImmutableSnapshot() throws InterruptedException {
        EventProbe<Integer> probe = new EventProbe<>("numbers");
        Thread first = Thread.ofPlatform().start(() -> probe.record(1));
        Thread second = Thread.ofPlatform().start(() -> probe.record(2));
        first.join();
        second.join();

        List<Integer> snapshot = probe.awaitCount(2, Duration.ofSeconds(1));

        assertEquals(2, snapshot.size());
        assertTrue(snapshot.containsAll(List.of(1, 2)));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(3));
    }

    @Test
    void reportsSnapshotAndLastErrorOnTimeout() {
        EventProbe<String> probe = new EventProbe<>("events");
        probe.record("current");
        probe.recordError(new IllegalStateException("last failure"));

        AssertionError error = assertThrows(AssertionError.class,
                () -> probe.awaitMatching("missing"::equals, Duration.ZERO));

        assertTrue(error.getMessage().contains("events"));
        assertTrue(error.getMessage().contains("snapshot=[current]"));
        assertTrue(error.getMessage().contains("last failure"));
        assertEquals("last failure", error.getCause().getMessage());
    }
}
