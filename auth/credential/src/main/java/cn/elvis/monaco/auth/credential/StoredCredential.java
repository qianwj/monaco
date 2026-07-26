package cn.elvis.monaco.auth.credential;

import java.util.Map;

/**
 * Stored credential record returned by a {@link CredentialProvider}.
 *
 * @param username       the authenticated username
 * @param hashedPassword the stored password (plain-text or hashed depending on encoder)
 * @param salt           optional salt used for hashing (null if plain-text)
 * @param iterations     iteration count for key derivation (0 if not applicable)
 * @param attributes     additional metadata associated with the credential
 */
public record StoredCredential(
        String username,
        String hashedPassword,
        String salt,
        int iterations,
        Map<String, String> attributes
) {

    public StoredCredential {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (hashedPassword == null) {
            throw new IllegalArgumentException("Hashed password must not be null");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /** Convenience factory for plain-text credentials without salt or iterations. */
    public static StoredCredential plainText(String username, String password) {
        return new StoredCredential(username, password, null, 0, Map.of());
    }
}
