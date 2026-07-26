package cn.elvis.monaco.testkit.time;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class ManualBrokerScheduler {

    private static final int DEFAULT_MAX_CASCADE_TASKS = 10_000;

    private final MutableBrokerClock clock;
    private final int maxCascadeTasks;
    private final PriorityQueue<ScheduledTask> tasks = new PriorityQueue<>(
            Comparator.comparing(ScheduledTask::dueAt).thenComparingLong(ScheduledTask::sequence));
    private final Map<String, ScheduledTask> tasksByKey = new HashMap<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private long nextSequence;

    public ManualBrokerScheduler(MutableBrokerClock clock) {
        this(clock, DEFAULT_MAX_CASCADE_TASKS);
    }

    public ManualBrokerScheduler(MutableBrokerClock clock, int maxCascadeTasks) {
        this.clock = Objects.requireNonNull(clock, "clock");
        if (maxCascadeTasks < 1) {
            throw new IllegalArgumentException("maxCascadeTasks must be positive");
        }
        this.maxCascadeTasks = maxCascadeTasks;
    }

    public synchronized void schedule(
            String key,
            Duration delay,
            Supplier<? extends Mono<Void>> action) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        MutableBrokerClock.requireNonNegative(delay);
        Objects.requireNonNull(action, "action");

        Instant dueAt = clock.now().plus(delay);
        ScheduledTask task = new ScheduledTask(key, dueAt, nextSequence(), action);
        ScheduledTask previous = tasksByKey.put(key, task);
        if (previous != null) {
            tasks.remove(previous);
        }
        tasks.add(task);
    }

    public synchronized boolean cancel(String key) {
        Objects.requireNonNull(key, "key");
        ScheduledTask removed = tasksByKey.remove(key);
        if (removed != null) {
            tasks.remove(removed);
            return true;
        }
        return false;
    }

    public synchronized int pendingTaskCount() {
        return tasksByKey.size();
    }

    public Mono<Void> advanceBy(Duration duration) {
        MutableBrokerClock.requireNonNegative(duration);
        return Mono.defer(() -> beginDrain(() -> clock.advance(duration)));
    }

    public Mono<Void> runDueTasks() {
        return Mono.defer(() -> beginDrain(() -> {
        }));
    }

    private Mono<Void> beginDrain(Runnable beforeDrain) {
        if (!draining.compareAndSet(false, true)) {
            return Mono.error(new IllegalStateException("scheduler is already draining"));
        }
        try {
            beforeDrain.run();
        } catch (Throwable error) {
            draining.set(false);
            return Mono.error(error);
        }
        AtomicInteger executed = new AtomicInteger();
        return drainNext(executed).doFinally(ignored -> draining.set(false));
    }

    private Mono<Void> drainNext(AtomicInteger executed) {
        return Mono.defer(() -> {
            ScheduledTask task;
            try {
                task = pollDueTask(executed);
            } catch (RuntimeException error) {
                return Mono.error(error);
            }
            if (task == null) {
                return Mono.empty();
            }

            Mono<Void> action;
            try {
                action = Objects.requireNonNull(task.action().get(), "scheduled action returned null");
            } catch (Throwable error) {
                return Mono.error(error);
            }
            return action.then(drainNext(executed));
        });
    }

    private synchronized ScheduledTask pollDueTask(AtomicInteger executed) {
        while (!tasks.isEmpty()) {
            ScheduledTask next = tasks.peek();
            if (tasksByKey.get(next.key()) != next) {
                tasks.remove();
                continue;
            }
            if (next.dueAt().isAfter(clock.now())) {
                return null;
            }
            if (executed.get() >= maxCascadeTasks) {
                throw new IllegalStateException("scheduled task cascade exceeded " + maxCascadeTasks);
            }
            tasks.remove();
            tasksByKey.remove(next.key(), next);
            executed.incrementAndGet();
            return next;
        }
        return null;
    }

    private long nextSequence() {
        long sequence = nextSequence;
        nextSequence = Math.incrementExact(nextSequence);
        return sequence;
    }

    private record ScheduledTask(
            String key,
            Instant dueAt,
            long sequence,
            Supplier<? extends Mono<Void>> action) {
    }
}
