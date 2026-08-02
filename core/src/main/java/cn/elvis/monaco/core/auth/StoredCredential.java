package cn.elvis.monaco.core.auth;

/**
 * A stored credential entry.
 *
 * @param username       the username
 * @param hashedPassword the password (plain or hashed depending on encoder)
 * @param encoderType    the encoder type used ("plain" or "sha256")
 */
public record StoredCredential(String username, String hashedPassword, String encoderType) {}
