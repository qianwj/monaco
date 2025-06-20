package cn.elvis.monaco.session;

import cn.elvis.monaco.common.session.Authenticator;
import cn.elvis.monaco.configuration.AuthConfiguration;
import cn.elvis.monaco.configuration.SessionConfiguration;
import cn.elvis.monaco.session.authenticate.ConfigurableAuthenticator;
import cn.elvis.monaco.session.authenticate.EnhancedAuthenticationManager;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final SessionConfiguration configuration;

    private final Vertx vertx;

    private final Authenticator authenticator;

    private final EnhancedAuthenticationManager enhancedAuthenticator;

    public SessionManager(Vertx vertx,
                          SessionConfiguration configuration,
                          AuthConfiguration authConfiguration) {
        this.vertx = vertx;
        this.configuration = configuration;
        this.authenticator = createAuthenticator(authConfiguration);
        this.enhancedAuthenticator = createEnhancedAuthenticationManager(authConfiguration);
    }

    public void add(MqttEndpoint endpoint) {
        var clientId = Optional.ofNullable(endpoint.clientIdentifier()).orElse("");
        if (clientId.isBlank()) {
            if (!configuration.isSeverAssignedClientId()) {
                endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID);
                return;
            }
            // todo: assign client id
        }
        if (clientId.length() > configuration.getMaxClientIdentifierLength()) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_CLIENT_IDENTIFIER_NOT_VALID);
            return;
        }
        endpoint.setClientIdentifier(clientId);
        var session = new Session(vertx, configuration, endpoint, authenticator, enhancedAuthenticator);
        session.connect(vertx).onSuccess(passed -> {
           if (passed) {
               sessions.put(clientId, session);
           }
        });
    }

    private Authenticator createAuthenticator(AuthConfiguration configuration) {
        return switch (configuration.getMode()) {
            case "allow_anonymous" -> Authenticator.AllowAnonymousAuthenticator.getInstance();
            case "configurable" -> new ConfigurableAuthenticator(configuration);
            default -> throw new IllegalStateException("Unsupported authentication mode: " + configuration.getMode());
        };
    }

    private EnhancedAuthenticationManager createEnhancedAuthenticationManager(AuthConfiguration configuration) {

        return null;
    }
}
