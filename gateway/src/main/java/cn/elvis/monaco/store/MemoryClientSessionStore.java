package cn.elvis.monaco.store;

import cn.elvis.monaco.session.ClientSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryClientSessionStore implements ClientSessionStore {

    private final Map<String, ClientSession> store = new ConcurrentHashMap<>();

    @Override
    public void add(ClientSession clientSession) {
        store.put(clientSession.identifier(), clientSession);
    }

    @Override
    public Optional<ClientSession> get(String clientId) {
        return Optional.ofNullable(store.get(clientId));
    }

    @Override
    public Optional<ClientSession> remove(String clientId) {
        return Optional.ofNullable(store.remove(clientId));
    }

    @Override
    public List<ClientSession> expired() {
        return store.values().stream().filter(ClientSession::isExpired).toList();
    }

    @Override
    public int total() {
        return store.size();
    }
}
