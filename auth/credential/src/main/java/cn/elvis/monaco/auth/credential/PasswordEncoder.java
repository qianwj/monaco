package cn.elvis.monaco.auth.credential;

/**
 * Strategy for verifying a raw password against a {@link StoredCredential}.
 */
public interface PasswordEncoder {

    /**
     * Checks whether the raw password matches the stored credential.
     *
     * @param rawPassword the password supplied by the client
     * @param stored      the stored credential to verify against
     * @return true if the password matches
     */
    boolean matches(CharSequence rawPassword, StoredCredential stored);
}
