package cn.elvis.monaco.common.session;

import cn.elvis.monaco.common.entity.AuthenticateResult;
import io.vertx.core.Future;

public interface Authenticator {

    Future<AuthenticateResult> authenticate(String clientId, String username, String password);
}
