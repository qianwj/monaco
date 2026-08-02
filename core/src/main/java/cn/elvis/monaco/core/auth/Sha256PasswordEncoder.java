package cn.elvis.monaco.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 password encoder. Compares hex(sha256(raw)) against stored hash. */
public final class Sha256PasswordEncoder implements PasswordEncoder {

    @Override
    public boolean matches(String rawPassword, StoredCredential stored) {
        String hashed = hash(rawPassword);
        return MessageDigest.isEqual(
                hashed.getBytes(StandardCharsets.UTF_8),
                stored.hashedPassword().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String type() {
        return "sha256";
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
