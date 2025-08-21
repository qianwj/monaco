package cn.elvis.monaco.entity.ack;

import io.vertx.mqtt.MqttEndpoint;

public interface Acknowledge {

    void send(MqttEndpoint endpoint);
}
