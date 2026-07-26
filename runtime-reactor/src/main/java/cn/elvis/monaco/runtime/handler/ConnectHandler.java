package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.transition.ConnectTransition;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.connection.ConnectionRegistry;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.port.BrokerClock;
import cn.elvis.monaco.runtime.port.IdGenerator;
import cn.elvis.monaco.runtime.store.SessionStore;
import reactor.core.publisher.Mono;

public class ConnectHandler {

    private final ConnectTransition transition = new ConnectTransition();
    private final ConnectionRegistry registry;
    private final CommandDispatcher dispatcher;
    private final SessionStore sessionStore;
    private final ActionExecutor actionExecutor;
    private final BrokerClock clock;
    private final IdGenerator idGenerator;
    private final BrokerConfig config;

    public ConnectHandler(
            ConnectionRegistry registry,
            CommandDispatcher dispatcher,
            SessionStore sessionStore,
            ActionExecutor actionExecutor,
            BrokerClock clock,
            IdGenerator idGenerator,
            BrokerConfig config) {
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.sessionStore = sessionStore;
        this.actionExecutor = actionExecutor;
        this.clock = clock;
        this.idGenerator = idGenerator;
        this.config = config;
    }

    public Mono<Void> handle(ConnectionId connectionId, ClientPacket.Connect packet) {
        String clientId = packet.clientId().isEmpty() ? idGenerator.nextId() : packet.clientId();

        return dispatcher.dispatch(clientId, cid -> {
            var command = new Command.Connect(packet, cid, clock.now());
            return sessionStore.get(cid)
                    .flatMap(existing -> {
                        var result = transition.apply(command, existing, null, config);
                        registry.bindClient(connectionId, cid);
                        return actionExecutor.execute(result);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        var result = transition.apply(command, null, null, config);
                        registry.bindClient(connectionId, cid);
                        return actionExecutor.execute(result);
                    }));
        });
    }
}
