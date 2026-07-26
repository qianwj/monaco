package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.state.WillRecord;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.ArrayList;
import java.util.List;

public class DisconnectTransition implements Transition<Command.Disconnect> {

    @Override
    public TransitionResult apply(Command.Disconnect command, SessionRecord session,
                                  LogicalConnection connection, BrokerConfig config) {
        var packet = command.packet();
        List<Action> actions = new ArrayList<>();

        // Apply new session expiry if provided in DISCONNECT packet
        long expirySeconds = session.sessionExpiryIntervalSeconds();
        // MQTT 5 allows updating expiry on DISCONNECT (only increase from 0 is protocol error,
        // but that's validated in the rule layer)

        // Normal disconnect: cancel will
        if (packet.reasonCode() == ReasonCode.NORMAL_DISCONNECTION) {
            actions.add(new Action.RemoveWill(command.clientId()));
        } else {
            // Abnormal disconnect or disconnect-with-will: schedule will publish
            long willDelay = 0; // Will delay comes from WillRecord, runtime resolves it
            actions.add(new Action.ScheduleWillPublish(command.clientId(), willDelay));
        }

        // Disconnect the session
        SessionRecord disconnected = session.disconnect(command.timestamp(), expirySeconds);

        // Schedule session expiry or remove immediately
        if (expirySeconds > 0 && expirySeconds != SessionRecord.EXPIRY_INFINITE) {
            actions.add(new Action.ScheduleSessionExpiry(command.clientId(), expirySeconds));
            actions.add(new Action.PersistSession(command.clientId()));
        } else if (expirySeconds == 0) {
            actions.add(new Action.RemoveSession(command.clientId()));
        } else {
            // Infinite expiry — persist but no timer
            actions.add(new Action.PersistSession(command.clientId()));
        }

        actions.add(new Action.CloseConnection(command.clientId()));

        return TransitionResult.of(disconnected, null, actions);
    }
}
