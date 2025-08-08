package cn.elvis.monaco.gateway.entity;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.MqttWill;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class WillMessageImpl implements WillMessage {

    private final PublishMessage body;

    private final Duration delayInterval;

    private final Instant expiryTime;

    WillMessageImpl(MqttWill will) {
        this.body = PublishMessage.of(-1, will);
        this.delayInterval = Optional.ofNullable(
                        will.getWillProperties()
                                .getProperty(MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value())
                ).map(MqttProperties.MqttProperty::value)
                .map(v -> Duration.ofMillis((int) v))
                .orElse(Duration.ZERO);
        this.expiryTime = Optional.ofNullable(
                        will.getWillProperties()
                                .getProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value())
                )
                .map(MqttProperties.MqttProperty::value)
                .map(v -> Instant.now().plusMillis((int) v))
                .orElse(null);
    }

    @Override
    public Duration delayInterval() {
        return delayInterval;
    }

    @Override
    public Instant expiryTime() {
        return expiryTime;
    }

    @Override
    public boolean expired() {
        if (Objects.isNull(expiryTime)) {
            return false;
        }
        return expiryTime.isAfter(Instant.now());
    }

    @Override
    public PublishMessage body() {
        return body;
    }
}
