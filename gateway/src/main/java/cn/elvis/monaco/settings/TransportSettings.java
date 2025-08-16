package cn.elvis.monaco.settings;

import cn.elvis.monaco.transport.TransportType;
import io.vertx.mqtt.MqttServerOptions;

import java.util.StringJoiner;

/**
 * Transport settings implementations
 * @param enable {@code true} is allowed use this transport.
 * @param port Transport listen port.
 * @param useTLS Use secure tcp or ws connection.
 * @param instances Transport count.
 *
 * @author qianwj
 * @since  0.0.1
 */
public record TransportSettings(
        TransportType transportType,
        boolean enable,
        int port,
        boolean useTLS,
        int instances
) {

    public MqttServerOptions options() {
        return enable ?
                new MqttServerOptions()
                        .setPort(port)
                        .setSsl(useTLS)
                        .setUseWebSocket(transportType == TransportType.WS)
                : null;
    }

    @Override
    public String toString() {
        var joiner = new StringJoiner(", ", "{", "}");
        joiner.add("enable: " + enable);
        joiner.add("port: " + port);
        joiner.add("useTLS: " + useTLS);
        joiner.add("instances: " + instances);
        return joiner.toString();
    }
}
