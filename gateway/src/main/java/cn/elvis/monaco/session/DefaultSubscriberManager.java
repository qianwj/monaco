package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.SubscribeAcknowledge;
import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.entity.UnsubscribeAcknowledge;
import cn.elvis.monaco.entity.events.SubscriptionExtend;
import cn.elvis.monaco.listener.SystemPublishListener;
import cn.elvis.monaco.listener.WillPublishListener;
import cn.elvis.monaco.manager.SubscriberManager;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.SubscriptionStore;
import cn.elvis.monaco.topics.Topic;
import cn.elvis.monaco.topics.Topics;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Managing subscribers
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultSubscriberManager implements SubscriberManager {

    private static final Logger log = LoggerFactory.getLogger(DefaultSubscriberManager.class);

    private final Map<String, List<Subscription>> store = new ConcurrentHashMap<>();

    private final Map<String, Map<Integer, Boolean>> messageAcknowledgeState = new ConcurrentHashMap<>();

    private final Settings settings;

    private final EventBus eventBus;

    private final SubscriptionStore subscriptionStore;

//    private final WillPublishListener willPublishListener;

//    private final SystemPublishListener systemPublishListener;

    public DefaultSubscriberManager(Settings settings,
                                    EventBus eventBus,
                                    SubscriptionStore subscriptionStore) {
        this.settings = settings;
        this.eventBus = eventBus;
        this.subscriptionStore = subscriptionStore;
//        this.willPublishListener = new WillPublishListener(eventBus, will -> {
//            if (will.body().expired()) {
//                log.info("skip publish will message cause expired.");
//                return;
//            }
//            var msg = will.body();
//            search(msg.topic(), subscription -> subscription.subscriber().forward(msg));
//        });
//        this.systemPublishListener = new SystemPublishListener(eventBus, message ->
//                search(message.topic(), subscription -> {
//                    subscription.subscriber().forward(message);
//                    if (message.qos() != MqttQoS.AT_MOST_ONCE) {
//                        Map<Integer, Boolean> clientMessageState = messageAcknowledgeState.getOrDefault(subscription.sessionId(), new ConcurrentHashMap<>());
//                        clientMessageState.put(message.packetId(), false);
//                        messageAcknowledgeState.put(subscription.sessionId(), clientMessageState);
//                    }
//                }));
    }

    @Override
    public SubscribeAcknowledge subscribe(MqttEndpoint endpoint, MqttSubscribeMessage packet) {
        int subscribeId = MqttPropertiesUtils.intValue(packet.properties(), MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 0);
        if (subscribeId > 0) {
            if (!settings.subscriptionIdentifierAvailable()) {
                List<MqttSubAckReasonCode> reasonCodes = packet.topicSubscriptions()
                        .stream()
                        .map(v -> MqttSubAckReasonCode.SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED)
                        .toList();
                return new SubscribeAcknowledge(packet.messageId(), reasonCodes, MqttPropertiesBuilder.from(packet.properties()));
            }
        }
        List<MqttSubAckReasonCode> reasonCodes = new ArrayList<>();
        List<Subscription> subscriptions = new ArrayList<>();
        for (MqttTopicSubscription mqttTopicSubscription : packet.topicSubscriptions()) {
            MqttQoS qos = mqttTopicSubscription.qualityOfService();
            if (mqttTopicSubscription.qualityOfService().value() > settings.maximumQualityOfService()) {
                qos = MqttQoS.valueOf(settings.maximumQualityOfService());
            }
            if (!Topics.isValidTopicFilter(mqttTopicSubscription.topicName())) {
                reasonCodes.add(MqttSubAckReasonCode.TOPIC_FILTER_INVALID);
                continue;
            }
            if (Topics.isShareTopic(mqttTopicSubscription.topicName()) && !settings.sharedSubscriptionAvailable()) {
                reasonCodes.add(MqttSubAckReasonCode.SHARED_SUBSCRIPTIONS_NOT_SUPPORTED);
                continue;
            }
            if (Topics.isWildcardTopic(mqttTopicSubscription.topicName()) && !settings.wildcardSubscriptionAvailable()) {
                reasonCodes.add(MqttSubAckReasonCode.WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED);
                continue;
            }
            Subscription subscription = Subscription.of(endpoint.clientIdentifier(), qos, mqttTopicSubscription);
            subscriptions.add(subscription);
            boolean reSubscribed = subscriptionStore.exists(endpoint.clientIdentifier(), subscription.topic());
            reasonCodes.add(MqttSubAckReasonCode.qosGranted(subscription.qos()));
            var extend = new SubscriptionExtend(
                    endpoint.clientIdentifier(),
                    subscription.topic(),
                    reSubscribed,
                    subscription.noLocal(),
                    subscription.retainAsPublished(),
                    subscription.retainedHandlingPolicy()
            );
            eventBus.publish(ChannelKeys.CLIENT_SESSION_SUBSCRIBE, extend);
        }
        subscriptionStore.addSubscriptions(endpoint.clientIdentifier(), subscribeId, subscriptions);
        return new SubscribeAcknowledge(packet.messageId(), reasonCodes, MqttPropertiesBuilder.from(packet.properties()));
    }

    @Override
    public UnsubscribeAcknowledge unsubscribe(MqttEndpoint endpoint, MqttUnsubscribeMessage packet) {
        Set<String> validTopicFilters = new HashSet<>();
        List<MqttUnsubAckReasonCode> reasonCodes = new ArrayList<>();
        for (String topicFilter : packet.topics()) {
            if (Topics.isValidTopicFilter(topicFilter)) {
                reasonCodes.add(MqttUnsubAckReasonCode.TOPIC_FILTER_INVALID);
                continue;
            }
            if (!subscriptionStore.exists(endpoint.clientIdentifier(), Topics.createTopic(topicFilter))) {
                reasonCodes.add(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED);
                continue;
            }
            validTopicFilters.add(topicFilter);
            reasonCodes.add(MqttUnsubAckReasonCode.SUCCESS);
        }
        if (!validTopicFilters.isEmpty()) {
            var removed = subscriptionStore.removeSubscriptions(endpoint.clientIdentifier(), validTopicFilters);
            // todo: handle removed subscriptions
        }
        return new UnsubscribeAcknowledge(packet.messageId(), reasonCodes, MqttPropertiesBuilder.from(packet.properties()));
    }

    @Override
    public boolean exists(String filter) {
        return store.containsKey(filter);
    }

    @Override
    public void close() {
        willPublishListener.close();
        systemPublishListener.close();
    }
}
