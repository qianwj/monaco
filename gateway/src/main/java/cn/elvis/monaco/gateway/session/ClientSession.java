package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.PublishMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Future;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;

import java.time.Instant;

/**
 * Client session
 * @author qianwj
 * @since  0.0.1
 */
public interface ClientSession extends Subscriber {

    void connect();

    /**
     * Method that return current client identifier.
     * @return Client identifier
     */
    String identifier();

    /**
     * If a CONNECT packet is received with Clean Start is set to 1, the Client and Server MUST discard any existing Session and start a new Session [MQTT-3.1.2-4]. Consequently, the Session Present flag in CONNACK is always set to 0 if Clean Start is set to 1.
     * If a CONNECT packet is received with Clean Start set to 0 and there is a Session associated with the Client Identifier, the Server MUST resume communications with the Client based on state from the existing Session [MQTT-3.1.2-5].
     * If a CONNECT packet is received with Clean Start set to 0 and there is no Session associated with the Client Identifier, the Server MUST create a new Session [MQTT-3.1.2-6].
     * @return true need to remove present client session.
     */
    boolean cleanStart();

    boolean isExpired();

    Instant expiryTime();

    Future<Void> forward(PublishMessage message);

    void heartbeat();

    void close();

}
