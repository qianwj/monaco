package cn.elvis.monaco.runtime.connection;

import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionRegistry {

    private final ConcurrentHashMap<ConnectionId, ConnectionHandle> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConnectionId> clientIndex = new ConcurrentHashMap<>();

    public void register(ConnectionHandle handle, String clientId) {
        connections.put(handle.id(), handle);
        if (clientId != null) {
            clientIndex.put(clientId, handle.id());
        }
    }

    public void bindClient(ConnectionId connectionId, String clientId) {
        clientIndex.put(clientId, connectionId);
    }

    public void unregister(ConnectionId connectionId) {
        connections.remove(connectionId);
        clientIndex.values().removeIf(id -> id.equals(connectionId));
    }

    public Optional<ConnectionHandle> getByConnectionId(ConnectionId connectionId) {
        return Optional.ofNullable(connections.get(connectionId));
    }

    public Optional<ConnectionHandle> getByClientId(String clientId) {
        ConnectionId connId = clientIndex.get(clientId);
        if (connId == null) return Optional.empty();
        return Optional.ofNullable(connections.get(connId));
    }

    public Mono<Void> sendTo(String clientId, ServerPacket packet) {
        return Mono.justOrEmpty(getByClientId(clientId))
                .flatMap(handle -> handle.send(packet));
    }

    public Mono<Void> closeConnection(String clientId) {
        return Mono.justOrEmpty(getByClientId(clientId))
                .flatMap(ConnectionHandle::close);
    }

    public Mono<Void> sendToConnection(String connectionId, ServerPacket packet) {
        return Mono.justOrEmpty(getByConnectionId(new ConnectionId(connectionId)))
                .flatMap(handle -> handle.send(packet));
    }

    public Mono<Void> closeByConnectionId(String connectionId) {
        return Mono.justOrEmpty(getByConnectionId(new ConnectionId(connectionId)))
                .flatMap(ConnectionHandle::close);
    }

    public Optional<String> clientIdForConnection(ConnectionId connectionId) {
        return clientIndex.entrySet().stream()
                .filter(e -> e.getValue().equals(connectionId))
                .map(java.util.Map.Entry::getKey)
                .findFirst();
    }

    public int size() {
        return connections.size();
    }
}
