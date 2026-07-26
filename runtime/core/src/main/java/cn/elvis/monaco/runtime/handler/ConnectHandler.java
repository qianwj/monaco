package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.core.port.BrokerClock;
import cn.elvis.monaco.core.port.IdGenerator;
import reactor.core.publisher.Mono;

import cn.elvis.monaco.protocol.packet.ClientPacket;

public class ConnectHandler {

    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final IdGenerator idGenerator;
    private final BrokerClock clock;

    public ConnectHandler(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            IdGenerator idGenerator,
            BrokerClock clock) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    public Mono<Void> handle(ConnectionId connectionId, ClientPacket.Connect packet) {
        String clientId = packet.clientId().isEmpty() ? idGenerator.nextId() : packet.clientId();
        var ref = ConnectionRef.local(connectionId.value(), 0);
        var command = new Command.Connect(packet, clientId, clock.now());
        var sessionCommand = SessionCommand.of(clientId, ref, command);

        return dispatcher.dispatch(sessionCommand)
                .doOnNext(result -> {
                    if (result instanceof CommandResult.Success) {
                        registry.bindClient(connectionId, clientId);
                    }
                })
                .then();
    }
}
