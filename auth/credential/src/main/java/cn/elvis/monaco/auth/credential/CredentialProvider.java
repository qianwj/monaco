package cn.elvis.monaco.auth.credential;

import reactor.core.publisher.Mono;

/**
 * Looks up stored credentials by username.
 *
 * <p>Implementations are loaded via ServiceLoader from the {@code auth:simple} plugin
 * based on the configured {@code credential.provider} key.</p>
 */
public interface CredentialProvider {

    /** Provider type identifier (e.g. "env", "file", "jdbc"). */
    String type();

    /**
     * Looks up a stored credential for the given username.
     *
     * @param username the username to look up
     * @return the stored credential, or empty if not found
     */
    Mono<StoredCredential> lookup(String username);
}
