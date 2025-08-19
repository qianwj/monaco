package cn.elvis.monaco.entity;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import java.util.List;

public record UnsubscribeAcknowledge(
        int packetId,
        List<MqttUnsubAckReasonCode> reasonCodes,
        MqttPropertiesBuilder properties
) {

    public void send(MqttEndpoint endpoint) {
        endpoint.unsubscribeAcknowledge(packetId, reasonCodes, properties.build());
    }
}
