package cn.elvis.monaco.entity;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;

public record PublishReceived(
        int packetId,
        ReasonCode reasonCode,
        MqttPropertiesBuilder properties
) implements PublishExchangeAcknowledge {

    @Override
    public void send(MqttEndpoint endpoint) {
        endpoint.publishReceived(packetId, MqttPubRecReasonCode.valueOf(reasonCode.value()), properties.build());
    }
}
