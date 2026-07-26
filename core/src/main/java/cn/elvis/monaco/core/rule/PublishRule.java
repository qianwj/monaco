package cn.elvis.monaco.core.rule;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.Optional;

/**
 * Validates PUBLISH command against broker config constraints.
 */
public final class PublishRule {

    private PublishRule() {
    }

    public static Optional<RejectReason> validate(Command.Publish command, BrokerConfig config) {
        var packet = command.packet();

        // QoS exceeds maximum
        if (packet.qos().value() > config.maximumQoS().value()) {
            return RejectReason.reject(ReasonCode.QOS_NOT_SUPPORTED,
                    "QoS " + packet.qos().value() + " exceeds maximum " + config.maximumQoS().value());
        }

        // Retain not supported
        if (packet.retain() && !config.retainAvailable()) {
            return RejectReason.reject(ReasonCode.RETAIN_NOT_SUPPORTED,
                    "Retain not supported by this server");
        }

        // Topic name validation
        if (packet.topicName() == null || packet.topicName().isEmpty()) {
            return RejectReason.reject(ReasonCode.TOPIC_NAME_INVALID,
                    "Topic name must not be empty");
        }

        // Topic name must not contain wildcards
        if (packet.topicName().contains("#") || packet.topicName().contains("+")) {
            return RejectReason.reject(ReasonCode.TOPIC_NAME_INVALID,
                    "Topic name must not contain wildcard characters");
        }

        return RejectReason.pass();
    }
}
