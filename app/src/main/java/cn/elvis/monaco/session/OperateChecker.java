package cn.elvis.monaco.session;

import cn.elvis.monaco.session.authenticate.EnhancedAuthenticationManager;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.mqtt.MqttEndpoint;

public final class OperateChecker {

    private final EnhancedAuthenticationManager enhancedAuthenticator;

    private final boolean enableEnhancedAuthentication;

    private final MqttEndpoint endpoint;

    OperateChecker(EnhancedAuthenticationManager enhancedAuthenticator,
                   boolean enableEnhancedAuthentication,
                   MqttEndpoint endpoint) {
        this.enhancedAuthenticator = enhancedAuthenticator;
        this.enableEnhancedAuthentication = enableEnhancedAuthentication;
        this.endpoint = endpoint;
    }

    boolean allowed() {
        if (!enhancedAuthenticator.authenticated(endpoint.clientIdentifier(), enableEnhancedAuthentication)) {
            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED_5);
            return false;
        }
        return true;
    }
}
