package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * Properties shared by DISCONNECT packet (both client and server).
 */
public record DisconnectProperties(
        Optional<Integer> sessionExpiryInterval,
        Optional<String> reasonString,
        Optional<String> serverReference,
        List<UserProperty> userProperties
) {
    public DisconnectProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static DisconnectProperties empty() {
        return new DisconnectProperties(
                Optional.empty(), Optional.empty(), Optional.empty(), List.of()
        );
    }
}
