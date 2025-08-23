package cn.elvis.monaco.authentication;

import cn.elvis.monaco.settings.Settings;
import io.vertx.core.Vertx;

public final class Authentications {

    private static final Authenticator ALLOW_ANONYMOUS = (clientId, username, password)
            -> new Authenticator.AuthenticateResult(true, "");

    public static Authenticator create(Settings settings, Vertx vertx) {
        return switch (settings.authenticationMode()) {
            case ALLOW_ANONYMOUS -> ALLOW_ANONYMOUS;
            case FILE -> new DefaultAuthenticator(vertx, settings.fileAuthenticationPath());
        };
    }
}
