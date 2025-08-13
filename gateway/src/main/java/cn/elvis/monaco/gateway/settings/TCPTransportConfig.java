package cn.elvis.monaco.gateway.settings;

import io.vertx.mqtt.MqttServerOptions;

/**
 * Transport use tcp protocol
 * @param enable
 * @param port
 * @param useTLS
 */
public record TCPTransportConfig(
        boolean enable,
        int port,
        boolean useTLS
) implements TransportSettings {

    @Override
    public MqttServerOptions options() {
        return enable ?
                new MqttServerOptions()
                        .setPort(port)
                        .setSsl(useTLS) : null;
    }
}
