package cn.elvis.monaco.gateway.entity;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.MqttWill;

import java.time.Duration;
import java.util.Optional;

/**
 * Will message implementation
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class WillMessageImpl implements WillMessage {

    private final PublishMessage body;

    private final Duration delayInterval;

    WillMessageImpl(MqttWill will) {
        this.body = PublishMessage.of(-1, will);
        this.delayInterval = Optional.ofNullable(
                        will.getWillProperties()
                                .getProperty(MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value())
                ).map(MqttProperties.MqttProperty::value)
                .map(v -> Duration.ofMillis((int) v))
                .orElse(Duration.ZERO);
    }

    @Override
    public Duration delayInterval() {
        return delayInterval;
    }

    @Override
    public PublishMessage body() {
        return body;
    }
}
