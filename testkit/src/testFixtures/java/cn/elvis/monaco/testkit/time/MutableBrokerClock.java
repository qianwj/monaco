package cn.elvis.monaco.testkit.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

public final class MutableBrokerClock implements Supplier<Instant> {

    private Instant current;

    private MutableBrokerClock(Instant current) {
        this.current = Objects.requireNonNull(current, "current");
    }

    public static MutableBrokerClock startingAt(Instant instant) {
        return new MutableBrokerClock(instant);
    }

    public synchronized Instant now() {
        return current;
    }

    @Override
    public Instant get() {
        return now();
    }

    public synchronized void advance(Duration duration) {
        requireNonNegative(duration);
        current = current.plus(duration);
    }

    static void requireNonNegative(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative()) {
            throw new IllegalArgumentException("duration must not be negative");
        }
    }
}
