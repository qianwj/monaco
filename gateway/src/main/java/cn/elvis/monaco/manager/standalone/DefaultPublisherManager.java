package cn.elvis.monaco.manager.standalone;

import cn.elvis.monaco.ChannelKeys;
import cn.elvis.monaco.entity.ack.*;
import cn.elvis.monaco.entity.ack.PublishExchangeAcknowledge.ReasonCode;
import cn.elvis.monaco.entity.PublishMessage;
import cn.elvis.monaco.listener.ClientSessionCloseListener;
import cn.elvis.monaco.manager.PublisherManager;
import cn.elvis.monaco.settings.Settings;
import cn.elvis.monaco.store.ClientSessionStore;
import cn.elvis.monaco.store.RetainMessageStore;
import cn.elvis.monaco.store.TopicAliasStore;
import cn.elvis.monaco.topics.Topics;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.internal.StringUtil;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * PublisherManager implementations
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class DefaultPublisherManager implements PublisherManager {

    private final Settings settings;

    private final ClientSessionStore clientSessionStore;

    private final TopicAliasStore topicAliasStore;

    private final RetainMessageStore retainMessageStore;

    private final Vertx vertx;

    private final ClientSessionCloseListener clientSessionCloseListener;

    public DefaultPublisherManager(Settings settings,
                                   Vertx vertx,
                                   ClientSessionStore clientSessionStore,
                                   TopicAliasStore topicAliasStore,
                                   RetainMessageStore retainMessageStore) {
        this.settings = settings;
        this.clientSessionStore = clientSessionStore;
        this.vertx = vertx;
        this.topicAliasStore = topicAliasStore;
        this.retainMessageStore = retainMessageStore;
        this.clientSessionCloseListener = new ClientSessionCloseListener(vertx.eventBus(), event -> {
           if (event.normalClosed()) {
               topicAliasStore.clearTopicAlias(event.clientId());
           }
        });
    }

    @Override
    public PublishExchangeAcknowledge publish(MqttEndpoint endpoint, MqttPublishMessage packet) {
        String clientId = endpoint.clientIdentifier();
        MqttPropertiesBuilder properties = MqttPropertiesBuilder.from(packet.properties());
        int topicAlias = MqttPropertiesUtils.intValue(packet.properties(), MqttProperties.TOPIC_ALIAS, 0);
        if (topicAlias > topicAliasStore.topicAliasMaximum(clientId)) {
            return new PublishAcknowledge(packet.messageId(), ReasonCode.IMPLEMENTATION_SPECIFIC_ERROR, properties);
        }
        int subscribeId = MqttPropertiesUtils.intValue(packet.properties(), MqttProperties.SUBSCRIPTION_IDENTIFIER, 0);
        if (subscribeId > 0 && !settings.subscriptionIdentifierAvailable()) {
            return new PublishAcknowledge(packet.messageId(), ReasonCode.IMPLEMENTATION_SPECIFIC_ERROR, properties);
        }
        String topic;
        if (!StringUtil.isNullOrEmpty(packet.topicName())) {
            if (!Topics.isValidTopicFilter(packet.topicName())) {
                return new PublishAcknowledge(packet.messageId(), ReasonCode.TOPIC_NAME_INVALID, properties);
            }
            topicAliasStore.addTopicAlias(clientId, packet.topicName(), topicAlias);
            topic = packet.topicName();
        } else {
            topic = topicAliasStore.getTopic(clientId, topicAlias);
        }
        MqttQoS qos = packet.qosLevel().value() > settings.maximumQualityOfService()
                ? MqttQoS.valueOf(settings.maximumQualityOfService()) : packet.qosLevel();
        PublishMessage message = PublishMessage.of(packet, topic, qos, packet.isDup(), packet.isRetain(), endpoint.clientIdentifier());
        if (message.retain()) {
            if (!settings.retainAvailable()) {
                return new PublishAcknowledge(packet.messageId(), ReasonCode.IMPLEMENTATION_SPECIFIC_ERROR, properties);
            }
            retainMessageStore.setRetain(message);
        }
        vertx.eventBus().publish(ChannelKeys.MESSAGE_PUBLISH_CHANNEL, message);
        if (qos == MqttQoS.EXACTLY_ONCE) {
            return new PublishReceived(packet.messageId(), ReasonCode.SUCCESS, properties);
        } else {
            return new PublishAcknowledge(packet.messageId(), ReasonCode.SUCCESS, properties);
        }
    }

    /**
     * When client send a PUBREC to broker, broker should send a PUBREL to client.
     * @param endpoint
     * @param packetId
     */
    @Override
    public PublishRelease publishReceived(MqttEndpoint endpoint, int packetId) {
        clientSessionStore.get(endpoint.clientIdentifier())
                .ifPresent(session -> session.ack(packetId));
        return new PublishRelease(packetId, ReasonCode.SUCCESS, MqttPropertiesBuilder.create());
    }

    /**
     * When receive REBREL packet from publisher, broker should forward this message to subscribers.
     * @param packetId
     */
    @Override
    public PublishComplete releasePublish(int packetId) {
        JsonObject release = new JsonObject();
        release.put("packetId", packetId);
        release.put("type", "release");
        vertx.eventBus().publish(ChannelKeys.PUBLISH_RELEASE_CHANNEL, release);
        return new PublishComplete(packetId, ReasonCode.SUCCESS, MqttPropertiesBuilder.create());
    }

    public void cleanPublish(int packetId) {
        JsonObject complete = new JsonObject();
        complete.put("packetId", packetId);
        complete.put("type", "complete");
        vertx.eventBus().publish(ChannelKeys.PUBLISH_RELEASE_CHANNEL, complete);
    }

    @Override
    public void shutdown() {
        clientSessionCloseListener.close();
    }
}
