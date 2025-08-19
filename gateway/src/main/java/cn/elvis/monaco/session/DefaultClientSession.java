package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.exception.Exceptions;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.MqttEndpoint;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
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

    private final Queue<PublishMessage> processingQueue;

    private final Instant expiredTime;

    private final MqttEndpoint endpoint;

    private volatile boolean closed;

    public DefaultClientSession(MqttEndpoint endpoint,
                                int expiryInterval,
                                int receiveMaximum) {
        this.endpoint = endpoint;
        this.expiredTime = Instant.now().plusMillis(expiryInterval);
        this.processingQueue = new ArrayBlockingQueue<>(receiveMaximum);
    }

    public void init() {
        Thread.ofVirtual().name("client-queue-worker-" + endpoint.clientIdentifier())
                .uncaughtExceptionHandler((t, e) -> {
                        log.error("[" + t.getName() + "] Unexpected exception:", e);
                        e.printStackTrace(System.err);
                })
                .start(() -> {
                    while (!closed) {
                        PublishMessage message = processingQueue.poll();
                        if (Objects.nonNull(message)) {
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
                        }
                    }
                });
    }

    @Override
    public boolean cleanStart() {
        return endpoint.isCleanSession();
    }

    @Override
    public String identifier() {
        return endpoint.clientIdentifier();
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
    public Future<Void> forward(PublishMessage message) {
        if (message.qos() == MqttQoS.AT_MOST_ONCE) {
            return endpoint.publish(message.topic(), message.payload(), message.qos(), message.duplicate(), message.retain())
                    .map(i -> null);
        }
        if (!processingQueue.offer(message)) {
            return Future.failedFuture(Exceptions.receiveMaximumExceeded());
        }
        return Future.succeededFuture();
    }

    @Override
    public void heartbeat() {
        lastActiveTime.set(Instant.now().toEpochMilli());
    }

    @Override
    public void close() {
        closed = true;
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
}
