package cn.elvis.monaco.manager;

import cn.elvis.monaco.entity.ConnectAcknowledge;
import cn.elvis.monaco.session.ClientSession;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.mqtt.MqttEndpoint;

import java.util.Optional;

/**
 * Managing client sessions that store client information and state.
 * @author qianwj
 * @since  0.0.1
 */
public interface ClientSessionManager extends Manager {

    /**
     * Check clients contains current client identifier
     * @param clientId: client identifier
     * @return true is present
     */
    boolean sessionPresent(String clientId);

    /**
     * register this client to manager
     * @param endpoint mqtt client connection
     * @return ConnectAcknowledge
     */
    ConnectAcknowledge register(MqttEndpoint endpoint);

    /**
     * get client session
     * @param clientId mqtt client id
     * @return Optional client session, not be null.
     */
    Optional<ClientSession> get(String clientId);

    /**
     * When client close or occur exceptions, remove this client from store and notify other managers
     * that client connection closed.
     * @param clientId: client identifier
     */
    void unregister(String clientId, boolean normalClosed);

    /**
     * Client heartbeat, refresh last active time of this client
     * @param clientId: client identifier
     */
    void heartbeat(String clientId);

    void cleanSession(String clientId);
}
