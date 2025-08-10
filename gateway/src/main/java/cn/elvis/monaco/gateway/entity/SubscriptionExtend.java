package cn.elvis.monaco.gateway.entity;

import io.netty.handler.codec.mqtt.MqttSubscriptionOption;

public record SubscriptionExtend(
        String clientId,
        String topicFilter,
        boolean reSubscribe,
        boolean noLocal,
        boolean retainAsPublished,
        MqttSubscriptionOption.RetainedHandlingPolicy retainedHandlingPolicy
) {}
