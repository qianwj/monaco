package cn.elvis.monaco.core.state;

/**
 * Immutable reference to a physical connection.
 * Used by Actions to target the correct connection, preventing
 * stale commands from affecting a new connection after takeover.
 *
 * @param connectionId unique ID per physical TCP/WS connection
 * @param generation   monotonically increasing per session; rejects stale commands
 * @param ingressNode  node where the physical connection lives (for cluster routing)
 */
public record ConnectionRef(
        String connectionId,
        int generation,
        String ingressNode
) {

    /**
     * Creates a ConnectionRef for single-node deployment (ingressNode = "local").
     */
    public static ConnectionRef local(String connectionId, int generation) {
        return new ConnectionRef(connectionId, generation, "local");
    }

    /**
     * Returns true if this ref is at least as recent as the given generation.
     */
    public boolean isCurrentOrNewer(int otherGeneration) {
        return generation >= otherGeneration;
    }
}
