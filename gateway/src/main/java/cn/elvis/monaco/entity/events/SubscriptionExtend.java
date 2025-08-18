package cn.elvis.monaco.entity.events;

import cn.elvis.monaco.topics.Topic;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;

public record SubscriptionExtend(
        String clientId,
        Topic topic,
        boolean reSubscribe,
        boolean noLocal,
        boolean retainAsPublished,
        MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy
) {}
