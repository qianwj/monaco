package cn.elvis.monaco.authentication;

@FunctionalInterface
public interface Authenticator {

    AuthenticateResult authenticate(String clientId, String username, String password);

    record AuthenticateResult (boolean passed, String reason) {}
}
