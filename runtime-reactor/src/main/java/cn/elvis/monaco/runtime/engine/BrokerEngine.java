package cn.elvis.monaco.runtime.engine;

import cn.elvis.monaco.protocol.model.ConnectionId;
import cn.elvis.monaco.protocol.packet.ClientPacket;
import cn.elvis.monaco.runtime.connection.ConnectionHandle;
import reactor.core.publisher.Mono;

public interface BrokerEngine {
    Mono<Void> opened(ConnectionHandle connection);
    Mono<Void> received(ConnectionId connectionId, ClientPacket packet);
    Mono<Void> closed(ConnectionId connectionId, DisconnectCause cause);
}
