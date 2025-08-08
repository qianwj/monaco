package cn.elvis.monaco.transport;

import cn.elvis.monaco.configuration.TransportConfiguration;
import cn.elvis.monaco.session.SessionManager;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

import java.util.Optional;

public final class MqttTransport extends AbstractVerticle {

    private static final Logger log = LogManager.getLogger(MqttTransport.class);

    private final TransportConfiguration transportConfig;

    private final Authenticator authenticator;

    private final SessionManager sessionManager;

    public MqttTransport(TransportConfiguration transportConfig,
                         Authenticator authenticator,
                         SessionManager sessionManager) {
        this.transportConfig = transportConfig;
        this.authenticator = authenticator;
        this.sessionManager = sessionManager;
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
            var username = Optional.ofNullable(auth.getUsername()).orElse("");
            var password = Optional.ofNullable(auth.getPassword()).orElse("");
            if (!authenticator.authenticate(endpoint.clientIdentifier(), username, password)) {
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD);
                return;
            }
            sessionManager.register(endpoint);
        }).exceptionHandler(ex -> {
            log.error("Failed to connect server", ex);
        });
        server.listen().andThen(ar -> {
            if (ar.succeeded()) {
                log.info("Transport[{}] already started on [:{}]", transportConfig.getType(), ar.result().actualPort());
                starter.complete();
            } else {
                log.error("Transport[{}] start failed", transportConfig.getType(), ar.cause());
                starter.fail(ar.cause());
            }
        });
    }
}
