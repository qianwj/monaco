package cn.elvis.monaco.transport;

import cn.elvis.monaco.configuration.TransportConfiguration;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MqttTransport extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(MqttTransport.class);

    private final TransportConfiguration transportConfig;

    public MqttTransport(TransportConfiguration transportConfig) {
        this.transportConfig = transportConfig;
    }

    @Override
    public void start(Promise<Void> starter) throws Exception {
        log.info("Deploying transport({}) server...", transportConfig.getType());
        var options = new MqttServerOptions();
        switch (transportConfig.getType()) {
            case TCP -> options.setPort(transportConfig.getPort());
            case WS -> options.setPort(transportConfig.getPort()).setUseWebSocket(true);
            default -> throw new IllegalStateException("Unsupported transport type: " + transportConfig.getType());
        }
        var server = MqttServer.create(vertx, options);
        server.endpointHandler(endpoint -> {
            MqttAuth auth = endpoint.auth();
            endpoint.will();
        });
        server.listen().andThen(ar -> {
            if (ar.succeeded()) {
                log.info("Transport[{}] already started", transportConfig.getType());
                starter.complete();
            } else {
                log.error("Transport[{}] start failed", transportConfig.getType(), ar.cause());
                starter.fail(ar.cause());
            }
        });
    }
}
