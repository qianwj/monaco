package cn.elvis.monaco.gateway.entity;

import io.vertx.mqtt.MqttWill;

import java.time.Duration;
import java.time.Instant;

public interface WillMessage {

    Duration delayInterval();

    Instant expiryTime();

    boolean expired();

    PublishMessage body();

    static WillMessage create(MqttWill will) {
        return new WillMessageImpl(will);
    }
}
