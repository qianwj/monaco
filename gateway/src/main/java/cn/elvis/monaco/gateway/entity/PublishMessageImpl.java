package cn.elvis.monaco.gateway.entity;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttPublishMessage;

public final class PublishMessageImpl implements PublishMessage {

    private final MqttPublishMessage source;

    PublishMessageImpl(MqttPublishMessage source) {
        this.source = source;
    }

    PublishMessageImpl(int packetId, MqttWill will) {
        this.source = MqttPublishMessage.create(
                packetId,
                MqttQoS.valueOf(will.getWillQos()),
                false,
                will.isWillRetain(),
                will.getWillTopic(),
                will.getWillMessage()
        );
    }

    @Override
    public int packetId() {
        return source.messageId();
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
    public MqttProperties properties() {
        return source.properties();
    }
}
