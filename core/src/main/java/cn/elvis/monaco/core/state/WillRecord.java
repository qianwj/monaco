package cn.elvis.monaco.core.state;

import cn.elvis.monaco.protocol.packet.WillMessage;

import java.time.Instant;

/**
 * Persistent will record — stored independently per clientId.
 *
 * @param clientId          owning session
 * @param connectionGeneration generation that set this will
 * @param will              the will message content
 * @param publishAt         absolute time to publish (now + willDelay, capped by session expiry)
 * @param state             current state of the will
 * @param publishCommandId  stable command ID for idempotent publish
 */
public record WillRecord(
        String clientId,
        int connectionGeneration,
        WillMessage will,
        Instant publishAt,
        WillState state,
        String publishCommandId
) {

    public enum WillState {
        PENDING,     // Will is armed, waiting for disconnect or timeout
        SCHEDULED,   // Disconnect happened, will delay timer running
        PUBLISHED,   // Will has been published
        CANCELLED    // Normal disconnect cleared the will
    }
}
