package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.exception.Exceptions;
import cn.elvis.monaco.exception.ProtocolException;
import cn.elvis.monaco.store.MessageStore;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Vertx;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Default client session implementations, wrapped MqttEndpoint instance and maintained state.
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultClientSession implements ClientSession {

    private static final Logger log = LoggerFactory.getLogger(DefaultClientSession.class);

    private final AtomicLong lastActiveTime = new AtomicLong(Instant.now().toEpochMilli());

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    private final Instant expiredTime;

    private final Vertx vertx;

    private final MqttEndpoint endpoint;

    private final MessageStore messageStore;

    private final Set<Integer> bucket;

    private final int receiveMaximum;

    private final boolean requestResponseInformation;

    private final long heartbeatCheckerId;

    private volatile boolean closed;

    private volatile boolean authorized;

    public DefaultClientSession(Vertx vertx,
                                MqttEndpoint endpoint,
                                int expiryInterval,
                                int receiveMaximum,
                                int keepaliveInterval,
                                boolean requestResponseInformation,
                                boolean authorized,
                                MessageStore messageStore) {
        this.endpoint = endpoint;
        this.vertx = vertx;
        this.expiredTime = Instant.now().plusMillis(expiryInterval);
        this.messageStore = messageStore;
        this.bucket = new HashSet<>();
        this.receiveMaximum = receiveMaximum;
        this.requestResponseInformation = requestResponseInformation;
        this.heartbeatCheckerId = setHeartbeatChecker(vertx, keepaliveInterval);
        this.authorized = authorized;
    }

    public void init() {
        Thread.ofVirtual().name("client-queue-worker-" + endpoint.clientIdentifier())
                .uncaughtExceptionHandler((t, e) -> {
                        log.error("[" + t.getName() + "] Unexpected exception:", e);
                        e.printStackTrace(System.err);
                })
                .start(() -> {
                    while (!closed) {
                        messageStore.poll(identifier()).ifPresent(message -> {
                            endpoint.publish(
                                    message.topic(),
                                    message.payload(),
                                    message.qos(),
                                    message.duplicate(),
                                    message.retain(),
                                    message.packetId(),
                                    message.properties()
                            );
                            heartbeat();
                        });
                    }
                });
    }

    @Override
    public String identifier() {
        return endpoint.clientIdentifier();
    }

    @Override
    public boolean authorized() {
        return authorized;
    }

    @Override
    public boolean isExpired() {
        return expiredTime.isAfter(Instant.now());
    }

    @Override
    public ZonedDateTime expiryTime() {
        return expiredTime.atZone(ZoneId.systemDefault());
    }

    @Override
    public synchronized void push(PublishMessage message) throws ProtocolException {
        switch (message.qos()) {
            case AT_MOST_ONCE:
                endpoint.publish(message.topic(), message.payload(), message.qos(), message.duplicate(), message.retain(), message.packetId(), message.properties());
                break;
            case AT_LEAST_ONCE, EXACTLY_ONCE:
                if (bucket.size() < receiveMaximum) {
                    bucket.add(message.packetId());
                    messageStore.push(identifier(), message);
                    endpoint.publish(message.topic(), message.payload(), message.qos(), message.duplicate(), message.retain(), message.packetId(), message.properties());
                } else {
                    throw Exceptions.receiveMaximumExceeded();
                }
                break;
        }
    }

    @Override
    public void releasePush(int packetId) {
        endpoint.publishRelease(packetId, MqttPubRelReasonCode.SUCCESS, new MqttProperties());
    }

    @Override
    public void ack(int packedId) {
        bucket.remove(packedId);
    }

    @Override
    public boolean requestResponseInformation() {
        return requestResponseInformation;
    }

    @Override
    public void heartbeat() {
        lastActiveTime.set(Instant.now().toEpochMilli());
    }

    @Override
    public void close() {
        closed = true;
        vertx.cancelTimer(heartbeatCheckerId);
        endpoint.close();
    }

    @Override
    public void subscribe(Subscription subscription) {
        subscriptions.add(subscription);
    }

    @Override
    public void unsubscribe(String topicFilter) {
//        subscriptions.removeIf(subscription -> subscription.topicFilter().equals(topicFilter));
    }

    @Override
    public boolean isReSubscribed(String topicFilter) {
        for (Subscription subscription : subscriptions) {
//            if (Objects.equals(topicFilter, subscription.topicFilter())) {
//                return true;
//            }
        }
        return false;
    }

    private long setHeartbeatChecker(Vertx vertx, int keepaliveSeconds) {
        return vertx.setTimer(keepaliveSeconds * 1500L, timerId -> {
            if (!Duration.between(Instant.now(), Instant.ofEpochMilli(lastActiveTime.get())).isPositive()) {
                // timeout, should close connection
                close();
            }
        });
    }
}
