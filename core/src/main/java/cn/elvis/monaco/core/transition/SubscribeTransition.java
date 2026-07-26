package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.packet.Subscription;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.protocol.property.AckProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.ArrayList;
import java.util.List;

public class SubscribeTransition implements Transition<Command.Subscribe> {

    @Override
    public TransitionResult apply(Command.Subscribe command, SessionRecord session,
                                  LogicalConnection connection, BrokerConfig config) {
        var packet = command.packet();
        List<Action> actions = new ArrayList<>();
        List<ReasonCode> reasonCodes = new ArrayList<>();
        List<SubscriptionRecord> toStore = new ArrayList<>();

        int subId = packet.properties().subscriptionIdentifier().orElse(0);

        for (Subscription sub : packet.subscriptions()) {
            ReasonCode code = validateSubscription(sub, config);
            reasonCodes.add(code);

            if (code.isSuccess()) {
                QoS grantedQoS = QoS.valueOf(Math.min(sub.maxQoS(), config.maximumQoS().value()));
                toStore.add(new SubscriptionRecord(
                        command.clientId(),
                        sub.topicFilter(),
                        grantedQoS,
                        sub.noLocal(),
                        sub.retainAsPublished(),
                        sub.retainHandling().value(),
                        subId,
                        0,
                        command.timestamp()
                ));
            }
        }

        if (!toStore.isEmpty()) {
            actions.add(new Action.PersistSubscriptions(command.clientId(), toStore));
        }

        // Send SUBACK
        actions.add(new Action.SendPacket(connection.toLocalRef(),
                new ServerPacket.SubAck(packet.packetId(), reasonCodes, AckProperties.empty())));

        return TransitionResult.of(session, connection, actions);
    }

    private ReasonCode validateSubscription(Subscription sub, BrokerConfig config) {
        // Wildcard check
        if (!config.wildcardSubscriptionAvailable()) {
            if (sub.topicFilter().contains("#") || sub.topicFilter().contains("+")) {
                return ReasonCode.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED;
            }
        }

        // Shared subscription check
        if (!config.sharedSubscriptionAvailable()) {
            if (sub.topicFilter().startsWith("$share/")) {
                return ReasonCode.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED;
            }
        }

        // Grant QoS (capped to server max)
        QoS requested = QoS.valueOf(sub.maxQoS());
        QoS granted = QoS.valueOf(Math.min(requested.value(), config.maximumQoS().value()));
        return switch (granted) {
            case AT_MOST_ONCE -> ReasonCode.GRANTED_QOS_0;
            case AT_LEAST_ONCE -> ReasonCode.GRANTED_QOS_1;
            case EXACTLY_ONCE -> ReasonCode.GRANTED_QOS_2;
        };
    }
}
