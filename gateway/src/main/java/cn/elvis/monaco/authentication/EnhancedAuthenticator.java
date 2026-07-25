package cn.elvis.monaco.authentication;

import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.buffer.Buffer;

public interface EnhancedAuthenticator {

    AuthenticationStage authenticate(String clientId, String method, Buffer data);

    record AuthenticationStage(String clientId, String method, Buffer data, Stage stage) {

        public static AuthenticationStage init(String clientId, MqttProperties properties) {
            String method = MqttPropertiesUtils.<String>getValue(properties, MqttProperties.AUTHENTICATION_METHOD).orElse("");
            Buffer data = MqttPropertiesUtils.binaryValue(properties, MqttProperties.AUTHENTICATION_DATA, null);
            return new AuthenticationStage(clientId, method, data, Stage.INIT);
        }
    }

    enum Stage {
        INIT,
        FAILED_ON_INVALID_METHOD,
        SUCCESS,
        CONTINUE_AUTHENTICATION,
        RE_AUTHENTICATION,
        ;
    }

    static AuthenticationStage success(String clientId, String method, Buffer data) {
        return new AuthenticationStage(clientId, method, data, Stage.SUCCESS);
    }
}
