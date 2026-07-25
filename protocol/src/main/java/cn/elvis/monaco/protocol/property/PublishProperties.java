package cn.elvis.monaco.protocol.property;

import java.util.List;
import java.util.Optional;

/**
 * PUBLISH packet properties.
 */
public record PublishProperties(
        Optional<Integer> payloadFormatIndicator,
        Optional<Integer> messageExpiryInterval,
        Optional<Integer> topicAlias,
        Optional<String> responseTopic,
        Optional<byte[]> correlationData,
        List<Integer> subscriptionIdentifiers,
        Optional<String> contentType,
        List<UserProperty> userProperties
) {
    public PublishProperties {
        if (subscriptionIdentifiers == null) {
            subscriptionIdentifiers = List.of();
        }
        if (userProperties == null) {
            userProperties = List.of();
        }
    }

    public static PublishProperties empty() {
        return new PublishProperties(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of(),
                Optional.empty(), List.of()
        );
    }
}
