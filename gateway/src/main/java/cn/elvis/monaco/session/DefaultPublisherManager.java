package cn.elvis.monaco.session;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.exception.Exceptions;
import cn.elvis.monaco.exception.ProtocolException;
import cn.elvis.monaco.listener.ClientSessionCloseListener;
import cn.elvis.monaco.manager.PublisherManager;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.TopicAliasStore;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * PublisherManager implementations
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultPublisherManager implements PublisherManager {

    private final Map<String, Queue<PublishMessage>> messageQueue = new ConcurrentHashMap<>();

    private final Settings settings;

    private final TopicAliasStore topicAliasStore;

    private final Vertx vertx;

    private final ClientSessionCloseListener clientSessionCloseListener;

    public DefaultPublisherManager(Settings settings, TopicAliasStore topicAliasStore, Vertx vertx) {
        this.settings = settings;
        this.topicAliasStore = topicAliasStore;
        this.vertx = vertx;
        this.clientSessionCloseListener = new ClientSessionCloseListener(vertx.eventBus(), event -> {
           if (event.normalClosed()) {
               topicAliasStore.clearTopicAlias(event.clientId());
           }
        });
    }

    @Override
    public void setTopicAliasMaximum(String clientId, int topicAliasMaximum) {
        topicAliasStore.setTopicAliasMaximum(clientId, topicAliasMaximum);
    }

    @Override
    public Future<PublishMessage> publish(MqttEndpoint endpoint,
                                          MqttPublishMessage packet,
                                          Predicate<String> subscriberExists) {
        String clientId = endpoint.clientIdentifier();
        int topicAlias = MqttPropertiesUtils.intValue(packet.properties(), MqttPropertyType.TOPIC_ALIAS, 0);
        String topic;
        if (topicAlias > topicAliasStore.topicAliasMaximum(clientId)) {
            return Future.failedFuture(Exceptions.invalidTopicAlias(topicAlias));
        }
        if (!StringUtil.isNullOrEmpty(packet.topicName())) {
            topicAliasStore.addTopicAlias(clientId, packet.topicName(), topicAlias);
            topic = packet.topicName();
        } else {
            topic = topicAliasStore.getTopic(clientId, topicAlias);
        }
        if (StringUtil.isNullOrEmpty(topic)) {
            return Future.failedFuture(Exceptions.topicNameInvalid(topic));
        }
        if (!subscriberExists.test(topic)) {
            return Future.failedFuture(Exceptions.noMatchingSubscribers(topic));
        }
        PublishMessage message = PublishMessage.of(packet, topic, packet.isDup(), packet.isRetain());
        if (message.qos() == MqttQoS.EXACTLY_ONCE) {
            endpoint.publishReceived(message.packetId());
        } else {
            endpoint.publishAcknowledge(message.packetId());
        }
        if (!message.retain()) {
            Queue<PublishMessage> clientMessageQueue = messageQueue.getOrDefault(clientId, new ArrayBlockingQueue<>(settings.publishQueueMaximum()));
            if (!clientMessageQueue.offer(message)) {
                return Future.failedFuture(Exceptions.publishQueueFulfilled());
            }
            messageQueue.put(clientId, clientMessageQueue);
            vertx.eventBus().publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, message);
        }
        return Future.succeededFuture(message);
    }

    public void reject(MqttEndpoint endpoint, Throwable exception, MqttPublishMessage packet) {
        byte reasonCode = switch (exception) {
            case ProtocolException protocolException -> (byte) protocolException.code();
            default -> MqttPubAckReasonCode.UNSPECIFIED_ERROR.value();
        };
        if (packet.qosLevel() == MqttQoS.EXACTLY_ONCE) {
            endpoint.publishReceived(packet.messageId(), MqttPubRecReasonCode.valueOf(reasonCode), packet.properties());
        } else {
            endpoint.publishAcknowledge(packet.messageId(), MqttPubAckReasonCode.valueOf(reasonCode), packet.properties());
        }
    }

    @Override
    public void close() {
        clientSessionCloseListener.close();
    }
}
