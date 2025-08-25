package cn.elvis.monaco.manager.standalone;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.authentication.Authentications;
import cn.elvis.monaco.authentication.Authenticator;
import cn.elvis.monaco.entity.ack.ConnectAcknowledge;
import cn.elvis.monaco.entity.events.ClientSessionClose;
import cn.elvis.monaco.manager.ClientSessionManager;
import cn.elvis.monaco.metrics.Metrics;
import cn.elvis.monaco.session.ClientSession;
import cn.elvis.monaco.session.DefaultClientSession;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.MessageStore;
import cn.elvis.monaco.store.TopicAliasStore;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import cn.elvis.monaco.utils.ULID;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;

import java.util.Map;
import java.util.Optional;

/**
 * Default client session manager implementations, use Hash Map store client session.
 * It's not a cluster mode, so `Server Reference` property will not be implemented.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultClientSessionManager implements ClientSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSessionManager.class);

    private final Settings settings;

    private final Vertx vertx;

    private final Authenticator authenticator;

    private final TopicAliasStore topicAliasStore;

    private final ClientSessionStore clientSessionStore;

    private final MessageStore messageStore;

    private final long timerId;

    public DefaultClientSessionManager(Settings settings,
                                       Vertx vertx,
                                       TopicAliasStore topicAliasStore,
                                       ClientSessionStore clientSessionStore,
                                       MessageStore messageStore) {
        this.settings = settings;
        this.vertx = vertx;
        this.authenticator = Authentications.create(settings, vertx);
        this.topicAliasStore = topicAliasStore;
        this.clientSessionStore = clientSessionStore;
        this.timerId = vertx.setTimer(1000, id -> removeExpiredSessions());
        this.messageStore = messageStore;
    }

    @Override
    public boolean sessionPresent(String clientId) {
        return clientSessionStore.get(clientId).isPresent();
    }

    @Override
    public synchronized ConnectAcknowledge register(MqttEndpoint endpoint) {
        removeExpiredSessions();
        endpoint.autoKeepAlive(false)
                .publishAutoAck(false)
                .subscriptionAutoAck(false);
        MqttPropertiesBuilder properties = MqttPropertiesBuilder.create();
        if (StringUtil.isNullOrEmpty(endpoint.clientIdentifier())) {
            if (settings.serverAssignedClientIdentifier()) {
                endpoint.setClientIdentifier(ULID.random());
                properties.withProperty(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER, endpoint.clientIdentifier());
            } else {
                return ConnectAcknowledge.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, properties);
            }
        }
        if (endpoint.clientIdentifier().length() > settings.maximumClientIdentifierLength()) {
            return ConnectAcknowledge.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, properties);
        }
        log.info("New client incoming: " + endpoint.clientIdentifier() + ", clean start: " + endpoint.isCleanSession());
        var username = Optional.ofNullable(endpoint.auth()).map(MqttAuth::getUsername).orElse("");
        var password = Optional.ofNullable(endpoint.auth()).map(MqttAuth::getPassword).orElse("");
        var auth = authenticator.authenticate(endpoint.clientIdentifier(), username, password);
        if (!auth.passed()) {
            log.info("Authentication failed: " + endpoint.clientIdentifier() + ", username: " + username + ", reason: " + auth.reason());
            return ConnectAcknowledge.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD, properties);
        }
        boolean sessionPresent = false;
        if (endpoint.isCleanSession()) {
            cleanSession(endpoint.clientIdentifier());
        } else {
            sessionPresent = sessionPresent(endpoint.clientIdentifier());
        }

        if (sessionPresent) {
            // todo: resume previous connection
            return ConnectAcknowledge.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, properties);
        }
        if (clientSessionStore.total() + 1 <= settings.maximumSessionCount()) {
            int sessionExpiryInterval = Math.min(
                    MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttPropertyType.SESSION_EXPIRY_INTERVAL, settings.defaultSessionExpiryInterval()),
                    settings.maxSessionExpiryInterval()
            );
            int receiveMaximum = Math.min(
                    MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttPropertyType.RECEIVE_MAXIMUM, settings.defaultReceiveMaximum()),
                    settings.maxReceiveMaximum()
            );
            int maximumPacketSize = MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttPropertyType.MAXIMUM_PACKET_SIZE, 0);
            int topicAliasMaximum = Math.min(
                    MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttPropertyType.TOPIC_ALIAS_MAXIMUM, settings.topicAliasMaximum()),
                    settings.topicAliasMaximum()
            );
            int serverKeepalive = MqttPropertiesUtils.intValue(endpoint.connectProperties(), MqttPropertyType.SERVER_KEEP_ALIVE, 0);
            topicAliasStore.setTopicAliasMaximum(endpoint.clientIdentifier(), topicAliasMaximum);
            properties
                    .withProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL, sessionExpiryInterval)
                    .withProperty(MqttPropertyType.RECEIVE_MAXIMUM, receiveMaximum)
                    .withProperty(MqttPropertyType.MAXIMUM_QOS, settings.maximumQualityOfService())
                    .withAvailableOption(MqttPropertyType.RETAIN_AVAILABLE, settings.retainAvailable())
                    .withProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE, maximumPacketSize)
                    .withProperty(MqttPropertyType.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum)
                    .withAvailableOption(MqttPropertyType.WILDCARD_SUBSCRIPTION_AVAILABLE, settings.wildcardSubscriptionAvailable())
                    .withAvailableOption(MqttPropertyType.SUBSCRIPTION_IDENTIFIER_AVAILABLE, settings.subscriptionIdentifierAvailable())
                    .withAvailableOption(MqttPropertyType.SHARED_SUBSCRIPTION_AVAILABLE, settings.sharedSubscriptionAvailable())
            ;
            // use server keepalive
            if (serverKeepalive > 0) {
                // store server keep alive
                serverKeepalive = Math.min(serverKeepalive, settings.serverKeepaliveIntervalMaximum());
                properties.withProperty(MqttPropertyType.SERVER_KEEP_ALIVE, serverKeepalive);
            } else {
                // store client keep alive
                serverKeepalive = endpoint.keepAliveTimeSeconds();
            }
            // todo: Response Information
            // todo: Authentication Method
            // todo: Authentication Data
            ClientSession session = new DefaultClientSession(vertx, endpoint, sessionExpiryInterval, receiveMaximum, serverKeepalive, messageStore);
            clientSessionStore.add(session);
            session.init();
            log.info("Client session [" + session.identifier() + "] registered. expiry time: " + session.expiryTime());
        }
        Metrics.addClient();
        return ConnectAcknowledge.accept(sessionPresent, properties);
    }

    @Override
    public Optional<ClientSession> get(String clientId) {
        return clientSessionStore.get(clientId);
    }

    @Override
    public void unregister(String clientId, boolean normalClosed) {
        var previous = clientSessionStore.remove(clientId);
        if (previous.isEmpty()) {
            return;
        }
        vertx.eventBus()
                .publish(ChannelKeys.CLIENT_SESSION_CLOSE, new ClientSessionClose(clientId, normalClosed));
    }

    @Override
    public void heartbeat(String clientId) {
        clientSessionStore.get(clientId)
                .ifPresent(ClientSession::heartbeat);
    }

    @Override
    public void cleanSession(String clientId) {
        clientSessionStore.remove(clientId)
                .ifPresent(ClientSession::close);
    }

    @Override
    public void shutdown() {
        vertx.cancelTimer(timerId);
    }

    private void removeExpiredSessions() {
        for (ClientSession session : clientSessionStore.expired()) {
            clientSessionStore.remove(session.identifier());
            session.close();
            vertx.eventBus()
                    .publish(ChannelKeys.CLIENT_SESSION_CLOSE, new ClientSessionClose(session.identifier(), true));
        }
    }
}
