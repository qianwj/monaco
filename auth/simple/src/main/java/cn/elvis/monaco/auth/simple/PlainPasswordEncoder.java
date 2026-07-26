package cn.elvis.monaco.auth.simple;

import cn.elvis.monaco.auth.credential.PasswordEncoder;
import cn.elvis.monaco.auth.credential.StoredCredential;

import java.security.MessageDigest;

/**
 * Constant-time plain-text password comparison.
 */
public final class PlainPasswordEncoder implements PasswordEncoder {

    @Override
    public boolean matches(CharSequence rawPassword, StoredCredential stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        byte[] expected = stored.hashedPassword().getBytes();
        byte[] actual = rawPassword.toString().getBytes();
        return MessageDigest.isEqual(expected, actual);
    }
}
