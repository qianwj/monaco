package cn.elvis.monaco.authentication;

import io.vertx.core.Vertx;

import java.util.List;
import java.util.Objects;

public final class DefaultAuthenticator implements Authenticator {

    private final List<User> users;

    public DefaultAuthenticator(Vertx vertx, String path) {
        users = vertx.fileSystem().readFile(path)
                .map(buf -> buf.toJsonArray().stream().map(obj -> (User) obj).toList())
                .await();
    }

    @Override
    public AuthenticateResult authenticate(String clientId, String username, String password) {
        for (User user : users) {
            if (Objects.equals(username, user.username()) && Objects.equals(password, user.password())) {
                return new AuthenticateResult(true, "");
            }
        }
        return new AuthenticateResult(false, "Invalid username or password");
    }
}
