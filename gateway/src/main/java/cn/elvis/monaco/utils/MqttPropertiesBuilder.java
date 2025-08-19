package cn.elvis.monaco.utils;

import io.netty.handler.codec.mqtt.MqttProperties;

import java.util.Objects;

/**
 * Utility Tools for build mqtt properties
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class MqttPropertiesBuilder {

    private final MqttProperties properties;

    private MqttPropertiesBuilder() {
        this(new MqttProperties());
    }

    private MqttPropertiesBuilder(MqttProperties properties) {
        this.properties = properties;
    }

    public static MqttPropertiesBuilder create() {
        return new MqttPropertiesBuilder();
    }

    public static MqttPropertiesBuilder from(MqttProperties properties) {
        return new MqttPropertiesBuilder(properties);
    }

    public MqttPropertiesBuilder withProperty(MqttProperties.MqttPropertyType propertyType, String value) {
        if (Objects.nonNull(propertyType) && Objects.nonNull(value) && !value.isBlank()) {
            properties.add(new MqttProperties.StringProperty(propertyType.value(), value));
        }
        return this;
    }

    public MqttPropertiesBuilder withProperty(MqttProperties.MqttPropertyType propertyType, int value) {
        if (Objects.nonNull(propertyType)) {
            properties.add(new MqttProperties.IntegerProperty(propertyType.value(), value));
        }
        return this;
    }

    public MqttPropertiesBuilder withAvailableOption(MqttProperties.MqttPropertyType propertyType, boolean value) {
        if (Objects.isNull(propertyType)) {
            return this;
        }
        var property = new ByteProperty(propertyType.value(), value ? (byte) 1 : (byte) 0);
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
