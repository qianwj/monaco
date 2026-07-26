package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.transition.TransitionResult;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.port.BrokerScheduler;
import cn.elvis.monaco.runtime.store.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class ActionExecutor {

    private final ConnectionRegistry registry;
    private final SessionStore sessionStore;
    private final SubscriptionStore subscriptionStore;
    private final FlightWindowStore flightWindowStore;
    private final WillStore willStore;
    private final BrokerScheduler scheduler;

    public ActionExecutor(
            ConnectionRegistry registry,
            SessionStore sessionStore,
            SubscriptionStore subscriptionStore,
            FlightWindowStore flightWindowStore,
            WillStore willStore,
            BrokerScheduler scheduler) {
        this.registry = registry;
        this.sessionStore = sessionStore;
        this.subscriptionStore = subscriptionStore;
        this.flightWindowStore = flightWindowStore;
        this.willStore = willStore;
        this.scheduler = scheduler;
    }

    public Mono<Void> execute(TransitionResult result) {
        return Flux.fromIterable(result.actions())
                .concatMap(this::executeAction)
                .then()
                .then(result.sessionRecord() != null
                        ? sessionStore.save(result.sessionRecord())
                        : Mono.empty());
    }

    private Mono<Void> executeAction(Action action) {
        return switch (action) {
            case Action.SendPacket a -> registry.sendToConnection(a.target().connectionId(), a.packet());
            case Action.CloseConnection a -> registry.closeByConnectionId(a.target().connectionId());
            case Action.DeliverMessage a -> registry.sendToConnection(a.target().connectionId(), a.publish());

            case Action.StartKeepAliveTimer a ->
                    scheduler.schedule("keepalive:" + a.clientId(),
                            Duration.ofSeconds((long) (a.keepAliveSeconds() * 1.5)), () -> {});
            case Action.ResetKeepAliveTimer a ->
                    scheduler.cancel("keepalive:" + a.clientId());

            case Action.PersistSession a -> Mono.empty(); // batch-saved after all actions
            case Action.RemoveSession a -> sessionStore.remove(a.clientId());

            case Action.ScheduleSessionExpiry a ->
                    scheduler.schedule("session-expiry:" + a.clientId(),
                            Duration.ofSeconds(a.expirySeconds()), () -> {});
            case Action.CancelSessionExpiry a ->
                    scheduler.cancel("session-expiry:" + a.clientId());

            case Action.PersistWill a -> willStore.save(a.willRecord());
            case Action.RemoveWill a -> willStore.remove(a.clientId());
            case Action.ScheduleWillPublish a ->
                    scheduler.schedule("will:" + a.clientId(),
                            Duration.ofSeconds(a.delaySeconds()), () -> {});
            case Action.PublishWill a -> Mono.empty(); // TODO: wire to publish pipeline

            case Action.PersistSubscriptions a ->
                    subscriptionStore.save(a.clientId(), a.records());
            case Action.RemoveSubscriptions a -> Mono.empty(); // TODO
            case Action.ClearAllSubscriptions a -> subscriptionStore.remove(a.clientId());

            case Action.PersistInflight a -> flightWindowStore.add(a.record().clientId(), a.record());
            case Action.RemoveInflight a ->
                    flightWindowStore.remove(a.clientId(), a.direction(), a.packetId());
            case Action.ClearAllInflight a -> Mono.empty(); // TODO
        };
    }
}
