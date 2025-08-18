package cn.elvis.monaco.session;

import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.exception.Exceptions;
import cn.elvis.monaco.settings.Settings;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
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
import java.util.function.Supplier;

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
                                Settings settings) {
        this.endpoint = endpoint;
        this.expiredTime = Instant.now()
                .plusMillis(sessionExpiryInterval(settings));
        this.processingQueue = new ArrayBlockingQueue<>(receiveMaximum(settings));
    }

    public void connect() {
        endpoint.accept(true);
        Thread.ofVirtual().name("client-queue-worker-" + endpoint.clientIdentifier())
                .uncaughtExceptionHandler((t, e) -> {
                        log.error("[" + t.getName() + "] Unexpected exception", e);
                        e.printStackTrace();
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

    private int sessionExpiryInterval(Settings settings) {
        int sessionExpiryInterval = intValue(MqttPropertyType.SESSION_EXPIRY_INTERVAL, settings::defaultSessionExpiryInterval);
        if (sessionExpiryInterval > settings.maxSessionExpiryInterval()) {
            return settings.defaultSessionExpiryInterval();
        }
        return sessionExpiryInterval;
    }

    private int receiveMaximum(Settings settings) {
        return intValue(MqttPropertyType.RECEIVE_MAXIMUM, settings::defaultReceiveMaximum);
    }

    @SuppressWarnings("unchecked")
    private int intValue(MqttPropertyType propertyType, Supplier<Integer> defaultValueSupplier) {
        return Optional.ofNullable(
                (MqttProperties.MqttProperty<Integer>)
                        endpoint.connectProperties().getProperty(propertyType.value())
                )
                .map(MqttProperties.MqttProperty::value)
                .orElseGet(defaultValueSupplier);
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
