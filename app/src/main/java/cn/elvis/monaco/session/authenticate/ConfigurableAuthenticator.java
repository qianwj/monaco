package cn.elvis.monaco.session.authenticate;

import cn.elvis.monaco.common.entity.AuthenticateResult;
import cn.elvis.monaco.common.session.Authenticator;
import cn.elvis.monaco.configuration.AuthConfiguration;
import io.vertx.core.Future;

import java.util.Map;
import java.util.Objects;

public class ConfigurableAuthenticator implements Authenticator {

    private final Map<String, String> adminUsers;

    public ConfigurableAuthenticator(AuthConfiguration config) {
        this.adminUsers = config.getAdminUsers();
    }

    @Override
    public Future<AuthenticateResult> authenticate(String clientId, String username, String password) {
        var passed = Objects.equals(password, adminUsers.get(username));
        var message = passed ? "" : "Username or password is incorrect";
        return Future.succeededFuture(new AuthenticateResult(passed, message));
    }
}
