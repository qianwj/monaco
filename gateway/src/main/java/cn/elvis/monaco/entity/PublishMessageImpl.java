package cn.elvis.monaco.entity;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttPublishMessage;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Publish message implementation, wrapped MqttPublishMessage
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class PublishMessageImpl implements PublishMessage {

    private final MqttPublishMessage source;

    private final String senderId;

    private final Instant expiryTime;

    PublishMessageImpl(MqttPublishMessage source, String senderId) {
        this.source = source;
        this.expiryTime = expiryTime(source.properties());
        this.senderId = senderId;
    }

    PublishMessageImpl(MqttPublishMessage source, String topic, MqttQoS qos, boolean duplicate, boolean retain, String senderId) {
        this.source = MqttPublishMessage.create(
                source.messageId(),
                qos,
                duplicate,
                retain,
                topic,
                source.payload(),
                source.properties()
        );
        this.expiryTime = expiryTime(source.properties());
        this.senderId = senderId;
    }

    PublishMessageImpl(PublishMessage source, String topic, boolean duplicate, boolean retain, String senderId) {
        this.source = MqttPublishMessage.create(
                source.packetId(),
                source.qos(),
                duplicate,
                retain,
                topic,
                source.payload(),
                source.properties()
        );
        this.expiryTime = expiryTime(source.properties());
        this.senderId = senderId;
    }

    PublishMessageImpl(int packetId, String senderId, MqttWill will) {
        this.source = MqttPublishMessage.create(
                packetId,
                MqttQoS.valueOf(will.getWillQos()),
                false,
                will.isWillRetain(),
                will.getWillTopic(),
                will.getWillMessage(),
                will.getWillProperties()
        );
        this.expiryTime = expiryTime(will.getWillProperties());
        this.senderId = senderId;
    }

    @Override
    public int packetId() {
        return source.messageId();
    }

    @Override
    public String senderId() {
        return senderId;
    }

    @Override
    public MqttQoS qos() {
        return source.qosLevel();
    }

    @Override
    public String topic() {
        return source.topicName();
    }

    @Override
    public Buffer payload() {
        return source.payload();
    }

    @Override
    public boolean duplicate() {
        return source.isDup();
    }

    @Override
    public boolean retain() {
        return source.isRetain();
    }

    @Override
    public boolean expired() {
        return Objects.nonNull(expiryTime) && expiryTime.isAfter(Instant.now());
    }

    @Override
    public MqttProperties properties() {
        return source.properties();
    }

    private Instant expiryTime(MqttProperties properties) {
        return Optional.ofNullable(
                        properties.getProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value())
                )
                .map(MqttProperties.MqttProperty::value)
                .map(v -> Instant.now().plusMillis((int) v))
                .orElse(null);
    }
}
