package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * Properties for SUBSCRIBE packet.
 */
public record SubscribeProperties(
        Optional<Integer> subscriptionIdentifier,
        List<UserProperty> userProperties
) {
    public SubscribeProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static SubscribeProperties empty() {
        return new SubscribeProperties(Optional.empty(), List.of());
    }
}
