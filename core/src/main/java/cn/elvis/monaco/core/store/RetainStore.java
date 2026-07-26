package cn.elvis.monaco.core.store;

import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

public interface RetainStore {
    Mono<Void> store(String topicName, ServerPacket.Publish message);
    Mono<ServerPacket.Publish> get(String topicName);
    Mono<Void> remove(String topicName);
}
