package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.store.FlightWindowStore;
import cn.elvis.monaco.core.store.SessionStore;
import cn.elvis.monaco.core.store.StateTransaction;
import cn.elvis.monaco.core.store.SubscriptionStore;
import cn.elvis.monaco.core.store.WillStore;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * In-memory StateTransaction for standalone mode.
 * <p>
 * Applies all mutations synchronously in a single thread-confined call,
 * providing atomicity through the session mailbox's serial execution guarantee.
 */
public class InMemoryStateTransaction implements StateTransaction {

    private final SessionStore sessionStore;
    private final SubscriptionStore subscriptionStore;
    private final FlightWindowStore flightWindowStore;
    private final WillStore willStore;

    public InMemoryStateTransaction(
            SessionStore sessionStore,
            SubscriptionStore subscriptionStore,
            FlightWindowStore flightWindowStore,
            WillStore willStore) {
        this.sessionStore = sessionStore;
        this.subscriptionStore = subscriptionStore;
        this.flightWindowStore = flightWindowStore;
        this.willStore = willStore;
    }

    @Override
    public Mono<Void> commit(SessionRecord sessionRecord, List<Action> mutations) {
        // Apply all mutations, then persist session record last
        Mono<Void> applyMutations = Mono.empty();

        for (var action : mutations) {
            applyMutations = applyMutations.then(applyMutation(action));
        }

        // Session record persisted as part of the atomic batch
        if (sessionRecord != null) {
            applyMutations = applyMutations.then(sessionStore.save(sessionRecord));
        }

        return applyMutations;
    }

    private Mono<Void> applyMutation(Action action) {
        return switch (action) {
            case Action.PersistSession a -> Mono.empty(); // handled via sessionRecord param
            case Action.RemoveSession a -> sessionStore.remove(a.clientId());
            case Action.PersistWill a -> willStore.save(a.willRecord());
            case Action.RemoveWill a -> willStore.remove(a.clientId());
            case Action.PersistSubscriptions a ->
                    subscriptionStore.save(a.clientId(), a.records());
            case Action.RemoveSubscriptions a ->
                    subscriptionStore.remove(a.clientId(), a.topicFilters());
            case Action.ClearAllSubscriptions a -> subscriptionStore.remove(a.clientId());
            case Action.PersistInflight a ->
                    flightWindowStore.add(a.record().clientId(), a.record());
            case Action.RemoveInflight a ->
                    flightWindowStore.remove(a.clientId(), a.direction(), a.packetId());
            case Action.ClearAllInflight a -> flightWindowStore.clear(a.clientId());
            // Non-mutation actions should not reach here
            default -> Mono.empty();
        };
    }
}
