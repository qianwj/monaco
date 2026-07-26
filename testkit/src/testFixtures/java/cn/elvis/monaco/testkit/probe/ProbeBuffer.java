package cn.elvis.monaco.testkit.probe;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

final class ProbeBuffer<T> {

    private final String name;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final List<T> values = new ArrayList<>();
    private Throwable lastError;

    ProbeBuffer(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    void record(T value) {
        lock.lock();
        try {
            values.add(Objects.requireNonNull(value, "value"));
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    void recordError(Throwable error) {
        lock.lock();
        try {
            lastError = Objects.requireNonNull(error, "error");
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    List<T> snapshot() {
        lock.lock();
        try {
            return List.copyOf(values);
        } finally {
            lock.unlock();
        }
    }

    List<T> awaitCount(int expectedCount, Duration timeout) {
        if (expectedCount < 0) {
            throw new IllegalArgumentException("expectedCount must not be negative");
        }
        return await(values -> values.size() >= expectedCount,
                "count >= " + expectedCount, timeout);
    }

    T awaitMatching(Predicate<? super T> predicate, Duration timeout) {
        Objects.requireNonNull(predicate, "predicate");
        List<T> snapshot = await(
                values -> values.stream().anyMatch(predicate), "matching value", timeout);
        return snapshot.stream().filter(predicate).findFirst().orElseThrow();
    }

    private List<T> await(Predicate<List<T>> predicate, String expectation, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }

        long remaining = toNanos(timeout);
        lock.lock();
        try {
            while (!predicate.test(values)) {
                if (remaining <= 0) {
                    throw timeout(expectation, timeout);
                }
                try {
                    remaining = changed.awaitNanos(remaining);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting on " + name, error);
                }
            }
            return List.copyOf(values);
        } finally {
            lock.unlock();
        }
    }

    private AssertionError timeout(String expectation, Duration timeout) {
        String message = "Timed out after " + timeout + " waiting for " + name + " " + expectation
                + "; snapshot=" + values + "; lastError=" + lastError;
        return new AssertionError(message, lastError);
    }

    private static long toNanos(Duration timeout) {
        try {
            return timeout.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }
}
