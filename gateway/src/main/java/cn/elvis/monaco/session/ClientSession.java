package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.exception.ProtocolException;
import io.vertx.core.Future;

import java.time.ZonedDateTime;

/**
 * Client session
 * @author qianwj
 * @since  0.0.1
 */
public interface ClientSession extends Subscriber {

    void init();

    /**
     * Method that return current client identifier.
     * @return Client identifier
     */
    String identifier();


    boolean authorized();

    boolean isExpired();

    ZonedDateTime expiryTime();

    void push(PublishMessage message) throws ProtocolException;

    void releasePush(int packetId);

    void ack(int packedId);

    boolean requestResponseInformation();

    void heartbeat();

    void close();

}
