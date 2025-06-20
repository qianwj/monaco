package cn.elvis.monaco.session;

import cn.elvis.monaco.common.ChannelKeys;
import cn.elvis.monaco.common.session.Authenticator;
import cn.elvis.monaco.session.authenticate.EnhancedAuthenticationManager;
import cn.elvis.monaco.utils.MqttPropertiesBuilder;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;

import java.util.Optional;

final class Connector {

    private final MqttEndpoint endpoint;

    private final Authenticator authenticator;

    private final EnhancedAuthenticationManager enhancedAuthenticator;

    private final boolean needEnhancedAuthenticate;

    private final boolean withProblemInfo;

    public Connector(MqttEndpoint endpoint,
                     Authenticator authenticator,
                     EnhancedAuthenticationManager enhancedAuthenticator,
                     boolean needEnhancedAuthenticate,
                     boolean withProblemInfo) {
        this.endpoint = endpoint;
        this.authenticator = authenticator;
        this.enhancedAuthenticator = enhancedAuthenticator;
        this.needEnhancedAuthenticate = needEnhancedAuthenticate;
        this.withProblemInfo = withProblemInfo;
    }

    Future<Boolean> connect(Vertx vertx) {
        return authenticate().compose(passed -> {
            if (passed) {
                if (needEnhancedAuthenticate) {
                    return enhancedAuthenticator.authenticate(endpoint.clientIdentifier(), endpoint.connectProperties())
                            .map(authResult -> {
                                var properties = MqttPropertiesBuilder.create()
                                        .withAuthenticationMethod(authResult.method())
                                        .withAuthenticationData(authResult.data());
                                return switch (authResult.state()) {
                                    case FAILURE -> {
                                        if (withProblemInfo) {
                                            properties.withReasonString(authResult.message());
                                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD, properties.build());
                                        } else {
                                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_AUTHENTICATION_METHOD);
                                        }
                                        yield false;
                                    }
                                    case CONTINUE, RE_AUTHENTICATE, SUCCESS -> {
                                        var code = MqttAuthenticateReasonCode.valueOf(authResult.state().code());
                                        MqttAuthenticationExchangeMessage authExchange = MqttAuthenticationExchangeMessage.create(code, properties.build());
                                        endpoint.authenticationExchange(authExchange);
                                        yield true;
                                    }
                                };
                            });
                }
                return Future.succeededFuture(true);
            }
            return Future.succeededFuture(false);
        }).compose(passed -> {
            if (passed) {
                return vertx.eventBus()
                        .<Boolean>request(ChannelKeys.SESSION_EXISTS, endpoint.clientIdentifier())
                        .compose(reply -> {
                            endpoint.accept(reply.body());
                            return Future.succeededFuture(true);
                        });
            }
            return Future.succeededFuture(false);
        });
    }

    private Future<Boolean> authenticate() {
        var auth = Optional.ofNullable(endpoint.auth());
        var username = auth.map(MqttAuth::getUsername).orElse("");
        var password = auth.map(MqttAuth::getPassword).orElse("");
        return authenticator.authenticate(endpoint.clientIdentifier(), username, password)
                .compose(result -> {
                    if (result.success()) {
                        return Future.succeededFuture(true);
                    } else {
                        if (withProblemInfo) {
                            var properties = MqttPropertiesBuilder.create().withReasonString(result.message()).build();
                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD, properties);
                        } else {
                            endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USERNAME_OR_PASSWORD);
                        }
                        return Future.succeededFuture(false);
                    }
                });
    }
}
