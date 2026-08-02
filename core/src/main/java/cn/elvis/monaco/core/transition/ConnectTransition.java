package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.state.ActiveBinding;
import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.state.WillRecord;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.protocol.property.ConnAckProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConnectTransition implements Transition<Command.Connect> {

    @Override
    public TransitionResult apply(Command.Connect command, SessionRecord existingSession,
                                  LogicalConnection existingConnection, ProtocolLimits limits) {
        var packet = command.packet();
        var props = packet.properties();

        // Negotiate session expiry
        int sessionExpiry = props.sessionExpiryInterval().orElse(0);
        long maxExpiry = limits.maxSessionExpiryInterval().getSeconds();
        if (sessionExpiry > maxExpiry) {
            sessionExpiry = (int) maxExpiry;
        }

        // Negotiate receive maximum
        int receiveMax = props.receiveMaximum().orElse(65535);
        receiveMax = Math.min(receiveMax, limits.maxReceiveMaximum());

        // Negotiate max packet size
        int maxPacketSize = props.maximumPacketSize().orElse(Integer.MAX_VALUE);
        maxPacketSize = Math.min(maxPacketSize, limits.maxPacketSize());

        // Negotiate topic alias maximum
        int topicAliasMax = props.topicAliasMaximum().orElse(0);
        topicAliasMax = Math.min(topicAliasMax, limits.topicAliasMaximum());

        // Negotiate keep alive
        int keepAlive = packet.keepAlive();
        int serverKeepAlive = (int) limits.serverKeepAlive().getSeconds();
        if (serverKeepAlive > 0 && (keepAlive == 0 || keepAlive > serverKeepAlive)) {
            keepAlive = serverKeepAlive;
        }

        // Determine session present
        boolean sessionPresent = !packet.cleanStart()
                && existingSession != null
                && !existingSession.isExpired(command.timestamp());

        // Build actions
        List<Action> actions = new ArrayList<>();

        // Session takeover: disconnect old connection
        if (existingSession != null && existingSession.isConnected()) {
            ConnectionRef oldRef = existingSession.activeBinding().toRef(existingSession.connectionGeneration());
            actions.add(new Action.SendPacket(oldRef,
                    new ServerPacket.Disconnect(ReasonCode.SESSION_TAKEN_OVER, null)));
            actions.add(new Action.CloseConnection(oldRef));
        }

        // Cancel pending session expiry
        if (existingSession != null) {
            actions.add(new Action.CancelSessionExpiry(command.assignedClientId()));
        }

        // Clean Start: clear old state
        if (packet.cleanStart() && existingSession != null) {
            actions.add(new Action.ClearAllSubscriptions(command.assignedClientId()));
            actions.add(new Action.ClearAllInflight(command.assignedClientId()));
            actions.add(new Action.RemoveWill(command.assignedClientId()));
        }

        // Create new session record or reconnect existing
        String connectionId = command.assignedClientId() + "-" + command.timestamp().toEpochMilli();
        ActiveBinding binding = new ActiveBinding(connectionId, "local", 1);

        SessionRecord newSession;
        if (existingSession == null || packet.cleanStart()) {
            newSession = SessionRecord.create(command.assignedClientId(), binding, sessionExpiry);
        } else {
            newSession = existingSession.connect(binding, sessionExpiry);
        }

        // Create logical connection (connection-level, not persisted)
        LogicalConnection connection = new LogicalConnection(
                connectionId, newSession.connectionGeneration(),
                receiveMax, maxPacketSize, topicAliasMax, keepAlive);

        // Will handling
        if (packet.will() != null) {
            long willDelay = packet.will().properties().willDelayInterval().orElse(0);
            WillRecord willRecord = new WillRecord(
                    command.assignedClientId(),
                    newSession.connectionGeneration(),
                    packet.will(),
                    null,  // publishAt set on disconnect
                    WillRecord.WillState.PENDING,
                    connectionId + "-will"
            );
            actions.add(new Action.PersistWill(willRecord));
        }

        // Build CONNACK properties
        ConnAckProperties connAckProps = new ConnAckProperties(
                Optional.of(sessionExpiry),
                Optional.of(limits.defaultReceiveMaximum()),
                Optional.of(limits.maximumQoS().value()),
                Optional.of(limits.retainAvailable()),
                Optional.of(limits.maxPacketSize()),
                packet.clientId().isEmpty() ? Optional.of(command.assignedClientId()) : Optional.empty(),
                Optional.of(limits.topicAliasMaximum()),
                Optional.empty(),
                Optional.of(limits.wildcardSubscriptionAvailable()),
                Optional.of(limits.subscriptionIdentifierAvailable()),
                Optional.of(limits.sharedSubscriptionAvailable()),
                keepAlive != packet.keepAlive() ? Optional.of(keepAlive) : Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );

        // Send CONNACK
        ConnectionRef newRef = connection.toLocalRef();
        actions.add(new Action.SendPacket(newRef,
                new ServerPacket.ConnAck(sessionPresent, ReasonCode.SUCCESS, connAckProps)));

        // Start keep alive timer
        if (keepAlive > 0) {
            actions.add(new Action.StartKeepAliveTimer(command.assignedClientId(), keepAlive));
        }

        // Persist session
        actions.add(new Action.PersistSession(command.assignedClientId()));

        return TransitionResult.of(newSession, connection, actions);
    }
}
