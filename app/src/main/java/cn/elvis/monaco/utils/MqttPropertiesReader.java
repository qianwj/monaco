package cn.elvis.monaco.utils;

import io.netty.handler.codec.mqtt.MqttProperties;

/**
 * Utility for read mqtt properties
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class MqttPropertiesReader {

    private final MqttProperties properties;

    public MqttPropertiesReader(MqttProperties properties) {
        this.properties = properties;
    }

    public int subscriptionId() {
        return intValue(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER, 0);
    }

    public int intValue(MqttProperties.MqttPropertyType propertyType, int defaultValue) {
        return MqttPropertiesUtils.intValue(properties, propertyType, defaultValue);
    }
}
