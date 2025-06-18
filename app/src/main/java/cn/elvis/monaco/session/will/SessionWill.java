package cn.elvis.monaco.session.will;

import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttWill;

public final class SessionWill {

    private String topic;

    private Buffer payload;

    private long delayInterval;

    private boolean useUTF8Encoding;

    private long expiredInterval;

    private String contentType;

    private String responseTopic;

    private Buffer correlationData;

    private MqttProperties.UserProperties userProperties;

    public static SessionWill create(MqttWill willMessage) {
        SessionWill will = new SessionWill();
        will.payload = willMessage.getWillMessage();
        will.topic = willMessage.getWillTopic();
        MqttProperties properties = willMessage.getWillProperties();
        if (properties != null) {
            will.delayInterval = MqttPropertiesUtils.longValue(properties, MqttPropertyType.WILL_DELAY_INTERVAL, -1L);
            will.expiredInterval = MqttPropertiesUtils.longValue(properties, MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL, -1L);
            will.useUTF8Encoding = MqttPropertiesUtils.boolValue(properties, MqttPropertyType.PAYLOAD_FORMAT_INDICATOR, false);
            will.contentType = MqttPropertiesUtils.<String>getValue(properties, MqttPropertyType.CONTENT_TYPE).orElse("");
            will.responseTopic = MqttPropertiesUtils.<String>getValue(properties, MqttPropertyType.RESPONSE_TOPIC).orElse("");
            will.correlationData = MqttPropertiesUtils.binaryValue(properties, MqttPropertyType.CORRELATION_DATA, Buffer.buffer());
            will.userProperties = MqttPropertiesUtils
                    .<MqttProperties.UserProperties>getValue(properties, MqttPropertyType.USER_PROPERTY)
                    .orElse(new MqttProperties.UserProperties());
        }
        return will;
    }

    public String getTopic() {
        return topic;
    }

    public Buffer getPayload() {
        return payload;
    }

    public long getDelayInterval() {
        return delayInterval;
    }

    public boolean isUseUTF8Encoding() {
        return useUTF8Encoding;
    }

    public long getExpiredInterval() {
        return expiredInterval;
    }

    public String getContentType() {
        return contentType;
    }

    public String getResponseTopic() {
        return responseTopic;
    }

    public Buffer getCorrelationData() {
        return correlationData;
    }

    public MqttProperties.UserProperties getUserProperties() {
        return userProperties;
    }
}
