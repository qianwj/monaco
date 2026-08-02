package cn.elvis.monaco.core.auth;

import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads credentials from a file.
 * Format: one user per line — {@code username:encoder:password_or_hash}
 * Lines starting with '#' are comments. Blank lines are ignored.
 */
public final class FileCredentialProvider implements CredentialProvider {

    private final Map<String, StoredCredential> credentials = new ConcurrentHashMap<>();

    public FileCredentialProvider(Path filePath) {
        load(filePath);
    }

    /** Test-friendly: load from lines directly. */
    public FileCredentialProvider(Iterable<String> lines) {
        parseLines(lines);
    }

    @Override
    public Mono<StoredCredential> lookup(String username) {
        if (username == null || username.isBlank()) {
            return Mono.empty();
        }
        StoredCredential cred = credentials.get(username);
        return cred != null ? Mono.just(cred) : Mono.empty();
    }

    private void load(Path filePath) {
        try {
            parseLines(Files.readAllLines(filePath));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load credentials from " + filePath, e);
        }
    }

    private void parseLines(Iterable<String> lines) {
        for (String line : lines) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split(":", 3);
            if (parts.length != 3) {
                continue;
            }
            String username = parts[0].strip();
            String encoder = parts[1].strip();
            String password = parts[2].strip();
            credentials.put(username, new StoredCredential(username, password, encoder));
        }
    }
}
