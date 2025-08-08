package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.entity.Subscription;
import cn.elvis.monaco.gateway.listener.WillPublishListener;
import io.vertx.core.eventbus.EventBus;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Managing subscribers
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultSubscriberManager implements SubscriberManager {

    private final Map<String, List<Subscription>> store = new ConcurrentHashMap<>();

    private final WillPublishListener willPublishListener;

    public DefaultSubscriberManager(EventBus eventBus) {
        this.willPublishListener = new WillPublishListener(eventBus, will -> {
            if (will.expired()) {
                return;
            }
            var msg = will.body();
            search(msg.topic(), subscription -> {
                subscription.subscriber().forward(msg);
            });
        });
    }

    @Override
    public MqttSubAckReasonCode subscribe(ClientSession clientSession, Subscription subscription) {
        List<Subscription> subscribers = store.getOrDefault(subscription.topicFilter(), new CopyOnWriteArrayList<>());
        subscribers.add(subscription);
        store.put(subscription.topicFilter(), subscribers);
        return MqttSubAckReasonCode.qosGranted(subscription.qos());
    }

    @Override
    public void search(String filter, Consumer<Subscription> consumer) {
        store.getOrDefault(filter, new ArrayList<>()).forEach(consumer);
    }

    @Override
    public void close() {
        willPublishListener.close();
    }
}
