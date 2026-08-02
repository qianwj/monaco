package cn.elvis.monaco.runtime.session;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.port.BrokerStore;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.transition.*;
import cn.elvis.monaco.runtime.connection.LogicalConnectionRegistry;
import cn.elvis.monaco.runtime.handler.ActionExecutor;
import cn.elvis.monaco.protocol.model.ConnectionId;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Session-scoped command processor — the execution function given to the dispatcher.
 * <p>
 * For each command:
 * 1. Load shard snapshot from BrokerStore
 * 2. Validate ConnectionRef generation (reject stale commands from taken-over connections)
 * 3. Resolve LogicalConnection for connection-level constraints
 * 4. Apply pure domain Transition
 * 5. Delegate to ActionExecutor for atomic persist + effect execution
 * <p>
 * This processor runs inside a ShardMailbox, so commands for the same clientId
 * are guaranteed to execute serially.
 */
public class SessionProcessor implements Function<SessionCommand, Mono<CommandResult>> {

    private final BrokerStore brokerStore;
    private final ActionExecutor actionExecutor;
    private final LogicalConnectionRegistry logicalRegistry;
    private final ProtocolLimits limits;

    private final ConnectTransition connectTransition = new ConnectTransition();
    private final DisconnectTransition disconnectTransition = new DisconnectTransition();
    private final PublishTransition publishTransition = new PublishTransition();
    private final SubscribeTransition subscribeTransition = new SubscribeTransition();
    private final UnsubscribeTransition unsubscribeTransition = new UnsubscribeTransition();
    private final PingReqTransition pingTransition = new PingReqTransition();

    public SessionProcessor(BrokerStore brokerStore,
                            ActionExecutor actionExecutor,
                            LogicalConnectionRegistry logicalRegistry,
                            ProtocolLimits limits) {
        this.brokerStore = brokerStore;
        this.actionExecutor = actionExecutor;
        this.logicalRegistry = logicalRegistry;
        this.limits = limits;
    }

    @Override
    public Mono<CommandResult> apply(SessionCommand sessionCommand) {
        var clientId = sessionCommand.clientId();
        var connectionRef = sessionCommand.connectionRef();
        var command = sessionCommand.command();

        return brokerStore.load(clientId)
                .flatMap(snapshot -> processCommand(clientId, connectionRef, command, snapshot));
    }

    private Mono<CommandResult> processCommand(String clientId,
                                                ConnectionRef connectionRef,
                                                Command command,
                                                ShardSnapshot snapshot) {
        var session = snapshot.session();

        // CONNECT is valid without an existing session
        if (session == null && !(command instanceof Command.Connect)) {
            return Mono.just(new CommandResult.Rejected(
                    CommandResult.RejectReason.SESSION_NOT_FOUND,
                    "No session for clientId: " + clientId));
        }

        // Generation check — reject stale commands from taken-over connections
        if (session != null && session.activeBinding() != null
                && !(command instanceof Command.Connect)) {
            var activeBinding = session.activeBinding();
            if (!connectionRef.connectionId().equals(activeBinding.connectionId())
                    || connectionRef.generation() < session.connectionGeneration()) {
                return Mono.just(new CommandResult.Rejected(
                        CommandResult.RejectReason.STALE_CONNECTION,
                        "Connection generation stale: " + connectionRef.generation()
                                + " < " + session.connectionGeneration()));
            }
        }

        // Resolve LogicalConnection for connection-level constraints
        var logical = logicalRegistry
                .get(new ConnectionId(connectionRef.connectionId()))
                .orElse(null);

        var result = applyTransition(command, session, logical);
        long expectedRevision = session != null ? session.revision() : 0;

        return actionExecutor.execute(clientId, expectedRevision, result)
                .thenReturn((CommandResult) new CommandResult.Success(result));
    }

    private TransitionResult applyTransition(Command command,
                                              SessionRecord session,
                                              LogicalConnection logical) {
        return switch (command) {
            case Command.Connect c -> connectTransition.apply(c, session, logical, limits);
            case Command.Disconnect c -> disconnectTransition.apply(c, session, logical, limits);
            case Command.Publish c -> publishTransition.apply(c, session, logical, limits);
            case Command.Subscribe c -> subscribeTransition.apply(c, session, logical, limits);
            case Command.Unsubscribe c -> unsubscribeTransition.apply(c, session, logical, limits);
            case Command.PingReq c -> pingTransition.apply(c, session, logical, limits);
            case Command.PubAck c -> TransitionResult.empty();
            case Command.PubRec c -> TransitionResult.empty();
            case Command.PubRel c -> TransitionResult.empty();
            case Command.PubComp c -> TransitionResult.empty();
        };
    }
}
