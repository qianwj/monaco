package cn.elvis.monaco.testkit.time;

import cn.elvis.monaco.core.port.IdGenerator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public final class SequenceIdGenerator implements IdGenerator {

    private final String prefix;
    private final AtomicLong next;

    public SequenceIdGenerator(String prefix) {
        this(prefix, 1);
    }

    public SequenceIdGenerator(String prefix, long firstValue) {
        this.prefix = Objects.requireNonNull(prefix, "prefix");
        if (firstValue < 0) {
            throw new IllegalArgumentException("firstValue must not be negative");
        }
        this.next = new AtomicLong(firstValue);
    }

    @Override
    public String nextId() {
        long value = next.getAndUpdate(Math::incrementExact);
        return prefix + value;
    }
}
