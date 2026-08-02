package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.state.InflightRecord;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.protocol.property.AckProperties;
import cn.elvis.monaco.protocol.property.PublishProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.ArrayList;
import java.util.List;

public class PublishTransition implements Transition<Command.Publish> {

    @Override
    public TransitionResult apply(Command.Publish command, SessionRecord session,
                                  LogicalConnection connection, ProtocolLimits limits) {
        var packet = command.packet();
        List<Action> actions = new ArrayList<>();

        if (packet.qos() == QoS.AT_MOST_ONCE) {
            // QoS 0: just route the message, no ack
            actions.add(new Action.DeliverMessage(connection.toLocalRef(),
                    new ServerPacket.Publish(packet.topicName(), packet.qos(),
                            packet.retain(), false, 0, packet.payload(), PublishProperties.empty())));

        } else if (packet.qos() == QoS.AT_LEAST_ONCE) {
            // QoS 1: persist inflight, deliver, send PUBACK
            InflightRecord inflight = new InflightRecord(
                    command.clientId(), InflightRecord.Direction.INBOUND,
                    packet.packetId(), null, QoS.AT_LEAST_ONCE,
                    InflightRecord.InflightState.PENDING_ACK,
                    false, 1, command.timestamp());
            actions.add(new Action.PersistInflight(inflight));

            actions.add(new Action.DeliverMessage(connection.toLocalRef(),
                    new ServerPacket.Publish(packet.topicName(), packet.qos(),
                            packet.retain(), false, packet.packetId(), packet.payload(), PublishProperties.empty())));

            actions.add(new Action.SendPacket(connection.toLocalRef(),
                    new ServerPacket.PubAck(packet.packetId(), ReasonCode.SUCCESS, AckProperties.empty())));

            actions.add(new Action.RemoveInflight(command.clientId(),
                    InflightRecord.Direction.INBOUND, packet.packetId()));

        } else {
            // QoS 2: persist RECEIVED state, send PUBREC
            InflightRecord inflight = new InflightRecord(
                    command.clientId(), InflightRecord.Direction.INBOUND,
                    packet.packetId(), null, QoS.EXACTLY_ONCE,
                    InflightRecord.InflightState.RECEIVED_QOS2,
                    false, 1, command.timestamp());
            actions.add(new Action.PersistInflight(inflight));

            actions.add(new Action.SendPacket(connection.toLocalRef(),
                    new ServerPacket.PubRec(packet.packetId(), ReasonCode.SUCCESS, AckProperties.empty())));
        }

        return TransitionResult.of(session, connection, actions);
    }
}
