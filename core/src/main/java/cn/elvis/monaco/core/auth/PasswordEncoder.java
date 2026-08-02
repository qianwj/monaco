package cn.elvis.monaco.core.auth;

/** Verifies a raw password against a stored credential. */
public interface PasswordEncoder {

    boolean matches(String rawPassword, StoredCredential stored);

    String type();
}
