package cn.elvis.monaco.testkit.probe;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class TraceProbe {

    private final Supplier<Instant> clock;
    private final AtomicLong nextSequence = new AtomicLong();
    private final ProbeBuffer<TraceEntry> buffer = new ProbeBuffer<>("trace");

    public TraceProbe(Supplier<Instant> clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized TraceEntry record(TraceEvent event) {
        TraceEntry entry = new TraceEntry(
                nextSequence.getAndUpdate(Math::incrementExact), clock.get(), event);
        buffer.record(entry);
        return entry;
    }

    public void recordError(Throwable error) {
        buffer.recordError(error);
    }

    public List<TraceEntry> snapshot() {
        return buffer.snapshot();
    }

    public List<TraceEntry> awaitCount(int expectedCount, Duration timeout) {
        return buffer.awaitCount(expectedCount, timeout);
    }

    public TraceEntry awaitMatching(Predicate<? super TraceEntry> predicate, Duration timeout) {
        return buffer.awaitMatching(predicate, timeout);
    }
}
