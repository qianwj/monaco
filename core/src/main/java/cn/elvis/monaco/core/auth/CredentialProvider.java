package cn.elvis.monaco.core.auth;

import reactor.core.publisher.Mono;

/** Looks up stored credentials by username. */
public interface CredentialProvider {

    Mono<StoredCredential> lookup(String username);
}
