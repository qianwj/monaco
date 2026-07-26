package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MessageStore {
    Mono<Void> store(String messageId, ServerPacket.Publish message);
    Mono<ServerPacket.Publish> get(String messageId);
    Mono<Void> remove(String messageId);
    Flux<ServerPacket.Publish> pendingMessages(String clientId);
}
