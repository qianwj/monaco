package cn.elvis.monaco.core.state;

import java.time.Instant;

/**
 * Persistent session metadata — bounded, no unbounded collections.
 * Subscriptions, inflight, delivery, and will are independent store records.
 *
 * <p>{@code 0xFFFFFFFF} sessionExpiryIntervalSeconds means no expiry.
 */
public record SessionRecord(
        String clientId,
        long revision,
        int connectionGeneration,
        ActiveBinding activeBinding,       // null when disconnected
        long sessionExpiryIntervalSeconds,
        Instant disconnectedAt,            // null when connected
        Instant expiryAt                   // null when connected or infinite expiry
) {

    /** Maximum value indicating "session never expires". */
    public static final long EXPIRY_INFINITE = 0xFFFFFFFFL;

    public boolean isConnected() {
        return activeBinding != null && disconnectedAt == null;
    }

    public boolean isExpired(Instant now) {
        if (expiryAt == null) return false;
        return !now.isBefore(expiryAt);
    }

    public SessionRecord connect(ActiveBinding binding, long expirySeconds) {
        return new SessionRecord(
                clientId, revision + 1, connectionGeneration + 1,
                binding, expirySeconds, null, null);
    }

    public SessionRecord disconnect(Instant now, long expirySeconds) {
        Instant expiry = (expirySeconds == EXPIRY_INFINITE)
                ? null
                : now.plusSeconds(expirySeconds);
        return new SessionRecord(
                clientId, revision + 1, connectionGeneration,
                null, expirySeconds, now, expiry);
    }

    public static SessionRecord create(String clientId, ActiveBinding binding, long expirySeconds) {
        return new SessionRecord(clientId, 1, 1, binding, expirySeconds, null, null);
    }
}
