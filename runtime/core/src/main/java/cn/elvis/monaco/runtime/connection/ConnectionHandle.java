package cn.elvis.monaco.runtime.connection;

import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ServerPacket;
import reactor.core.publisher.Mono;

public interface ConnectionHandle {
    ConnectionId id();
    Mono<Void> send(ServerPacket packet);
    Mono<Void> close();
}
