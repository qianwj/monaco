package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.limits.ProtocolLimits;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.protocol.packet.ServerPacket;

public class PingReqTransition implements Transition<Command.PingReq> {

    @Override
    public TransitionResult apply(Command.PingReq command, SessionRecord session,
                                  LogicalConnection connection, ProtocolLimits limits) {
        return TransitionResult.of(session, connection,
                new Action.SendPacket(connection.toLocalRef(), new ServerPacket.PingResp()),
                new Action.ResetKeepAliveTimer(command.clientId()));
    }
}
