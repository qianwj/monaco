package cn.elvis.monaco.runtime.dispatch;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.state.ConnectionRef;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency proof for {@link ShardMailbox}.
 * <p>
 * Verifies:
 * 1. Serial execution — at most 1 active command at any time
 * 2. Overflow rejection — QUOTA_EXCEEDED when mailbox is full
 * 3. Dispose semantics — SERVER_SHUTTING_DOWN after dispose
 * 4. Error isolation — handler exception does not block drain loop
 * 5. Cancel safety — cancelled subscriber does not break subsequent commands
 */
class ShardMailboxTest {

    private static SessionCommand command(String clientId) {
        var ref = ConnectionRef.local("conn-1", 1);
        var cmd = new Command.PingReq(clientId, Instant.now());
        return SessionCommand.of(clientId, ref, cmd);
    }

    // ========== 1. Serial execution proof ==========

    @Test
    void serialExecution_activeCountNeverExceedsOne() throws InterruptedException {
        var mailbox = new ShardMailbox(64);
        var maxActive = new AtomicInteger(0);
        var active = new AtomicInteger(0);
        int totalCommands = 50;
        var latch = new CountDownLatch(totalCommands);

        // Handler that tracks concurrent active count
        var results = Collections.synchronizedList(new ArrayList<CommandResult>());

        for (int i = 0; i < totalCommands; i++) {
            mailbox.submit(command("client-" + i), cmd -> {
                int current = active.incrementAndGet();
                maxActive.accumulateAndGet(current, Math::max);
                return Mono.delay(Duration.ofMillis(5))
                        .map(_ -> {
                            active.decrementAndGet();
                            return (CommandResult) new CommandResult.Success(null);
                        });
            }).subscribe(r -> {
                results.add(r);
                latch.countDown();
            });
        }

        latch.await();
        assertEquals(totalCommands, results.size());
        assertEquals(1, maxActive.get(), "Active command count must never exceed 1");
    }

    // ========== 2. Overflow rejection ==========

    @Test
    void overflow_returnsQuotaExceeded() {
        // Capacity 1: concatMap prefetch(1) + buffer(1) = 2 slots before overflow
        var mailbox = new ShardMailbox(1);
        var blocker = new CountDownLatch(1);

        // Handler that never completes until latch released
        java.util.function.Function<SessionCommand, Mono<CommandResult>> blockingHandler = cmd ->
                Mono.create(sink -> new Thread(() -> {
                    try { blocker.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    sink.success(new CommandResult.Success(null));
                }).start());

        // Fill: subscribe without blocking on result
        mailbox.submit(command("c"), blockingHandler).subscribe();
        mailbox.submit(command("c"), blockingHandler).subscribe();

        // 3rd submit — the Mono.create callback runs synchronously,
        // tryEmitNext fails immediately, and sink.success is called with Rejected
        StepVerifier.create(mailbox.submit(command("c"), cmd ->
                        Mono.just(new CommandResult.Success(null))))
                .assertNext(result -> {
                    assertInstanceOf(CommandResult.Rejected.class, result);
                    var rejected = (CommandResult.Rejected) result;
                    assertEquals(CommandResult.RejectReason.QUOTA_EXCEEDED, rejected.reason());
                })
                .verifyComplete();

        blocker.countDown();
    }

    // ========== 3. Dispose semantics ==========

    @Test
    void dispose_subsequentSubmitReturnsShuttingDown() {
        var mailbox = new ShardMailbox(16);
        mailbox.dispose();

        StepVerifier.create(mailbox.submit(command("c"), cmd ->
                        Mono.just(new CommandResult.Success(null))))
                .assertNext(result -> {
                    assertInstanceOf(CommandResult.Rejected.class, result);
                    var rejected = (CommandResult.Rejected) result;
                    assertEquals(CommandResult.RejectReason.SERVER_SHUTTING_DOWN, rejected.reason());
                })
                .verifyComplete();
    }

    @Test
    void dispose_idempotent() {
        var mailbox = new ShardMailbox(16);
        mailbox.dispose();
        mailbox.dispose(); // no exception
    }

    // ========== 4. Error isolation ==========

    @Test
    void handlerException_doesNotBlockSubsequentCommands() throws InterruptedException {
        var mailbox = new ShardMailbox(16);
        var latch = new CountDownLatch(2);
        var results = Collections.synchronizedList(new ArrayList<CommandResult>());

        // First command throws
        mailbox.submit(command("c1"), cmd ->
                Mono.error(new RuntimeException("boom"))
        ).subscribe(r -> {
            results.add(r);
            latch.countDown();
        });

        // Second command should still execute
        mailbox.submit(command("c2"), cmd ->
                Mono.just(new CommandResult.Success(null))
        ).subscribe(r -> {
            results.add(r);
            latch.countDown();
        });

        latch.await();
        assertEquals(2, results.size());

        // First should be INTERNAL_ERROR rejection
        assertInstanceOf(CommandResult.Rejected.class, results.get(0));
        assertEquals(CommandResult.RejectReason.INTERNAL_ERROR,
                ((CommandResult.Rejected) results.get(0)).reason());

        // Second should succeed
        assertInstanceOf(CommandResult.Success.class, results.get(1));
    }

    // ========== 5. Cancel safety ==========

    @Test
    void cancelledSubscriber_doesNotBreakDrainLoop() throws InterruptedException {
        var mailbox = new ShardMailbox(16);
        var latch = new CountDownLatch(1);

        // Submit and immediately cancel
        var disposable = mailbox.submit(command("c1"), cmd ->
                Mono.delay(Duration.ofMillis(50))
                        .map(_ -> (CommandResult) new CommandResult.Success(null))
        ).subscribe();
        disposable.dispose(); // cancel

        // Subsequent command should still work
        mailbox.submit(command("c2"), cmd ->
                Mono.just(new CommandResult.Success(null))
        ).subscribe(r -> {
            assertInstanceOf(CommandResult.Success.class, r);
            latch.countDown();
        });

        assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS),
                "Drain loop must continue after cancellation");
    }
}
