package cn.elvis.monaco.plugin.api.model;

import cn.elvis.monaco.protocol.model.QoS;
import cn.elvis.monaco.protocol.model.TopicName;
import cn.elvis.monaco.protocol.property.UserProperty;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Will message policy view evaluated once while accepting CONNECT. */
public record WillView(
        TopicName topic,
        PluginPayload payload,
        QoS qos,
        boolean retain,
        Duration delayInterval,
        Optional<Duration> messageExpiryInterval,
        Optional<String> contentType,
        Optional<TopicName> responseTopic,
        Optional<PluginPayload> correlationData,
        List<UserProperty> userProperties
) {

    public WillView {
        if (topic == null || payload == null || qos == null || delayInterval == null) {
            throw new IllegalArgumentException("Will view fields must not be null");
        }
        if (delayInterval.isNegative()) {
            throw new IllegalArgumentException("Will delay interval must not be negative");
        }
        messageExpiryInterval = messageExpiryInterval == null
                ? Optional.empty()
                : messageExpiryInterval;
        messageExpiryInterval.ifPresent(duration -> {
            if (duration.isNegative()) {
                throw new IllegalArgumentException("Will expiry interval must not be negative");
            }
        });
        contentType = contentType == null ? Optional.empty() : contentType;
        responseTopic = responseTopic == null ? Optional.empty() : responseTopic;
        correlationData = correlationData == null ? Optional.empty() : correlationData;
        userProperties = userProperties == null ? List.of() : List.copyOf(userProperties);
    }

    public WillView withMessage(
            TopicName modifiedTopic,
            PluginPayload modifiedPayload,
            QoS modifiedQos,
            List<UserProperty> modifiedUserProperties
    ) {
        if (modifiedQos == null || modifiedQos.value() > qos.value()) {
            throw new IllegalArgumentException("Plugin must not increase Will QoS");
        }
        return new WillView(modifiedTopic, modifiedPayload, modifiedQos, retain,
                delayInterval, messageExpiryInterval, contentType, responseTopic,
                correlationData, modifiedUserProperties);
    }
}
