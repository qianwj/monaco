package cn.elvis.monaco.gateway.settings;

import io.vertx.mqtt.MqttServerOptions;

public interface TransportSettings {

    boolean enable();

    MqttServerOptions options();
}
