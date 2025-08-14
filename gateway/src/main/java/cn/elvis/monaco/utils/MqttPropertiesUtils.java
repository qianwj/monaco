package cn.elvis.monaco.utils;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttProperty;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.vertx.core.buffer.Buffer;

import java.util.Optional;

public final class MqttPropertiesUtils {

    public static boolean boolValue(MqttProperties properties, MqttPropertyType propertyType, boolean defaultValue) {
        Optional<Byte> value = getValue(properties, propertyType);
        return value.map(b -> b > 0).orElse(defaultValue);
    }

    public static int intValue(MqttProperties properties, MqttPropertyType propertyType, int defaultValue) {
        Optional<Integer> value = getValue(properties, propertyType);
        return value.orElse(defaultValue);
    }

    public static long longValue(MqttProperties properties, MqttPropertyType propertyType, long defaultValue) {
        Optional<Long> value = getValue(properties, propertyType);
        return value.orElse(defaultValue);
    }

    public static Buffer binaryValue(MqttProperties properties, MqttPropertyType propertyType, Buffer defaultValue) {
        Optional<ByteBuf> value = getValue(properties, propertyType);
        return value.map(buf -> Buffer.buffer(buf.array())).orElse(defaultValue);
    }

    public static <T> Optional<T> getValue(MqttProperties properties, MqttPropertyType propertyType) {
        return Optional.ofNullable(MqttPropertiesUtils.<T>getProperty(properties, propertyType))
                .map(MqttProperty::value);
    }

    @SuppressWarnings("unchecked")
    public static <T> MqttProperty<T> getProperty(MqttProperties properties, MqttPropertyType propertyType) {
        return (MqttProperty<T>) properties.getProperty(propertyType.value());
    }
}
