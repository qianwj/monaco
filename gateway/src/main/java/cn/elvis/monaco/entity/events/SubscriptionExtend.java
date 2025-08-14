package cn.elvis.monaco.entity.events;

import io.netty.handler.codec.mqtt.MqttSubscriptionOption;

public record SubscriptionExtend(
        String clientId,
        String topicFilter,
        boolean reSubscribe,
        boolean noLocal,
        boolean retainAsPublished,
        MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy
) {}
