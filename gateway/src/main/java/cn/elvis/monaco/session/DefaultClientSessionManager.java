package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.ConnectAcknowledge;
import cn.elvis.monaco.entity.events.ClientSessionClose;
import cn.elvis.monaco.manager.ClientSessionManager;
import cn.elvis.monaco.metrics.Metrics;
import cn.elvis.monaco.settings.Settings;
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
import io.vertx.mqtt.MqttEndpoint;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default client session manager implementations, use Hash Map store client session.
 * It's not a cluster mode, so `Server Reference` property will not be implemented.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultClientSessionManager implements ClientSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSessionManager.class);

    private final Map<String, ClientSession> store = new ConcurrentHashMap<>();

    private final Settings settings;

    private final Vertx vertx;

    private final TopicAliasStore topicAliasStore;

    private final long timerId;

    public DefaultClientSessionManager(Settings settings,
                                       Vertx vertx,
                                       TopicAliasStore topicAliasStore) {
        this.settings = settings;
        this.vertx = vertx;
        this.topicAliasStore = topicAliasStore;
        this.timerId = vertx.setTimer(1000, id -> removeExpiredSessions());
    }

    @Override
    public boolean sessionPresent(String clientId) {
        return store.containsKey(clientId);
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
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
            return ConnectAcknowledge.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, properties);
        }
        log.info("New client incoming: " + endpoint.clientIdentifier() + ", clean start: " + endpoint.isCleanSession());
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
        if (store.size() + 1 <= settings.maximumSessionCount()) {
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
            ;
            // todo: Shared Subscription Available
            // todo: Server Keep Alive
            // todo: Response Information
            // todo: Authentication Method
            // todo: Authentication Data
            ClientSession session = new DefaultClientSession(endpoint, sessionExpiryInterval, receiveMaximum);
            store.put(endpoint.clientIdentifier(), session);
            session.init();
            log.info("Client session [" + session.identifier() + "] registered. expiry time: " + session.expiryTime());
        }
        Metrics.addClient();
        return ConnectAcknowledge.accept(sessionPresent, properties);
    }

    @Override
    public Optional<ClientSession> get(String clientId) {
        return Optional.ofNullable(store.get(clientId));
    }

    @Override
    public void unregister(String clientId, boolean normalClosed) {
        var previous = store.remove(clientId);
        if (Objects.isNull(previous)) {
            return;
        }
        vertx.eventBus()
                .publish(ChannelKeys.CLIENT_SESSION_CLOSE, new ClientSessionClose(clientId, normalClosed));
    }

    @Override
    public void heartbeat(String clientId) {
        ClientSession clientSession = store.get(clientId);
        if (clientSession != null) {
            clientSession.heartbeat();
        }
    }

    @Override
    public void cleanSession(String clientId) {
        ClientSession previous = store.remove(clientId);
        if (Objects.nonNull(previous)) {
            System.out.println("disconnect:" + previous.identifier());
            previous.close();
        }
    }

    @Override
    public int sessionCount() {
        return store.size();
    }

    @Override
    public void close() {
        vertx.cancelTimer(timerId);
    }

    private void removeExpiredSessions() {
        for (Map.Entry<String, ClientSession> entry : store.entrySet()) {
            if (entry.getValue().isExpired()) {
                store.remove(entry.getKey());
                entry.getValue().close();
            }
        }
    }
}
