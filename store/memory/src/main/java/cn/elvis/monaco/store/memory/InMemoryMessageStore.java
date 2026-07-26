package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.protocol.packet.ServerPacket;
import cn.elvis.monaco.core.store.MessageStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMessageStore implements MessageStore {

    private final ConcurrentHashMap<String, ServerPacket.Publish> messages = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> clientMessages = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> store(String clientId, String messageId, ServerPacket.Publish message) {
        return Mono.fromRunnable(() -> {
            messages.put(messageId, message);
            clientMessages.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet())
                    .add(messageId);
        });
    }

    @Override
    public Mono<ServerPacket.Publish> get(String messageId) {
        return Mono.justOrEmpty(messages.get(messageId));
    }

    @Override
    public Mono<Void> remove(String clientId, String messageId) {
        return Mono.fromRunnable(() -> {
            messages.remove(messageId);
            var ids = clientMessages.get(clientId);
            if (ids != null) {
                ids.remove(messageId);
            }
        });
    }

    @Override
    public Flux<ServerPacket.Publish> pendingMessages(String clientId) {
        var ids = clientMessages.get(clientId);
        if (ids == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(ids)
                .mapNotNull(messages::get);
    }
}
