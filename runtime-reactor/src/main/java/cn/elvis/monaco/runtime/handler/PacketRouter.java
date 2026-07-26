package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.transition.PingReqTransition;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.port.BrokerClock;
import cn.elvis.monaco.runtime.store.SessionStore;
import reactor.core.publisher.Mono;

public class PacketRouter {

    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final SessionStore sessionStore;
    private final ActionExecutor actionExecutor;
    private final BrokerClock clock;
    private final BrokerConfig config;

    private final PublishHandler publishHandler;
    private final SubscribeHandler subscribeHandler;
    private final UnsubscribeHandler unsubscribeHandler;
    private final DisconnectHandler disconnectHandler;

    private final PingReqTransition pingTransition = new PingReqTransition();

    public PacketRouter(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            SessionStore sessionStore,
            ActionExecutor actionExecutor,
            BrokerClock clock,
            BrokerConfig config,
            PublishHandler publishHandler,
            SubscribeHandler subscribeHandler,
            UnsubscribeHandler unsubscribeHandler,
            DisconnectHandler disconnectHandler) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.sessionStore = sessionStore;
        this.actionExecutor = actionExecutor;
        this.clock = clock;
        this.config = config;
        this.publishHandler = publishHandler;
        this.subscribeHandler = subscribeHandler;
        this.unsubscribeHandler = unsubscribeHandler;
        this.disconnectHandler = disconnectHandler;
    }

    public Mono<Void> route(ConnectionId connectionId, ClientPacket packet) {
        return resolveClientId(connectionId)
                .flatMap(clientId -> routeToHandler(clientId, packet));
    }

    private Mono<Void> routeToHandler(String clientId, ClientPacket packet) {
        return switch (packet) {
            case ClientPacket.Publish pub -> publishHandler.handle(clientId, pub);
            case ClientPacket.Subscribe sub -> subscribeHandler.handle(clientId, sub);
            case ClientPacket.Unsubscribe unsub -> unsubscribeHandler.handle(clientId, unsub);
            case ClientPacket.Disconnect disc -> disconnectHandler.handle(clientId, disc);
            case ClientPacket.PingReq ping -> handlePing(clientId);
            case ClientPacket.PubAck ack -> handleAck(clientId);
            case ClientPacket.PubRec rec -> handleAck(clientId);
            case ClientPacket.PubRel rel -> handleAck(clientId);
            case ClientPacket.PubComp comp -> handleAck(clientId);
            case ClientPacket.Auth auth -> Mono.empty(); // TODO: Enhanced AUTH
            default -> Mono.empty();
        };
    }

    private Mono<Void> handlePing(String clientId) {
        return dispatcher.dispatch(clientId, cid ->
                sessionStore.get(cid)
                        .flatMap(session -> {
                            var command = new Command.PingReq(cid, clock.now());
                            var result = pingTransition.apply(command, session, null, config);
                            return actionExecutor.execute(result);
                        })
        );
    }

    private Mono<Void> handleAck(String clientId) {
        return dispatcher.dispatch(clientId, cid -> Mono.empty()); // TODO: QoS ACK handling
    }

    private Mono<String> resolveClientId(ConnectionId connectionId) {
        return Mono.justOrEmpty(registry.clientIdForConnection(connectionId));
    }
}
