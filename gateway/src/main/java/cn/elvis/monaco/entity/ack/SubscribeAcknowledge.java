package cn.elvis.monaco.entity.ack;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.util.List;

public record SubscribeAcknowledge(
        int packetId,
        List<MqttSubAckReasonCode> reasonCodes,
        MqttPropertiesBuilder properties
) implements Acknowledge {

    @Override
    public void send(MqttEndpoint endpoint) {
        endpoint.subscribeAcknowledge(packetId, reasonCodes, properties.build());
    }
}
