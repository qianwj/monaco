package cn.elvis.monaco.session;

import cn.elvis.monaco.common.session.Authenticator;
import cn.elvis.monaco.configuration.AuthConfiguration;
import cn.elvis.monaco.configuration.SessionConfiguration;
import cn.elvis.monaco.session.authenticate.ConfigurableAuthenticator;
import cn.elvis.monaco.session.authenticate.EnhancedAuthenticationManager;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.configuration.SessionConfiguration;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager extends AbstractVerticle {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final ArrayBlockingQueue<MqttEndpoint> waitingList;

    private final int maxConnections;

    private final boolean enableServerAssignedClientId;

    private final boolean enableClientIdValidate;

    private final ClientIdGenerator clientIdGenerator;

    private final ClientIdValidator clientIdValidator;

    private volatile boolean running;

    public SessionManager(SessionConfiguration config,
                          ClientIdGenerator clientIdGenerator,
                          ClientIdValidator clientIdValidator) {
        this.maxConnections = config.getMaxConnections();
        this.enableServerAssignedClientId = config.getClientIdentifier().isEnableServerAssigned();
        this.enableClientIdValidate = config.getClientIdentifier().isEnableValidate();
        this.clientIdGenerator = clientIdGenerator;
        this.clientIdValidator = clientIdValidator;
        this.waitingList = new ArrayBlockingQueue<>(maxConnections);
    }

    public void register(MqttEndpoint endpoint) {
        if (waitingList.size() >= maxConnections) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_CONNECTION_RATE_EXCEEDED);
            return;
        }
        waitingList.add(endpoint);
    }

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
    @Override
    public void start(Promise<Void> starter) {
        running = true;
        handleEndpoints(vertx);
        vertx.eventBus().<String>consumer(ChannelKeys.CLEAN_SESSION, message -> {
            sessions.remove(message.body());
        });
    }

    @Override
    public void stop(Promise<Void> stopper) {
        running = false;
        stopper.complete();
    }

    private void handleEndpoints(Vertx vertx) {
        Thread.ofVirtual().name("EndpointHandler").start(() -> {
            while (running) {
                MqttEndpoint endpoint = waitingList.poll();
                if (endpoint == null) {
                    continue;
                }
                var clientId = endpoint.clientIdentifier();
                if (clientId == null) {
                    if (enableServerAssignedClientId) {
                        clientId = clientIdGenerator.generate();
                        endpoint.setClientIdentifier(clientId);
                    } else {
                        endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                        continue;
                    }
                }
                if (enableClientIdValidate && !clientIdValidator.isValid(clientId)) {
                    endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
                    continue;
                }
                sessions.put(clientId, new Session(vertx, endpoint));
            }
        });
    }
}
