package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.store.SessionStore;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionStore implements SessionStore {

    private final ConcurrentHashMap<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    @Override
    public Mono<SessionRecord> get(String clientId) {
        return Mono.justOrEmpty(sessions.get(clientId));
    }

    @Override
    public Mono<Void> save(SessionRecord session) {
        return Mono.fromRunnable(() -> sessions.put(session.clientId(), session));
    }

    @Override
    public Mono<Void> remove(String clientId) {
        return Mono.fromRunnable(() -> sessions.remove(clientId));
    }
}
