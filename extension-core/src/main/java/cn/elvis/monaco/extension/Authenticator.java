package cn.elvis.monaco.extension;

import cn.elvis.monaco.common.entity.AuthenticateResult;
import cn.elvis.monaco.extension.dsl.*;
import io.vertx.core.Future;

public non-sealed class Authenticator extends Extension {

    private final ExtensionManager manager;

    public Authenticator(ExtensionManager manager) {
        this.manager = manager;
    }

    public Future<AuthenticateResult> authenticate(String clientId, String username, String password) {
        return useRequestBuilder((builder) -> {
            var offset = AuthenticateRequest.createAuthenticateRequest(
                    builder,
                    builder.createString(username),
                    builder.createString(password),
                    builder.createString(clientId)
            );
            builder.finish(offset);
            var data = builder.dataBuffer();
            return manager.request(TYPE_AUTHENTICATE, data).map(this::parse);
        });
    }

    private AuthenticateResult parse(Response response) {
        var reply = new AuthenticateReply();
        response.responseBody(reply);
        return new AuthenticateResult(reply.passed(), reply.message());
    }
}
