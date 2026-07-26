package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.reason.ReasonCode;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.engine.DisconnectCause;
import cn.elvis.monaco.runtime.port.BrokerClock;
import reactor.core.publisher.Mono;

public class DisconnectHandler {

    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final BrokerClock clock;

    public DisconnectHandler(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            BrokerClock clock) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.clock = clock;
    }

    public Mono<Void> handle(String clientId, ClientPacket.Disconnect packet) {
        var ref = ConnectionRef.local(clientId, 0);
        var command = new Command.Disconnect(packet, clientId, clock.now());
        var sessionCommand = SessionCommand.of(clientId, ref, command);
        return dispatcher.dispatch(sessionCommand).then();
    }

    public Mono<Void> handleClose(ConnectionId connectionId, DisconnectCause cause) {
        return Mono.justOrEmpty(registry.clientIdForConnection(connectionId))
                .flatMap(clientId -> {
                    var packet = new ClientPacket.Disconnect(mapCause(cause), null);
                    return handle(clientId, packet);
                });
    }

    private ReasonCode mapCause(DisconnectCause cause) {
        return switch (cause) {
            case CLIENT_DISCONNECT -> ReasonCode.NORMAL_DISCONNECTION;
            case PROTOCOL_ERROR -> ReasonCode.PROTOCOL_ERROR;
            case KEEP_ALIVE_TIMEOUT -> ReasonCode.KEEP_ALIVE_TIMEOUT;
            case SESSION_TAKEN_OVER -> ReasonCode.SESSION_TAKEN_OVER;
            case SERVER_SHUTDOWN -> ReasonCode.SERVER_SHUTTING_DOWN;
            case NETWORK_ERROR -> ReasonCode.UNSPECIFIED_ERROR;
        };
    }
}
