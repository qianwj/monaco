package cn.elvis.monaco.session.authenticate;

import cn.elvis.monaco.common.entity.EnhancedAuthenticateResult;
import cn.elvis.monaco.common.entity.EnhancedAuthenticateState;
import cn.elvis.monaco.common.session.EnhancedAuthenticator;
import cn.elvis.monaco.utils.MqttPropertiesUtils;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Managing enhanced authenticator.
 *
 * @author qianwenjin1
 * @since  2025/06/19
 */
public final class EnhancedAuthenticationManager {

    private final Map<String, EnhancedAuthenticator> authenticators = new ConcurrentHashMap<>();

    private final Map<String, EnhancedAuthenticateState> clientAuthenticateStates = new ConcurrentHashMap<>();

    public void register(EnhancedAuthenticator authenticator) {
        authenticators.put(authenticator.method(), authenticator);
    }

    public Future<EnhancedAuthenticateResult> authenticate(String clientId, MqttProperties properties) {
        var method = MqttPropertiesUtils.<String>getValue(properties, MqttPropertyType.AUTHENTICATION_METHOD).orElse("");
        var data = MqttPropertiesUtils.binaryValue(properties, MqttPropertyType.AUTHENTICATION_DATA, Buffer.buffer());
        if (!supportedMethods(method)) {
            clientAuthenticateStates.remove(clientId);
            return Future.succeededFuture(
                    new EnhancedAuthenticateResult(
                            EnhancedAuthenticateState.FAILURE,
                            "Unsupported authenticate method: " + method,
                            method,
                            data
                    )
            );
        }
        EnhancedAuthenticator authenticator = authenticators.get(clientId);
        return authenticator.authenticate(clientId, data).map(result -> {
            clientAuthenticateStates.put(clientId, result.state());
            return result;
        });
    }

    public boolean authenticated(String clientId, boolean needEnhancedAuthenticate) {
        if (!needEnhancedAuthenticate) {
            return true;
        }
        return clientAuthenticateStates.get(clientId) == EnhancedAuthenticateState.SUCCESS;
    }

    public Optional<EnhancedAuthenticateState> clientAuthenticateState(String clientId) {
        return Optional.ofNullable(clientAuthenticateStates.get(clientId));
    }

    private boolean supportedMethods(String method) {
        return authenticators.containsKey(method);
    }
}
