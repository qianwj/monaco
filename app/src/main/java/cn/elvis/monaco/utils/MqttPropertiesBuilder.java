package cn.elvis.monaco.utils;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.buffer.Buffer;

import java.util.Objects;

/**
 * Utility Tools for build mqtt properties
 *
 * @author qianwj
 * @since  0.0.1
 */
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

    public MqttPropertiesBuilder withAvailableOption(MqttProperties.MqttPropertyType propertyType) {
        if (Objects.isNull(propertyType)) {
            return this;
        }
        var property = new ByteProperty(propertyType.value(), (byte) 1);
        properties.add(property);
        return this;
    }

    public MqttProperties build() {
        return properties;
    }

    private static class ByteProperty extends MqttProperties.MqttProperty<Byte> {

        protected ByteProperty(int propertyId, Byte value) {
            super(propertyId, value);
        }
    }
}
