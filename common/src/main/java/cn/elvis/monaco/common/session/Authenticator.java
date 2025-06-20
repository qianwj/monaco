package cn.elvis.monaco.common.session;

import cn.elvis.monaco.common.entity.AuthenticateResult;
import io.vertx.core.Future;

public interface Authenticator {

    Future<AuthenticateResult> authenticate(String clientId, String username, String password);

    class AllowAnonymousAuthenticator implements Authenticator {

        private static final AllowAnonymousAuthenticator INSTANCE = new AllowAnonymousAuthenticator();

        private final Future<AuthenticateResult> result = Future.succeededFuture(new AuthenticateResult(true, ""));

        public static Authenticator getInstance() {
            return INSTANCE;
        }

        @Override
        public Future<AuthenticateResult> authenticate(String clientId, String username, String password) {
            return result;
        }
    }
}
