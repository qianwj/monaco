package cn.elvis.monaco.testkit.time;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.Disposable;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualBrokerSchedulerTest {

    @Test
    void executesByDueTimeThenRegistrationOrder() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();
        scheduler.scheduleAsync("later", Duration.ofSeconds(2), record(calls, "later"));
        scheduler.scheduleAsync("first", Duration.ofSeconds(1), record(calls, "first"));
        scheduler.scheduleAsync("second", Duration.ofSeconds(1), record(calls, "second"));

        StepVerifier.create(scheduler.advanceBy(Duration.ofSeconds(2))).verifyComplete();

        assertEquals(List.of("first", "second", "later"), calls);
        assertEquals(0, scheduler.pendingTaskCount());
    }

    @Test
    void replacesAndCancelsTasksByKey() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();
        scheduler.scheduleAsync("replace", Duration.ZERO, record(calls, "old"));
        scheduler.scheduleAsync("replace", Duration.ZERO, record(calls, "new"));
        scheduler.scheduleAsync("cancel", Duration.ZERO, record(calls, "cancelled"));

        assertTrue(scheduler.cancelNow("cancel"));
        assertFalse(scheduler.cancelNow("cancel"));
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();

        assertEquals(List.of("new"), calls);
    }

    @Test
    void executesTasksScheduledByDueActions() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();
        scheduler.scheduleAsync("outer", Duration.ZERO, () -> Mono.fromRunnable(() -> {
            calls.add("outer");
            scheduler.scheduleAsync("inner", Duration.ZERO, record(calls, "inner"));
        }));

        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();

        assertEquals(List.of("outer", "inner"), calls);
    }

    @Test
    void propagatesActionFailureAndLeavesLaterTaskPending() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        scheduler.scheduleAsync("failure", Duration.ZERO, () -> Mono.error(new TestException()));
        scheduler.scheduleAsync("later", Duration.ZERO, Mono::empty);

        StepVerifier.create(scheduler.runDueTasks()).expectError(TestException.class).verify();

        assertEquals(1, scheduler.pendingTaskCount());
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();
    }

    @Test
    void limitsCascadingTasks() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock, 2);
        scheduler.scheduleAsync("one", Duration.ZERO, () -> Mono.fromRunnable(() ->
                scheduler.scheduleAsync("two", Duration.ZERO, () -> Mono.fromRunnable(() ->
                        scheduler.scheduleAsync("three", Duration.ZERO, Mono::empty)))));

        StepVerifier.create(scheduler.runDueTasks())
                .expectErrorMessage("scheduled task cascade exceeded 2")
                .verify();

        assertEquals(1, scheduler.pendingTaskCount());
    }

    @Test
    void validatesArguments() {
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock());

        assertThrows(IllegalArgumentException.class,
                () -> scheduler.scheduleAsync("", Duration.ZERO, Mono::empty));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.scheduleAsync("task", Duration.ofSeconds(-1), Mono::empty));
        assertThrows(IllegalArgumentException.class,
                () -> new ManualBrokerScheduler(clock(), 0));
    }

    @Test
    void implementsLazySchedulerPortOperations() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();

        Mono<Void> schedule = scheduler.schedule(
                "port-task", Duration.ZERO, () -> calls.add("ran"));
        assertEquals(0, scheduler.pendingTaskCount());

        StepVerifier.create(schedule).verifyComplete();
        assertEquals(1, scheduler.pendingTaskCount());
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();
        assertEquals(List.of("ran"), calls);

        StepVerifier.create(scheduler.schedule(
                "cancelled", Duration.ZERO, () -> calls.add("cancelled"))).verifyComplete();
        StepVerifier.create(scheduler.cancel("cancelled")).verifyComplete();
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();
        assertEquals(List.of("ran"), calls);
    }

    @Test
    void concurrentDrainDoesNotAdvanceClock() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        scheduler.scheduleAsync("never", Duration.ZERO, Mono::never);
        Disposable running = scheduler.runDueTasks().subscribe();
        try {
            StepVerifier.create(scheduler.advanceBy(Duration.ofSeconds(1)))
                    .expectErrorMessage("scheduler is already draining")
                    .verify();
            assertEquals(Instant.parse("2026-07-25T00:00:00Z"), clock.now());
        } finally {
            running.dispose();
        }
    }

    private static MutableBrokerClock clock() {
        return MutableBrokerClock.startingAt(Instant.parse("2026-07-25T00:00:00Z"));
    }

    private static java.util.function.Supplier<Mono<Void>> record(List<String> calls, String value) {
        return () -> Mono.fromRunnable(() -> calls.add(value));
    }

    private static final class TestException extends RuntimeException {
    }
}
