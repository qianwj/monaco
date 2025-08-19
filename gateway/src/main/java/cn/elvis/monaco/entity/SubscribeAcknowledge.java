package cn.elvis.monaco.entity;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.util.List;

public record SubscribeAcknowledge(
        int packetId,
        List<MqttSubAckReasonCode> reasonCodes,
        MqttPropertiesBuilder properties
) {

    public void send(MqttEndpoint endpoint) {
        endpoint.subscribeAcknowledge(packetId, reasonCodes, properties.build());
    }
}
