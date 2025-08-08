package cn.elvis.monaco.topics;

import io.netty.handler.codec.mqtt.MqttQoS;

public record Subscription(String clientId, MqttQoS qos) {}