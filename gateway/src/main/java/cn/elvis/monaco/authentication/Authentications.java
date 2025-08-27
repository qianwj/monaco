package cn.elvis.monaco.authentication;

import cn.elvis.monaco.settings.Settings;
import io.vertx.core.Vertx;

public final class Authentications {

    private static final Authenticator ALLOW_ANONYMOUS = (clientId, username, password)
            -> new Authenticator.AuthenticateResult(true, "");

    private static final EnhancedAuthenticator NOT_AUTH = EnhancedAuthenticator::success;

    public static Authenticator createAuthenticator(Settings settings, Vertx vertx) {
        return switch (settings.authenticationMode()) {
            case ALLOW_ANONYMOUS -> ALLOW_ANONYMOUS;
            case FILE -> new DefaultAuthenticator(vertx, settings.fileAuthenticationPath());
        };
    }

    // todo: support enhanced authenticate.
    public static EnhancedAuthenticator enhancedAuthenticator() {
        return NOT_AUTH;
    }
}
