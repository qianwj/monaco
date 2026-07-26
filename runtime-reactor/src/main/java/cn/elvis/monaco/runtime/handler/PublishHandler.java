package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.transition.PublishTransition;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.port.BrokerClock;
import cn.elvis.monaco.runtime.store.SessionStore;
import reactor.core.publisher.Mono;

public class PublishHandler {

    private final PublishTransition transition = new PublishTransition();
    private final CommandDispatcher dispatcher;
    private final SessionStore sessionStore;
    private final ActionExecutor actionExecutor;
    private final BrokerClock clock;
    private final BrokerConfig config;

    public PublishHandler(
            CommandDispatcher dispatcher,
            SessionStore sessionStore,
            ActionExecutor actionExecutor,
            BrokerClock clock,
            BrokerConfig config) {
        this.dispatcher = dispatcher;
        this.sessionStore = sessionStore;
        this.actionExecutor = actionExecutor;
        this.clock = clock;
        this.config = config;
    }

    public Mono<Void> handle(String clientId, ClientPacket.Publish packet) {
        return dispatcher.dispatch(clientId, cid ->
                sessionStore.get(cid)
                        .flatMap(session -> {
                            var command = new Command.Publish(packet, cid, clock.now());
                            var result = transition.apply(command, session, null, config);
                            return actionExecutor.execute(result);
                        })
        );
    }
}
