package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.state.WillRecord;
import cn.elvis.monaco.core.store.WillStore;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryWillStore implements WillStore {

    private final ConcurrentHashMap<String, WillRecord> wills = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(WillRecord will) {
        return Mono.fromRunnable(() -> wills.put(will.clientId(), will));
    }

    @Override
    public Mono<WillRecord> get(String clientId) {
        return Mono.justOrEmpty(wills.get(clientId));
    }

    @Override
    public Mono<Void> remove(String clientId) {
        return Mono.fromRunnable(() -> wills.remove(clientId));
    }
}
