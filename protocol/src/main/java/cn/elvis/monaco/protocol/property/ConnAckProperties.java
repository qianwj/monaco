package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * CONNACK packet properties.
 */
public record ConnAckProperties(
        Optional<Integer> sessionExpiryInterval,
        Optional<Integer> receiveMaximum,
        Optional<Integer> maximumQoS,
        Optional<Boolean> retainAvailable,
        Optional<Integer> maximumPacketSize,
        Optional<String> assignedClientIdentifier,
        Optional<Integer> topicAliasMaximum,
        Optional<String> reasonString,
        Optional<Boolean> wildcardSubscriptionAvailable,
        Optional<Boolean> subscriptionIdentifierAvailable,
        Optional<Boolean> sharedSubscriptionAvailable,
        Optional<Integer> serverKeepAlive,
        Optional<String> responseInformation,
        Optional<String> serverReference,
        Optional<String> authenticationMethod,
        Optional<byte[]> authenticationData,
        List<UserProperty> userProperties
) {
    public ConnAckProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static ConnAckProperties empty() {
        return new ConnAckProperties(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), List.of()
        );
    }
}
