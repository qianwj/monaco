package cn.elvis.monaco.testkit.probe;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

public final class EventProbe<T> {

    private final ProbeBuffer<T> buffer;

    public EventProbe(String name) {
        this.buffer = new ProbeBuffer<>(name);
    }

    public void record(T event) {
        buffer.record(event);
    }

    public void recordError(Throwable error) {
        buffer.recordError(error);
    }

    public List<T> snapshot() {
        return buffer.snapshot();
    }

    public List<T> awaitCount(int expectedCount, Duration timeout) {
        return buffer.awaitCount(expectedCount, timeout);
    }

    public T awaitMatching(Predicate<? super T> predicate, Duration timeout) {
        return buffer.awaitMatching(predicate, timeout);
    }
}
