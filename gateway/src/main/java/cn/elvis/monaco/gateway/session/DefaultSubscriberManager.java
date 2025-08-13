package cn.elvis.monaco.gateway.session;

import cn.elvis.monaco.gateway.ChannelKeys;
import cn.elvis.monaco.gateway.entity.Subscription;
import cn.elvis.monaco.gateway.entity.events.SubscriptionExtend;
import cn.elvis.monaco.gateway.listener.SystemPublishListener;
import cn.elvis.monaco.gateway.listener.WillPublishListener;
import cn.elvis.monaco.gateway.manager.SubscriberManager;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

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

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriberManager.class);

    private final Map<String, List<Subscription>> store = new ConcurrentHashMap<>();

    private final EventBus eventBus;

    private final WillPublishListener willPublishListener;

    private final SystemPublishListener systemPublishListener;

    public DefaultSubscriberManager(EventBus eventBus) {
        this.eventBus = eventBus;
        this.willPublishListener = new WillPublishListener(eventBus, will -> {
            if (will.body().expired()) {
                log.info("skip publish will message cause expired.");
                return;
            }
            var msg = will.body();
            search(msg.topic(), subscription -> subscription.subscriber().forward(msg));
        });
        this.systemPublishListener = new SystemPublishListener(eventBus, message ->
                search(message.topic(), subscription -> subscription.subscriber().forward(message)));
    }

    @Override
    public MqttSubAckReasonCode subscribe(ClientSession clientSession, Subscription subscription) {
        List<Subscription> subscribers = store.getOrDefault(subscription.topicFilter(), new CopyOnWriteArrayList<>());
        subscribers.add(subscription);
        store.put(subscription.topicFilter(), subscribers);
        var extend = new SubscriptionExtend(
                clientSession.identifier(),
                subscription.topicFilter(),
                clientSession.isReSubscribed(subscription.topicFilter()),
                subscription.noLocal(),
                subscription.retainAsPublished(),
                subscription.retainedHandlingPolicy()
        );
        clientSession.subscribe(subscription);
        eventBus.publish(ChannelKeys.CLIENT_SESSION_SUBSCRIBE, extend);
        return MqttSubAckReasonCode.qosGranted(subscription.qos());
    }

    @Override
    public MqttUnsubAckReasonCode unsubscribe(ClientSession clientSession, String topicFilter) {
        List<Subscription> subscribers = store.getOrDefault(topicFilter, new CopyOnWriteArrayList<>());
        subscribers.removeIf(subscription -> subscription.subscriber().identifier().equals(clientSession.identifier()));
        if (subscribers.isEmpty()) {
            store.remove(topicFilter);
        }
        clientSession.unsubscribe(topicFilter);
        return MqttUnsubAckReasonCode.SUCCESS;
    }

    @Override
    public void search(String filter, Consumer<Subscription> consumer) {
        store.getOrDefault(filter, new ArrayList<>()).forEach(consumer);
    }

    @Override
    public void close() {
        willPublishListener.close();
        systemPublishListener.close();
    }
}
