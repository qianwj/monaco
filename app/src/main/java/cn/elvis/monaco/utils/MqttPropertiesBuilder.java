package cn.elvis.monaco.utils;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.buffer.Buffer;

import java.util.Objects;

public final class MqttPropertiesBuilder {

    private final MqttProperties properties = new MqttProperties();

    private MqttPropertiesBuilder() {}

    public static MqttPropertiesBuilder create() {
        return new MqttPropertiesBuilder();
    }

    public MqttPropertiesBuilder withReasonString(String reason) {
        if (Objects.isNull(reason) || reason.isBlank()) {
            return this;
        }
        var property = new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.REASON_STRING.value(),
                reason
        );
        properties.add(property);
        return this;
    }

    public MqttPropertiesBuilder withAuthenticationMethod(String method) {
        if (Objects.isNull(method) || method.isBlank()) {
            return this;
        }
        var property = new MqttProperties.StringProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(),
                method
        );
        properties.add(property);
        return this;
    }

    public MqttPropertiesBuilder withAuthenticationData(Buffer data) {
        if (data == null || data.length() == 0) {
            return this;
        }
        var property = new MqttProperties.BinaryProperty(
                MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(),
                data.getBytes()
        );
        properties.add(property);
        return this;
    }

    public MqttProperties build() {
        return properties;
    }
}
