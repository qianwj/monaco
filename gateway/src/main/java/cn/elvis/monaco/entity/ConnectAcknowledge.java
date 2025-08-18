package cn.elvis.monaco.entity;

import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.mqtt.MqttEndpoint;

public record ConnectAcknowledge(
        MqttConnectReturnCode returnCode,
        boolean sessionPresent,
        MqttPropertiesBuilder properties
) {

    public static ConnectAcknowledge reject(MqttConnectReturnCode returnCode, MqttPropertiesBuilder properties) {
        return new ConnectAcknowledge(returnCode, false, properties);
    }

    public static ConnectAcknowledge accept(boolean sessionPresent, MqttPropertiesBuilder properties) {
        return new ConnectAcknowledge(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent, properties);
    }

    public void send(MqttEndpoint endpoint) {
        if (MqttConnectReturnCode.CONNECTION_ACCEPTED == returnCode) {
            endpoint.accept(sessionPresent, properties.build());
        }
        endpoint.reject(returnCode, properties.build());
    }

    public boolean reject() {
        return returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED;
    }
}
