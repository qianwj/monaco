package cn.elvis.monaco.entity.ack;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;

public record PublishAcknowledge(
        int packetId,
        ReasonCode reasonCode,
        MqttPropertiesBuilder properties) implements PublishExchangeAcknowledge {

    @Override
    public void send(MqttEndpoint endpoint) {
        endpoint.publishAcknowledge(packetId, MqttPubAckReasonCode.valueOf(reasonCode.value()), properties.build());
    }

}
