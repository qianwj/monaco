package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.transition.*;
import cn.elvis.monaco.core.store.SessionStore;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Central command processor — the execution function given to LocalDispatcher.
 * <p>
 * Routes each SessionCommand to the appropriate Transition, loads session state,
 * applies the transition, and executes the resulting actions.
 */
public class CommandProcessor implements Function<SessionCommand, Mono<CommandResult>> {

    private final SessionStore sessionStore;
    private final ActionExecutor actionExecutor;
    private final BrokerConfig config;

    private final ConnectTransition connectTransition = new ConnectTransition();
    private final DisconnectTransition disconnectTransition = new DisconnectTransition();
    private final PublishTransition publishTransition = new PublishTransition();
    private final SubscribeTransition subscribeTransition = new SubscribeTransition();
    private final UnsubscribeTransition unsubscribeTransition = new UnsubscribeTransition();
    private final PingReqTransition pingTransition = new PingReqTransition();

    public CommandProcessor(SessionStore sessionStore,
                            ActionExecutor actionExecutor,
                            BrokerConfig config) {
        this.sessionStore = sessionStore;
        this.actionExecutor = actionExecutor;
        this.config = config;
    }

    @Override
    public Mono<CommandResult> apply(SessionCommand sessionCommand) {
        var command = sessionCommand.command();
        var clientId = sessionCommand.clientId();

        return sessionStore.get(clientId)
                .flatMap(session -> {
                    var result = applyTransition(command, session);
                    return actionExecutor.execute(result)
                            .thenReturn((CommandResult) new CommandResult.Success(result));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No existing session — only CONNECT is valid without one
                    if (command instanceof Command.Connect) {
                        var result = applyTransition(command, null);
                        return actionExecutor.execute(result)
                                .thenReturn(new CommandResult.Success(result));
                    }
                    return Mono.just(new CommandResult.Rejected(
                            CommandResult.RejectReason.SESSION_NOT_FOUND,
                            "No session for clientId: " + clientId));
                }));
    }

    private TransitionResult applyTransition(
            Command command, SessionRecord session) {
        return switch (command) {
            case Command.Connect c -> connectTransition.apply(c, session, null, config);
            case Command.Disconnect c -> disconnectTransition.apply(c, session, null, config);
            case Command.Publish c -> publishTransition.apply(c, session, null, config);
            case Command.Subscribe c -> subscribeTransition.apply(c, session, null, config);
            case Command.Unsubscribe c -> unsubscribeTransition.apply(c, session, null, config);
            case Command.PingReq c -> pingTransition.apply(c, session, null, config);
            case Command.PubAck c -> TransitionResult.empty();
            case Command.PubRec c -> TransitionResult.empty();
            case Command.PubRel c -> TransitionResult.empty();
            case Command.PubComp c -> TransitionResult.empty();
        };
    }
}
