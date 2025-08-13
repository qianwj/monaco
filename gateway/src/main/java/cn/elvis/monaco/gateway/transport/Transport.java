package cn.elvis.monaco.gateway.transport;

import cn.elvis.monaco.gateway.session.EndpointHandler;
import cn.elvis.monaco.gateway.settings.Settings;
import cn.elvis.monaco.gateway.settings.TransportSettings;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.MqttServer;

import java.util.Objects;

/**
 * Transport abstraction
 *
 * @author qianwj
 * @since  0.0.1
 */
abstract class Transport extends AbstractVerticle {

    static final Logger log = LoggerFactory.getLogger(Transport.class);

    private final Settings settings;

    private final EndpointHandler endpointHandler;

    MqttServer server;

    Transport(Settings settings, EndpointHandler endpointHandler) {
        this.settings = settings;
        this.endpointHandler = endpointHandler;
    }

    final Future<Void> startServer(TransportType transportType) throws Exception {
        TransportSettings config = switch (transportType) {
            case TransportType.TCP -> settings.tcp();
            case TransportType.WS -> settings.webSocket();
            default -> throw new IllegalArgumentException("Unsupported transportType: " + transportType);
        };
        if (config.enable()) {
            this.server = MqttServer.create(vertx, config.options());
            return server.endpointHandler(endpointHandler)
                    .exceptionHandler(ex -> ex.printStackTrace(System.err))
                    .listen().compose(srv -> {
                        log.info("Transport[" + transportType + "] server started on [:" + srv.actualPort() + "]");
                        return Future.succeededFuture();
                    });
        }
        return Future.succeededFuture();
    }

    @Override
    public final void stop(Promise<Void> stopper) throws Exception {
        if (Objects.nonNull(server)) {
            server.close().onComplete(stopper);
        } else {
            stopper.complete();
        }
    }
}
