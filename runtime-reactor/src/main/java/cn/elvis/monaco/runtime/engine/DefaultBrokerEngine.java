package cn.elvis.monaco.runtime.engine;

import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.connection.ConnectionHandle;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.handler.ConnectHandler;
import cn.elvis.monaco.runtime.handler.DisconnectHandler;
import cn.elvis.monaco.runtime.handler.PacketRouter;
import reactor.core.publisher.Mono;

public class DefaultBrokerEngine implements BrokerEngine {

    private final ConnectionRegistry registry;
    private final ConnectHandler connectHandler;
    private final DisconnectHandler disconnectHandler;
    private final PacketRouter packetRouter;

    public DefaultBrokerEngine(
            ConnectionRegistry registry,
            ConnectHandler connectHandler,
            DisconnectHandler disconnectHandler,
            PacketRouter packetRouter) {
        this.registry = registry;
        this.connectHandler = connectHandler;
        this.disconnectHandler = disconnectHandler;
        this.packetRouter = packetRouter;
    }

    @Override
    public Mono<Void> opened(ConnectionHandle connection) {
        registry.register(connection, null);
        return Mono.empty();
    }

    @Override
    public Mono<Void> received(ConnectionId connectionId, ClientPacket packet) {
        return switch (packet) {
            case ClientPacket.Connect connect -> connectHandler.handle(connectionId, connect);
            default -> packetRouter.route(connectionId, packet);
        };
    }

    @Override
    public Mono<Void> closed(ConnectionId connectionId, DisconnectCause cause) {
        return disconnectHandler.handleClose(connectionId, cause)
                .doFinally(signal -> registry.unregister(connectionId));
    }
}
