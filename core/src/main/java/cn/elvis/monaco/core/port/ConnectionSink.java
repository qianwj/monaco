package cn.elvis.monaco.core.port;

import cn.elvis.monaco.core.state.ConnectionRef;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

/**
 * Port for sending packets to physical connections.
 * Implementations live in the transport layer (e.g., Reactor Netty).
 * <p>
 * All operations are targeted by {@link ConnectionRef} to prevent
 * stale delivery to replaced connections.
 */
public interface ConnectionSink {

    /**
     * Send a packet to the specified connection.
     * Returns empty Mono if the connection no longer exists or generation mismatches.
     */
    Mono<Void> send(ConnectionRef target, ServerPacket packet);

    /**
     * Close the specified connection gracefully.
     */
    Mono<Void> close(ConnectionRef target);
}
