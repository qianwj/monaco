package cn.elvis.monaco.plugin.api.model;

import cn.elvis.monaco.plugin.api.context.MessageOrigin;
import cn.elvis.monaco.protocol.model.PacketId;
import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicName;
import cn.elvis.monaco.protocol.property.UserProperty;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Inbound publication exposed after Topic Alias resolution. Methods only
 * modify fields the plugin contract permits; packet identity remains fixed.
 * A packet identifier may therefore remain present when a plugin lowers the
 * effective delivery QoS to zero.
 */
public record PublishView(
        TopicName topic,
        PluginPayload payload,
        QoS qos,
        boolean retain,
        boolean duplicate,
        Optional<PacketId> packetId,
        Optional<Duration> messageExpiryInterval,
        Optional<String> contentType,
        Optional<TopicName> responseTopic,
        Optional<PluginPayload> correlationData,
        List<UserProperty> userProperties,
        MessageOrigin origin
) {

    public PublishView {
        if (topic == null || payload == null || qos == null || origin == null) {
            throw new IllegalArgumentException("Publish view fields must not be null");
        }
        packetId = packetId == null ? Optional.empty() : packetId;
        messageExpiryInterval = messageExpiryInterval == null
                ? Optional.empty()
                : messageExpiryInterval;
        messageExpiryInterval.ifPresent(duration -> {
            if (duration.isNegative()) {
                throw new IllegalArgumentException("Message expiry interval must not be negative");
            }
        });
        contentType = contentType == null ? Optional.empty() : contentType;
        responseTopic = responseTopic == null ? Optional.empty() : responseTopic;
        correlationData = correlationData == null ? Optional.empty() : correlationData;
        userProperties = userProperties == null ? List.of() : List.copyOf(userProperties);

        if (qos != QoS.AT_MOST_ONCE && packetId.isEmpty()) {
            throw new IllegalArgumentException("QoS 1/2 publication requires a packet id");
        }
    }

    public PublishView withMessage(
            TopicName modifiedTopic,
            PluginPayload modifiedPayload,
            QoS modifiedQos,
            List<UserProperty> modifiedUserProperties
    ) {
        if (modifiedQos != null && modifiedQos.value() > qos.value()) {
            throw new IllegalArgumentException("Plugin must not increase publication QoS");
        }
        return new PublishView(
                modifiedTopic,
                modifiedPayload,
                modifiedQos,
                retain,
                duplicate,
                packetId,
                messageExpiryInterval,
                contentType,
                responseTopic,
                correlationData,
                modifiedUserProperties,
                origin);
    }

}
