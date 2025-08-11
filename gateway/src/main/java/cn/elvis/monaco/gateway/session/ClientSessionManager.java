package cn.elvis.monaco.gateway.session;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;

/**
 * Managing client sessions that store client information and state.
 * @author qianwj
 * @since  0.0.1
 */
public interface ClientSessionManager {

    /**
     * Check clients contains current client identifier
     * @param clientId: client identifier
     * @return true is present
     */
    boolean sessionPresent(String clientId);

    /**
     * register this client to manager
     * @param clientSession mqtt client session
     * @return MqttConnectReturnCode
     */
    MqttConnectReturnCode register(ClientSession clientSession);

    /**
     * When client close or occur exceptions, remove this client from store and notify other managers
     * that client connection closed.
     * @param clientId: client identifier
     */
    void unregister(String clientId, boolean normalClosed);

    /**
     * client heartbeat, refresh last active time of this client
     * @param clientId: client identifier
     */
    void heartbeat(String clientId);

    int sessionCount();
}
