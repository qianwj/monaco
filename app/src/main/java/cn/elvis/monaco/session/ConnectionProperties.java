package cn.elvis.monaco.session;

import cn.elvis.monaco.configuration.SessionConfiguration;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;

import java.util.Optional;

record ConnectionProperties(
        Optional<Long> sessionExpiryInterval,
        int receiveMaximum,
        int topicAliasMaximum,
        boolean withResponseInfo,
        boolean withProblemInfo
) {

    static ConnectionProperties fromPropertiesAndConfig(MqttProperties properties, SessionConfiguration config) {
        var expiredInterval = MqttPropertiesUtils.<Long>getValue(properties, MqttPropertyType.SESSION_EXPIRY_INTERVAL)
                .map(max -> max > config.getMaxSessionExpiryInterval() ? config.getMaxSessionExpiryInterval() : max);
        var receiveMaximum = MqttPropertiesUtils.<Integer>getValue(properties, MqttPropertyType.RECEIVE_MAXIMUM)
                .map(max -> max > config.getReceiveMaximumLimit() ? config.getReceiveMaximumLimit() : max)
                .orElse(65535);
        return new ConnectionProperties(
                expiredInterval,
                receiveMaximum,
                MqttPropertiesUtils.intValue(properties, MqttPropertyType.TOPIC_ALIAS_MAXIMUM, 0),
                MqttPropertiesUtils.boolValue(properties, MqttPropertyType.REQUEST_RESPONSE_INFORMATION, false),
                MqttPropertiesUtils.boolValue(properties, MqttPropertyType.REQUEST_PROBLEM_INFORMATION, false)
        );
    }
}
