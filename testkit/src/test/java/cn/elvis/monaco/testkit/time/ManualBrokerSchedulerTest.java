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
        scheduler.schedule("later", Duration.ofSeconds(2), record(calls, "later"));
        scheduler.schedule("first", Duration.ofSeconds(1), record(calls, "first"));
        scheduler.schedule("second", Duration.ofSeconds(1), record(calls, "second"));

        StepVerifier.create(scheduler.advanceBy(Duration.ofSeconds(2))).verifyComplete();

        assertEquals(List.of("first", "second", "later"), calls);
        assertEquals(0, scheduler.pendingTaskCount());
    }

    @Test
    void replacesAndCancelsTasksByKey() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();
        scheduler.schedule("replace", Duration.ZERO, record(calls, "old"));
        scheduler.schedule("replace", Duration.ZERO, record(calls, "new"));
        scheduler.schedule("cancel", Duration.ZERO, record(calls, "cancelled"));

        assertTrue(scheduler.cancel("cancel"));
        assertFalse(scheduler.cancel("cancel"));
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();

        assertEquals(List.of("new"), calls);
    }

    @Test
    void executesTasksScheduledByDueActions() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        List<String> calls = new ArrayList<>();
        scheduler.schedule("outer", Duration.ZERO, () -> Mono.fromRunnable(() -> {
            calls.add("outer");
            scheduler.schedule("inner", Duration.ZERO, record(calls, "inner"));
        }));

        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();

        assertEquals(List.of("outer", "inner"), calls);
    }

    @Test
    void propagatesActionFailureAndLeavesLaterTaskPending() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        scheduler.schedule("failure", Duration.ZERO, () -> Mono.error(new TestException()));
        scheduler.schedule("later", Duration.ZERO, Mono::empty);

        StepVerifier.create(scheduler.runDueTasks()).expectError(TestException.class).verify();

        assertEquals(1, scheduler.pendingTaskCount());
        StepVerifier.create(scheduler.runDueTasks()).verifyComplete();
    }

    @Test
    void limitsCascadingTasks() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock, 2);
        scheduler.schedule("one", Duration.ZERO, () -> Mono.fromRunnable(() ->
                scheduler.schedule("two", Duration.ZERO, () -> Mono.fromRunnable(() ->
                        scheduler.schedule("three", Duration.ZERO, Mono::empty)))));

        StepVerifier.create(scheduler.runDueTasks())
                .expectErrorMessage("scheduled task cascade exceeded 2")
                .verify();

        assertEquals(1, scheduler.pendingTaskCount());
    }

    @Test
    void validatesArguments() {
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock());

        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("", Duration.ZERO, Mono::empty));
        assertThrows(IllegalArgumentException.class,
                () -> scheduler.schedule("task", Duration.ofSeconds(-1), Mono::empty));
        assertThrows(IllegalArgumentException.class,
                () -> new ManualBrokerScheduler(clock(), 0));
    }

    @Test
    void concurrentDrainDoesNotAdvanceClock() {
        MutableBrokerClock clock = clock();
        ManualBrokerScheduler scheduler = new ManualBrokerScheduler(clock);
        scheduler.schedule("never", Duration.ZERO, Mono::never);
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
