package cn.elvis.monaco.settings;

import io.vertx.mqtt.MqttServerOptions;

public interface TransportSettings {

    boolean enable();

    int port();

    boolean useTLS();

    int instances();

    MqttServerOptions options();
}
