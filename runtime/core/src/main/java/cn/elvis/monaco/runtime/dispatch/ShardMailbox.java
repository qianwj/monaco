package cn.elvis.monaco.runtime.dispatch;

import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * A bounded, serial mailbox for a single shard lane.
 * <p>
 * Commands are enqueued into a Sinks.Many with bounded capacity.
 * A single drain loop (concatMap) guarantees that only one command
 * executes at a time — the next command starts only after the
 * previous Mono completes.
 */
public class ShardMailbox {

    private final Sinks.Many<Envelope> inbox;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public ShardMailbox(int capacity) {
        this.inbox = Sinks.many().multicast().onBackpressureBuffer(capacity, false);

        // Single drain loop — concatMap ensures serial execution
        inbox.asFlux()
                .concatMap(envelope -> envelope.execute()
                        .doOnSuccess(envelope::complete)
                        .doOnError(envelope::fail)
                        .onErrorComplete())
                .subscribe();
    }

    public Mono<CommandResult> submit(SessionCommand command,
                               Function<SessionCommand, Mono<CommandResult>> handler) {
        if (disposed.get()) {
            return Mono.just(new CommandResult.Rejected(
                    CommandResult.RejectReason.SERVER_SHUTTING_DOWN,
                    "Shard disposed"));
        }

        return Mono.create(sink -> {
            var envelope = new Envelope(command, handler, sink);
            Sinks.EmitResult result = inbox.tryEmitNext(envelope);
            if (result.isFailure()) {
                sink.success(new CommandResult.Rejected(
                        CommandResult.RejectReason.QUOTA_EXCEEDED,
                        "Mailbox full: " + result));
            }
        });
    }

    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            inbox.tryEmitComplete();
        }
    }

    private static class Envelope {
        private final SessionCommand command;
        private final Function<SessionCommand, Mono<CommandResult>> handler;
        private final reactor.core.publisher.MonoSink<CommandResult> sink;

        Envelope(SessionCommand command,
                 Function<SessionCommand, Mono<CommandResult>> handler,
                 reactor.core.publisher.MonoSink<CommandResult> sink) {
            this.command = command;
            this.handler = handler;
            this.sink = sink;
        }

        Mono<CommandResult> execute() {
            return handler.apply(command);
        }

        void complete(CommandResult result) {
            sink.success(result);
        }

        void fail(Throwable t) {
            sink.success(new CommandResult.Rejected(
                    CommandResult.RejectReason.INTERNAL_ERROR,
                    t.getMessage()));
        }
    }
}
