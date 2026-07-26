package cn.elvis.monaco.core.command;

import cn.elvis.monaco.core.state.ConnectionRef;

/**
 * Typed dispatch envelope for session-scoped commands.
 * <p>
 * Unlike a lambda, this is pure data — serializable for cluster dispatch.
 * The dispatcher uses {@code clientId} as the partition key to route
 * the command to the correct shard/mailbox.
 *
 * @param clientId      partition key — determines which shard owns this command
 * @param connectionRef identifies the physical connection that issued the command
 * @param command       the domain command to execute
 */
public record SessionCommand(
        String clientId,
        ConnectionRef connectionRef,
        Command command
) {

    /**
     * Convenience factory for single-node usage.
     */
    public static SessionCommand of(String clientId, ConnectionRef ref, Command command) {
        return new SessionCommand(clientId, ref, command);
    }
}
