package cn.elvis.monaco.core.rule;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.Optional;

/**
 * Validates SUBSCRIBE command against broker config constraints.
 */
public final class SubscribeRule {

    private SubscribeRule() {
    }

    public static Optional<RejectReason> validate(Command.Subscribe command, BrokerConfig config) {
        var packet = command.packet();

        // Must have at least one subscription
        if (packet.subscriptions() == null || packet.subscriptions().isEmpty()) {
            return RejectReason.reject(ReasonCode.PROTOCOL_ERROR,
                    "SUBSCRIBE must contain at least one topic filter");
        }

        // Subscription identifier support
        if (packet.properties().subscriptionIdentifier().isPresent()
                && !config.subscriptionIdentifierAvailable()) {
            return RejectReason.reject(ReasonCode.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
                    "Subscription identifiers not supported");
        }

        return RejectReason.pass();
    }
}
