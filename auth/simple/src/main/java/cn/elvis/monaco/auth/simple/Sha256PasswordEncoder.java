package cn.elvis.monaco.auth.simple;

import cn.elvis.monaco.auth.credential.PasswordEncoder;
import cn.elvis.monaco.auth.credential.StoredCredential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 password encoder. Stored password format: hex(SHA-256(salt + rawPassword)).
 * Salt is stored in {@link StoredCredential#salt()}.
 */
public final class Sha256PasswordEncoder implements PasswordEncoder {

    @Override
    public boolean matches(CharSequence rawPassword, StoredCredential stored) {
        if (rawPassword == null || stored == null) {
            return false;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (stored.salt() != null) {
                digest.update(stored.salt().getBytes(StandardCharsets.UTF_8));
            }
            digest.update(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            byte[] computed = digest.digest();
            byte[] expected = HexFormat.of().parseHex(stored.hashedPassword());
            return MessageDigest.isEqual(computed, expected);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (IllegalArgumentException e) {
            // Invalid hex in stored password
            return false;
        }
    }
}
