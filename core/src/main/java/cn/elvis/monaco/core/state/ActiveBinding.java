package cn.elvis.monaco.core.state;

/**
 * Identifies the currently bound connection for a session.
 * Used to reject stale commands from old connections after takeover.
 *
 * @param connectionId unique ID per physical connection
 * @param ingressNode  node where the TCP connection lives
 * @param ownerEpoch   shard owner epoch at bind time
 */
public record ActiveBinding(
        String connectionId,
        String ingressNode,
        long ownerEpoch
) {

    /**
     * Derives a ConnectionRef for targeting network actions to this binding.
     */
    public ConnectionRef toRef(int generation) {
        return new ConnectionRef(connectionId, generation, ingressNode);
    }
}
