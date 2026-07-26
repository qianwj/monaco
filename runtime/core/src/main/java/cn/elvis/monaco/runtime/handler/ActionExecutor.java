package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.port.*;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.transition.TransitionResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes transition results with persist-before-send guarantee.
 * <p>
 * Flow: partition actions → build MutationBatch → BrokerStore.commit → execute effects.
 * Network packets and timers only fire after state is durably persisted.
 */
public class ActionExecutor {

    private final ConnectionSink connectionSink;
    private final BrokerStore brokerStore;
    private final BrokerScheduler scheduler;

    public ActionExecutor(
            ConnectionSink connectionSink,
            BrokerStore brokerStore,
            BrokerScheduler scheduler) {
        this.connectionSink = connectionSink;
        this.brokerStore = brokerStore;
        this.scheduler = scheduler;
    }

    public Mono<Void> execute(String clientId, long expectedRevision, TransitionResult result) {
        var partitioned = partition(result.actions());
        var mutations = buildMutations(result.sessionRecord(), partitioned.mutations);
        var commit = StoreCommit.of(clientId, expectedRevision, mutations);

        // 1. Persist all state mutations atomically
        return brokerStore.commit(commit)
                .flatMap(commitResult -> switch (commitResult) {
                    case CommitResult.Success s -> executeEffects(partitioned.effects);
                    case CommitResult.ConflictRevision c -> Mono.error(
                            new RevisionConflictException(clientId, expectedRevision, c.actualRevision()));
                    case CommitResult.StoreError e -> Mono.error(
                            new StoreCommitException(clientId, e.message(), e.cause()));
                });
    }

    private MutationBatch buildMutations(SessionRecord session, List<Action> mutationActions) {
        var builder = MutationBatch.builder();

        for (var action : mutationActions) {
            var mutation = toMutation(action);
            if (mutation != null) {
                builder.add(mutation);
            }
        }

        // Session record is always part of the batch
        if (session != null) {
            builder.add(new Mutation.SaveSession(session));
        }

        return builder.build();
    }

    private Mutation toMutation(Action action) {
        return switch (action) {
            case Action.PersistSession a -> null; // handled via sessionRecord
            case Action.RemoveSession a -> new Mutation.RemoveSession(a.clientId());
            case Action.PersistWill a -> new Mutation.SaveWill(a.willRecord());
            case Action.RemoveWill a -> new Mutation.RemoveWill(a.clientId());
            case Action.PersistSubscriptions a ->
                    new Mutation.SaveSubscriptions(a.clientId(), a.records());
            case Action.RemoveSubscriptions a ->
                    new Mutation.RemoveSubscriptions(a.clientId(), a.topicFilters());
            case Action.ClearAllSubscriptions a -> new Mutation.ClearSubscriptions(a.clientId());
            case Action.PersistInflight a ->
                    new Mutation.AddInflight(a.record().clientId(), a.record());
            case Action.RemoveInflight a ->
                    new Mutation.RemoveInflight(a.clientId(), a.direction(), a.packetId());
            case Action.ClearAllInflight a -> new Mutation.ClearInflight(a.clientId());
            default -> null;
        };
    }

    private Mono<Void> executeEffects(List<Action> effects) {
        if (effects.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(effects)
                .concatMap(this::executeEffect)
                .then();
    }

    private Mono<Void> executeEffect(Action action) {
        return switch (action) {
            case Action.SendPacket a -> connectionSink.send(a.target(), a.packet());
            case Action.CloseConnection a -> connectionSink.close(a.target());
            case Action.DeliverMessage a -> connectionSink.send(a.target(), a.publish());

            case Action.StartKeepAliveTimer a ->
                    scheduler.schedule("keepalive:" + a.clientId(),
                            Duration.ofSeconds((long) (a.keepAliveSeconds() * 1.5)), () -> {});
            case Action.ResetKeepAliveTimer a ->
                    scheduler.cancel("keepalive:" + a.clientId());
            case Action.ScheduleSessionExpiry a ->
                    scheduler.schedule("session-expiry:" + a.clientId(),
                            Duration.ofSeconds(a.expirySeconds()), () -> {});
            case Action.CancelSessionExpiry a ->
                    scheduler.cancel("session-expiry:" + a.clientId());
            case Action.ScheduleWillPublish a ->
                    scheduler.schedule("will:" + a.clientId(),
                            Duration.ofSeconds(a.delaySeconds()), () -> {});
            case Action.PublishWill a -> Mono.empty(); // TODO: wire to publish pipeline

            default -> Mono.empty();
        };
    }

    private static PartitionedActions partition(List<Action> actions) {
        var mutations = new ArrayList<Action>();
        var effects = new ArrayList<Action>();

        for (var action : actions) {
            if (isMutation(action)) {
                mutations.add(action);
            } else {
                effects.add(action);
            }
        }
        return new PartitionedActions(mutations, effects);
    }

    private static boolean isMutation(Action action) {
        return action instanceof Action.PersistSession
                || action instanceof Action.RemoveSession
                || action instanceof Action.PersistWill
                || action instanceof Action.RemoveWill
                || action instanceof Action.PersistSubscriptions
                || action instanceof Action.RemoveSubscriptions
                || action instanceof Action.ClearAllSubscriptions
                || action instanceof Action.PersistInflight
                || action instanceof Action.RemoveInflight
                || action instanceof Action.ClearAllInflight;
    }

    private record PartitionedActions(List<Action> mutations, List<Action> effects) {}

    public static class RevisionConflictException extends RuntimeException {
        public RevisionConflictException(String clientId, long expected, long actual) {
            super("Revision conflict for " + clientId + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static class StoreCommitException extends RuntimeException {
        public StoreCommitException(String clientId, String message, Throwable cause) {
            super("Store commit failed for " + clientId + ": " + message, cause);
        }
    }
}
