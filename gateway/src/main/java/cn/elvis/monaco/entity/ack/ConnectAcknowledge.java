package cn.elvis.monaco.entity.ack;

import cn.elvis.monaco.authentication.EnhancedAuthenticator;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;

import java.util.Objects;

public record ConnectAcknowledge(
        MqttConnectReturnCode returnCode,
        boolean sessionPresent,
        MqttPropertiesBuilder properties,
        EnhancedAuthenticator.AuthenticationStage authorized
) implements Acknowledge {

    public static ConnectAcknowledge reject(MqttConnectReturnCode returnCode, MqttPropertiesBuilder properties) {
        return new ConnectAcknowledge(returnCode, false, properties, null);
    }

    public static ConnectAcknowledge accept(boolean sessionPresent, MqttPropertiesBuilder properties) {
        return new ConnectAcknowledge(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent, properties, null);
    }

    public static ConnectAcknowledge needAuthorization(EnhancedAuthenticator.AuthenticationStage stage) {
        return new ConnectAcknowledge(null, false, null, stage);
    }

    @Override
    public void send(MqttEndpoint endpoint) {
        if (Objects.nonNull(authorized)) {
            MqttAuthenticationExchangeMessage message = MqttAuthenticationExchangeMessage.create(
                    MqttAuthenticateReasonCode.valueOf(authorized.stage().name()),
                    MqttPropertiesBuilder.create()
                            .withProperty(MqttProperties.AUTHENTICATION_DATA, authorized.data())
                            .withProperty(MqttProperties.AUTHENTICATION_METHOD, authorized.method())
                            .build()
            );
            endpoint.authenticationExchange(message);
            return;
        }
        if (MqttConnectReturnCode.CONNECTION_ACCEPTED == returnCode) {
            endpoint.accept(sessionPresent, properties.build());
        }
        endpoint.reject(returnCode, properties.build());
    }

    public boolean reject() {
        return returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED;
    }
}
