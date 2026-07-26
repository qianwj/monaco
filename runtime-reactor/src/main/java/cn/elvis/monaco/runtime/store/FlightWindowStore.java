package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.core.state.InflightRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FlightWindowStore {
    Mono<Void> add(String clientId, InflightRecord record);
    Mono<Void> remove(String clientId, InflightRecord.Direction direction, int packetId);
    Flux<InflightRecord> getAll(String clientId);
    Mono<Void> clear(String clientId);
    Mono<Integer> size(String clientId);
}
