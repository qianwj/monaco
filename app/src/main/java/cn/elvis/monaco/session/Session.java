package cn.elvis.monaco.session;

import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.common.session.Authenticator;
import cn.elvis.monaco.configuration.SessionConfiguration;
import cn.elvis.monaco.session.authenticate.EnhancedAuthenticationManager;
import cn.elvis.monaco.session.will.SessionWill;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.mqtt.MqttEndpoint;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MQTT client session
 * @author qianwenjin1
 * @since  2025/06/19
 */
public final class Session {

    private static final Logger log = LogManager.getLogger();

    private final MqttEndpoint endpoint;

    private final ConnectionProperties connectionProperties;

    private final Connector connector;

    private final Publisher publisher;

    private final SessionWill will;

    public Session(Vertx vertx,
                   SessionConfiguration config,
                   MqttEndpoint endpoint,
                   Authenticator authenticator,
                   EnhancedAuthenticationManager enhancedAuthenticator) {
        this.endpoint = endpoint;
        this.connectionProperties = ConnectionProperties.fromPropertiesAndConfig(endpoint.connectProperties(), config);
        this.will = SessionWill.create(endpoint.will());
        this.connector = new Connector(
                endpoint,
                authenticator,
                enhancedAuthenticator,
                config.isEnableEnhancedAuthenticate(),
                connectionProperties.withProblemInfo()
        );
        var operateChecker = new OperateChecker(enhancedAuthenticator, config.isEnableEnhancedAuthenticate(), endpoint);
        this.publisher = new Publisher(endpoint, vertx, operateChecker);
    }

    void close() {
        endpoint.close();
    }

    Future<Boolean> connect(Vertx vertx) {
        if (!setExpired(vertx)) {
            return Future.succeededFuture(false);
        }
        if (!supportedVersion()) {
            return Future.succeededFuture(false);
        }
        return connector.connect(vertx);
    }

    private boolean setExpired(Vertx vertx) {
        if (connectionProperties.sessionExpiryInterval().isEmpty()) {
            endpoint.close();
            return false;
        }
        vertx.setPeriodic(connectionProperties.sessionExpiryInterval().get() * 1000L, id -> {
           endpoint.close();
        });
        return true;
    }

    private boolean supportedVersion() {
        if (endpoint.protocolVersion() < 5) {
            if (connectionProperties.withProblemInfo()) {
                var properties = MqttPropertiesBuilder.create().withReasonString("Unsupported protocol version").build();
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR, properties);
            } else {
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_PROTOCOL_ERROR);
            }
            return false;
        }
        return true;
    }

    private void closeNotify(Vertx vertx) {
        var opts = new DeliveryOptions().setLocalOnly(true);
        vertx.eventBus().publish(ChannelKeys.SESSION_CLOSE, endpoint.clientIdentifier(), opts);
    }

}
