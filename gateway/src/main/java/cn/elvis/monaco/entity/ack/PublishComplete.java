package cn.elvis.monaco.entity.ack;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;

public record PublishComplete(
        int packetId,
        ReasonCode reasonCode,
        MqttPropertiesBuilder properties
) implements PublishExchangeAcknowledge {

    @Override
    public void send(MqttEndpoint endpoint) {
        endpoint.publishComplete(packetId, MqttPubCompReasonCode.valueOf(reasonCode.value()), properties.build());
    }
}
