package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * Will properties (part of CONNECT payload).
 */
public record WillProperties(
        Optional<Integer> willDelayInterval,
        Optional<Integer> payloadFormatIndicator,
        Optional<Integer> messageExpiryInterval,
        Optional<String> contentType,
        Optional<String> responseTopic,
        Optional<byte[]> correlationData,
        List<UserProperty> userProperties
) {
    public WillProperties {
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static WillProperties empty() {
        return new WillProperties(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                List.of()
        );
    }
}
