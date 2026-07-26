package cn.elvis.monaco.runtime.connection;

import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.protocol.model.ConnectionId;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains per-physical-connection negotiated state (LogicalConnection).
 * This state is ephemeral — destroyed on disconnect, not persisted.
 */
public class LogicalConnectionRegistry {

    private final ConcurrentHashMap<ConnectionId, LogicalConnection> connections = new ConcurrentHashMap<>();

    public void bind(ConnectionId connectionId, LogicalConnection logical) {
        connections.put(connectionId, logical);
    }

    public Optional<LogicalConnection> get(ConnectionId connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public void unbind(ConnectionId connectionId) {
        connections.remove(connectionId);
    }

    public int size() {
        return connections.size();
    }
}
