package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.transition.TransitionResult;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.port.BrokerScheduler;
import cn.elvis.monaco.core.store.StateTransaction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes transition results with persist-before-send guarantee.
 * <p>
 * Flow: partition actions → commit mutations atomically → execute effects.
 * Network packets and timers only fire after state is durably persisted.
 */
public class ActionExecutor {

    private final ConnectionRegistry registry;
    private final StateTransaction stateTransaction;
    private final BrokerScheduler scheduler;

    public ActionExecutor(
            ConnectionRegistry registry,
            StateTransaction stateTransaction,
            BrokerScheduler scheduler) {
        this.registry = registry;
        this.stateTransaction = stateTransaction;
        this.scheduler = scheduler;
    }

    public Mono<Void> execute(TransitionResult result) {
        var partitioned = partition(result.actions());

        // 1. Persist all state mutations atomically
        return stateTransaction.commit(result.sessionRecord(), partitioned.mutations)
                // 2. Only after persistence succeeds, execute effects
                .then(executeEffects(partitioned.effects));
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
            // Network effects — only after persist
            case Action.SendPacket a ->
                    registry.sendToConnection(a.target().connectionId(), a.packet());
            case Action.CloseConnection a ->
                    registry.closeByConnectionId(a.target().connectionId());
            case Action.DeliverMessage a ->
                    registry.sendToConnection(a.target().connectionId(), a.publish());

            // Timer effects
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

            // Mutations — should not reach here, but handle gracefully
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
}
