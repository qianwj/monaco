package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * CONNECT packet properties.
 */
public record ConnectProperties(
        Optional<Integer> sessionExpiryInterval,
        Optional<Integer> receiveMaximum,
        Optional<Integer> maximumPacketSize,
        Optional<Integer> topicAliasMaximum,
        Optional<Boolean> requestResponseInformation,
        Optional<Boolean> requestProblemInformation,
        Optional<String> authenticationMethod,
        Optional<byte[]> authenticationData,
        List<UserProperty> userProperties
) {
    public ConnectProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static ConnectProperties empty() {
        return new ConnectProperties(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of()
        );
    }
}
