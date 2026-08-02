package cn.elvis.monaco.core.auth;

import java.security.MessageDigest;

/** Constant-time plain text password comparison. */
public final class PlainPasswordEncoder implements PasswordEncoder {

    @Override
    public boolean matches(String rawPassword, StoredCredential stored) {
        return MessageDigest.isEqual(
                rawPassword.getBytes(),
                stored.hashedPassword().getBytes());
    }

    @Override
    public String type() {
        return "plain";
    }
}
