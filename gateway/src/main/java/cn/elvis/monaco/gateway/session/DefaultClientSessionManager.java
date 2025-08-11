package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.events.ClientSessionClose;
import cn.elvis.monaco.gateway.settings.Settings;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default client session manager implementations, use Hash Map store client session.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultClientSessionManager implements ClientSessionManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSessionManager.class);

    private final Map<String, ClientSession> store = new ConcurrentHashMap<>();

    private final Settings settings;

    private final EventBus eventBus;

    public DefaultClientSessionManager(Settings settings, Vertx vertx) {
        this.settings = settings;
        this.eventBus = vertx.eventBus();
        vertx.setTimer(1000, id -> {
            removeExpiredSessions();
        });
    }

    @Override
    public boolean sessionPresent(String clientId) {
        return store.containsKey(clientId);
    }

    @Override
    public MqttConnectReturnCode register(ClientSession clientSession) {
        removeExpiredSessions();
        if (clientSession.cleanStart()) {
            ClientSession previous = store.remove(clientSession.identifier());
            if (Objects.nonNull(previous)) {
                previous.close();
            }
        } else if (store.containsKey(clientSession.identifier())) {
            return MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;
        }
        if (store.size() + 1 <= settings.maximumSessionCount()) {
            store.put(clientSession.identifier(), clientSession);
            clientSession.connect();
            log.info("Client session [" + clientSession.identifier() + "] registered. expiry time: " + clientSession.expiryTime());
            return MqttConnectReturnCode.CONNECTION_ACCEPTED;
        }
        return MqttConnectReturnCode.CONNECTION_REFUSED_CONNECTION_RATE_EXCEEDED;
    }

    @Override
    public void unregister(String clientId, boolean normalClosed) {
        store.remove(clientId);
        eventBus.publish(ChannelKeys.CLIENT_SESSION_CLOSE, new ClientSessionClose(clientId, normalClosed));
    }

    @Override
    public void heartbeat(String clientId) {
        ClientSession clientSession = store.get(clientId);
        if (clientSession != null) {
            clientSession.heartbeat();
        }
    }

    @Override
    public int sessionCount() {
        return store.size();
    }

    private void removeExpiredSessions() {
        for (Map.Entry<String, ClientSession> entry : store.entrySet()) {
            if (entry.getValue().isExpired()) {
                store.remove(entry.getKey());
            }
        }
    }
}
