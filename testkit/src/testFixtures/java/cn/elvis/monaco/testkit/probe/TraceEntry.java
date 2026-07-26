package cn.elvis.monaco.testkit.probe;

import java.time.Instant;
import java.util.Objects;

public record TraceEntry(long sequence, Instant at, TraceEvent event) {

    public TraceEntry {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(event, "event");
    }
}
