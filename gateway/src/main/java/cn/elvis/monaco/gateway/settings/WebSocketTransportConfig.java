package cn.elvis.monaco.gateway.settings;

import io.vertx.mqtt.MqttServerOptions;

/**
 * Transport use web socket protocol
 * @param enable
 * @param port
 * @param path vertx-mqtt not support config custom websocket path
 * @param useTLS
 */
public record WebSocketTransportConfig(
        boolean enable,
        int port,
        String path,
        boolean useTLS
) implements TransportSettings {

    @Override
    public MqttServerOptions options() {
        return enable ?
                new MqttServerOptions()
                        .setUseWebSocket(true)
                        .setPort(port)
                        .setSsl(useTLS) : null;
    }
}
