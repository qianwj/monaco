package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.state.InflightRecord;
import cn.elvis.monaco.core.store.FlightWindowStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ConcurrentHashMap;

public class InMemoryFlightWindowStore implements FlightWindowStore {

    // clientId -> (direction:packetId -> record)
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, InflightRecord>> flights =
            new ConcurrentHashMap<>();

    @Override
    public Mono<Void> add(String clientId, InflightRecord record) {
        return Mono.fromRunnable(() ->
                flights.computeIfAbsent(clientId, k -> new ConcurrentHashMap<>())
                        .put(key(record.direction(), record.packetId()), record));
    }

    @Override
    public Mono<Void> remove(String clientId, InflightRecord.Direction direction, int packetId) {
        return Mono.fromRunnable(() -> {
            var map = flights.get(clientId);
            if (map != null) {
                map.remove(key(direction, packetId));
            }
        });
    }

    @Override
    public Flux<InflightRecord> getAll(String clientId) {
        var map = flights.get(clientId);
        if (map == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(map.values());
    }

    @Override
    public Mono<Void> clear(String clientId) {
        return Mono.fromRunnable(() -> flights.remove(clientId));
    }

    @Override
    public Mono<Integer> size(String clientId) {
        var map = flights.get(clientId);
        return Mono.just(map == null ? 0 : map.size());
    }

    private static String key(InflightRecord.Direction direction, int packetId) {
        return direction.name() + ":" + packetId;
    }
}
