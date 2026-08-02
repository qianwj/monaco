package cn.elvis.monaco.runtime.handler;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.transition.*;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Central command processor — the execution function given to LocalDispatcher.
 * <p>
 * Loads shard state from BrokerStore, applies the domain transition,
 * then delegates to ActionExecutor for atomic persist + effect execution.
 */
public class CommandProcessor implements Function<SessionCommand, Mono<CommandResult>> {

    private final BrokerStore brokerStore;
    private final ActionExecutor actionExecutor;
    private final ProtocolLimits limits;

    private final ConnectTransition connectTransition = new ConnectTransition();
    private final DisconnectTransition disconnectTransition = new DisconnectTransition();
    private final PublishTransition publishTransition = new PublishTransition();
    private final SubscribeTransition subscribeTransition = new SubscribeTransition();
    private final UnsubscribeTransition unsubscribeTransition = new UnsubscribeTransition();
    private final PingReqTransition pingTransition = new PingReqTransition();

    public CommandProcessor(BrokerStore brokerStore,
                            ActionExecutor actionExecutor,
                            ProtocolLimits limits) {
        this.brokerStore = brokerStore;
        this.actionExecutor = actionExecutor;
        this.limits = limits;
    }

    @Override
    public Mono<CommandResult> apply(SessionCommand sessionCommand) {
        var command = sessionCommand.command();
        var clientId = sessionCommand.clientId();

        return brokerStore.load(clientId)
                .flatMap(snapshot -> processCommand(clientId, command, snapshot));
    }

    private Mono<CommandResult> processCommand(String clientId, Command command, ShardSnapshot snapshot) {
        var session = snapshot.session();

        // Only CONNECT is valid without an existing session
        if (session == null && !(command instanceof Command.Connect)) {
            return Mono.just(new CommandResult.Rejected(
                    CommandResult.RejectReason.SESSION_NOT_FOUND,
                    "No session for clientId: " + clientId));
        }

        var result = applyTransition(command, session);
        long expectedRevision = session != null ? session.revision() : 0;

        return actionExecutor.execute(clientId, expectedRevision, result)
                .thenReturn((CommandResult) new CommandResult.Success(result));
    }

    private TransitionResult applyTransition(Command command, SessionRecord session) {
        return switch (command) {
            case Command.Connect c -> connectTransition.apply(c, session, null, limits);
            case Command.Disconnect c -> disconnectTransition.apply(c, session, null, limits);
            case Command.Publish c -> publishTransition.apply(c, session, null, limits);
            case Command.Subscribe c -> subscribeTransition.apply(c, session, null, limits);
            case Command.Unsubscribe c -> unsubscribeTransition.apply(c, session, null, limits);
            case Command.PingReq c -> pingTransition.apply(c, session, null, limits);
            case Command.PubAck c -> TransitionResult.empty();
            case Command.PubRec c -> TransitionResult.empty();
            case Command.PubRel c -> TransitionResult.empty();
            case Command.PubComp c -> TransitionResult.empty();
        };
    }
}
