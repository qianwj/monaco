package cn.elvis.monaco.testkit.probe;

import cn.elvis.monaco.testkit.assertion.TraceAssertions;
import cn.elvis.monaco.testkit.time.MutableBrokerClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TraceProbeTest {

    @Test
    void stampsEventsWithSequenceAndBrokerTime() {
        MutableBrokerClock clock = MutableBrokerClock.startingAt(Instant.parse("2026-07-25T00:00:00Z"));
        TraceProbe trace = new TraceProbe(clock);

        trace.record(new Started());
        clock.advance(Duration.ofSeconds(2));
        trace.record(new Completed());

        List<TraceEntry> snapshot = trace.snapshot();
        assertEquals(0, snapshot.get(0).sequence());
        assertEquals(1, snapshot.get(1).sequence());
        assertEquals(Instant.parse("2026-07-25T00:00:02Z"), snapshot.get(1).at());
        TraceAssertions.assertContainsInOrder(snapshot, Started.class, Completed.class);
    }

    private record Started() implements TraceEvent {
    }

    private record Completed() implements TraceEvent {
    }
}
