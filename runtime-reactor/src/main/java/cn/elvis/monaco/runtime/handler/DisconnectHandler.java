package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.transition.DisconnectTransition;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.protocol.reason.ReasonCode;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.engine.DisconnectCause;
import cn.elvis.monaco.runtime.port.BrokerClock;
import cn.elvis.monaco.runtime.store.SessionStore;
import reactor.core.publisher.Mono;

public class DisconnectHandler {

    private final DisconnectTransition transition = new DisconnectTransition();
    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final SessionStore sessionStore;
    private final ActionExecutor actionExecutor;
    private final BrokerClock clock;
    private final BrokerConfig config;

    public DisconnectHandler(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            SessionStore sessionStore,
            ActionExecutor actionExecutor,
            BrokerClock clock,
            BrokerConfig config) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.sessionStore = sessionStore;
        this.actionExecutor = actionExecutor;
        this.clock = clock;
        this.config = config;
    }

    public Mono<Void> handle(String clientId, ClientPacket.Disconnect packet) {
        return dispatcher.dispatch(clientId, cid ->
                sessionStore.get(cid)
                        .flatMap(session -> {
                            var command = new Command.Disconnect(packet, cid, clock.now());
                            var result = transition.apply(command, session, null, config);
                            return actionExecutor.execute(result);
                        })
        );
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
