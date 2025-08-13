package cn.elvis.monaco.gateway.settings;

import io.vertx.mqtt.MqttServerOptions;

/**
 * Transport use tcp protocol
 * @param enable
 * @param port
 * @param useTLS
 */
record TransportSettingsImpl(
        boolean enable,
        int port,
        boolean useTLS,
        int instances
) implements TransportSettings {

    @Override
    public MqttServerOptions options() {
        return enable ?
                new MqttServerOptions()
                        .setPort(port)
                        .setSsl(useTLS) : null;
    }
}
