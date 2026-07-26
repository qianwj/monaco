package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.core.store.RetainStore;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryRetainStore implements RetainStore {

    private final ConcurrentHashMap<String, ServerPacket.Publish> retained = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> store(String topicName, ServerPacket.Publish message) {
        return Mono.fromRunnable(() -> retained.put(topicName, message));
    }

    @Override
    public Mono<ServerPacket.Publish> get(String topicName) {
        return Mono.justOrEmpty(retained.get(topicName));
    }

    @Override
    public Mono<Void> remove(String topicName) {
        return Mono.fromRunnable(() -> retained.remove(topicName));
    }
}
