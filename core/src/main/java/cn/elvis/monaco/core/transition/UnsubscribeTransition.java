package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.protocol.property.AckProperties;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.ArrayList;
import java.util.List;

public class UnsubscribeTransition implements Transition<Command.Unsubscribe> {

    @Override
    public TransitionResult apply(Command.Unsubscribe command, SessionRecord session,
                                  LogicalConnection connection, ProtocolLimits limits) {
        var packet = command.packet();
        List<Action> actions = new ArrayList<>();
        List<ReasonCode> reasonCodes = new ArrayList<>();

        List<String> toRemove = new ArrayList<>();
        for (String topicFilter : packet.topicFilters()) {
            // In a real implementation, we'd check if the subscription exists.
            // For now, always return SUCCESS (runtime can map to NO_SUBSCRIPTION_EXISTED if needed).
            reasonCodes.add(ReasonCode.SUCCESS);
            toRemove.add(topicFilter);
        }

        if (!toRemove.isEmpty()) {
            actions.add(new Action.RemoveSubscriptions(command.clientId(), toRemove));
        }

        // Send UNSUBACK
        actions.add(new Action.SendPacket(connection.toLocalRef(),
                new ServerPacket.UnsubAck(packet.packetId(), reasonCodes, AckProperties.empty())));

        return TransitionResult.of(session, connection, actions);
    }
}
