package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.port.BrokerClock;
import reactor.core.publisher.Mono;

public class PacketRouter {

    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final BrokerClock clock;

    private final PublishHandler publishHandler;
    private final SubscribeHandler subscribeHandler;
    private final UnsubscribeHandler unsubscribeHandler;
    private final DisconnectHandler disconnectHandler;

    public PacketRouter(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            BrokerClock clock,
            PublishHandler publishHandler,
            SubscribeHandler subscribeHandler,
            UnsubscribeHandler unsubscribeHandler,
            DisconnectHandler disconnectHandler) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.clock = clock;
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
        var ref = ConnectionRef.local(clientId, 0);
        var command = new Command.PingReq(clientId, clock.now());
        var sessionCommand = SessionCommand.of(clientId, ref, command);
        return dispatcher.dispatch(sessionCommand).then();
    }

    private Mono<Void> handleAck(String clientId) {
        return Mono.empty(); // TODO: QoS ACK handling
    }

    private Mono<String> resolveClientId(ConnectionId connectionId) {
        return Mono.justOrEmpty(registry.clientIdForConnection(connectionId));
    }
}
