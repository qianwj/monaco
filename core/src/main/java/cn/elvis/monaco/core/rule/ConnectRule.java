package cn.elvis.monaco.core.rule;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.Optional;

/**
 * Validates CONNECT command against broker config constraints.
 */
public final class ConnectRule {

    private ConnectRule() {
    }

    public static Optional<RejectReason> validate(Command.Connect command, BrokerConfig config) {
        var packet = command.packet();

        // Client identifier length check
        if (!packet.clientId().isEmpty()
                && packet.clientId().length() > config.maxClientIdentifierLength()) {
            return RejectReason.reject(ReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
                    "Client identifier exceeds maximum length: " + config.maxClientIdentifierLength());
        }

        // Empty client id requires server-assigned support
        if (packet.clientId().isEmpty() && !config.serverAssignedClientIdentifier()) {
            return RejectReason.reject(ReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
                    "Server-assigned client identifiers not supported");
        }

        // Empty client id with cleanStart=false is a protocol error
        if (packet.clientId().isEmpty() && !packet.cleanStart()) {
            return RejectReason.reject(ReasonCode.CLIENT_IDENTIFIER_NOT_VALID,
                    "Empty client identifier requires cleanStart=true");
        }

        return RejectReason.pass();
    }
}
