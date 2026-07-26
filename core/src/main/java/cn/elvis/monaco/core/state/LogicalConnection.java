package cn.elvis.monaco.core.state;

/**
 * Connection-level negotiated state. Not persisted — destroyed on disconnect.
 * These values do NOT survive reconnect.
 *
 * @param connectionId    unique per physical connection
 * @param generation      connection generation within the session
 * @param receiveMaximum  negotiated receive maximum (client's flow control)
 * @param maxPacketSize   negotiated maximum packet size
 * @param topicAliasMaximum negotiated topic alias maximum
 * @param keepAlive       effective keep alive in seconds
 */
public record LogicalConnection(
        String connectionId,
        int generation,
        int receiveMaximum,
        int maxPacketSize,
        int topicAliasMaximum,
        int keepAlive
) {
}
