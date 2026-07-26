package cn.elvis.monaco.runtime.connection;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.core.port.BrokerClock;
import cn.elvis.monaco.core.port.IdGenerator;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-physical-connection processor that ensures packet ordering via serial drain.
 * <p>
 * Each physical connection gets its own ConnectionProcessor instance.
 * Packets are submitted into a bounded queue and drained one-at-a-time
 * (concatMap) to guarantee MQTT ordering within a single connection.
 * <p>
 * After CONNECT succeeds, binds a LogicalConnection with the negotiated
 * parameters and proper generation. All subsequent packets use the correct
 * ConnectionRef with generation for stale-command detection.
 */
public class ConnectionProcessor {

    private final ConnectionId connectionId;
    private final ConnectionRegistry connectionRegistry;
    private final LogicalConnectionRegistry logicalRegistry;
    private final CommandDispatcher dispatcher;
    private final IdGenerator idGenerator;
    private final BrokerClock clock;

    private volatile String clientId;
    private final AtomicInteger generation = new AtomicInteger(0);

    private final Sinks.Many<ClientPacket> inbound;

    public ConnectionProcessor(
            ConnectionId connectionId,
            ConnectionRegistry connectionRegistry,
            LogicalConnectionRegistry logicalRegistry,
            CommandDispatcher dispatcher,
            IdGenerator idGenerator,
            BrokerClock clock,
            int mailboxCapacity) {
        this.connectionId = connectionId;
        this.connectionRegistry = connectionRegistry;
        this.logicalRegistry = logicalRegistry;
        this.dispatcher = dispatcher;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.inbound = Sinks.many().unicast().onBackpressureBuffer();

        // Serial drain — concatMap ensures only one packet processes at a time
        inbound.asFlux()
                .concatMap(this::processPacket)
                .subscribe();
    }

    /**
     * Submit a packet for ordered processing.
     */
    public void submit(ClientPacket packet) {
        inbound.tryEmitNext(packet);
    }

    /**
     * Close this processor (connection lost or explicitly disconnected).
     */
    public void close() {
        inbound.tryEmitComplete();
        logicalRegistry.unbind(connectionId);
    }

    private Mono<Void> processPacket(ClientPacket packet) {
        return switch (packet) {
            case ClientPacket.Connect connect -> handleConnect(connect);
            default -> handlePostConnect(packet);
        };
    }

    private Mono<Void> handleConnect(ClientPacket.Connect connect) {
        String assignedClientId = connect.clientId().isEmpty()
                ? idGenerator.nextId()
                : connect.clientId();

        int gen = generation.incrementAndGet();
        var ref = ConnectionRef.local(connectionId.value(), gen);
        var command = new Command.Connect(connect, assignedClientId, clock.now());
        var sessionCommand = SessionCommand.of(assignedClientId, ref, command);

        return dispatcher.dispatch(sessionCommand)
                .doOnNext(result -> {
                    if (result instanceof CommandResult.Success success) {
                        this.clientId = assignedClientId;
                        connectionRegistry.bindClient(connectionId, assignedClientId);

                        // Bind negotiated LogicalConnection from transition result
                        var logical = success.result().connection();
                        if (logical != null) {
                            logicalRegistry.bind(connectionId, logical);
                        }
                    }
                })
                .then();
    }

    private Mono<Void> handlePostConnect(ClientPacket packet) {
        if (clientId == null) {
            // No session bound yet — protocol violation, drop packet
            return Mono.empty();
        }

        var ref = ConnectionRef.local(connectionId.value(), generation.get());
        var command = toCommand(packet);
        if (command == null) {
            return Mono.empty();
        }

        var sessionCommand = SessionCommand.of(clientId, ref, command);
        return dispatcher.dispatch(sessionCommand).then();
    }

    private Command toCommand(ClientPacket packet) {
        return switch (packet) {
            case ClientPacket.Publish pub ->
                    new Command.Publish(pub, clientId, clock.now());
            case ClientPacket.Subscribe sub ->
                    new Command.Subscribe(sub, clientId, clock.now());
            case ClientPacket.Unsubscribe unsub ->
                    new Command.Unsubscribe(unsub, clientId, clock.now());
            case ClientPacket.Disconnect disc ->
                    new Command.Disconnect(disc, clientId, clock.now());
            case ClientPacket.PingReq ping ->
                    new Command.PingReq(clientId, clock.now());
            case ClientPacket.PubAck ack ->
                    new Command.PubAck(ack, clientId, clock.now());
            case ClientPacket.PubRec rec ->
                    new Command.PubRec(rec, clientId, clock.now());
            case ClientPacket.PubRel rel ->
                    new Command.PubRel(rel, clientId, clock.now());
            case ClientPacket.PubComp comp ->
                    new Command.PubComp(comp, clientId, clock.now());
            default -> null;
        };
    }
}
