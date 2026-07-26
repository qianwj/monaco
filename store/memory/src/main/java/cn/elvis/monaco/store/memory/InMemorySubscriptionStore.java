package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.core.store.SubscriptionStore;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemorySubscriptionStore implements SubscriptionStore {

    // clientId -> subscriptions
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SubscriptionRecord>> subscriptions =
            new ConcurrentHashMap<>();

    @Override
    public Mono<Void> save(String clientId, List<SubscriptionRecord> records) {
        return Mono.fromRunnable(() -> {
            var list = subscriptions.computeIfAbsent(clientId, k -> new CopyOnWriteArrayList<>());
            for (var record : records) {
                // Replace existing subscription with same topicFilter, or add new
                list.removeIf(r -> r.topicFilter().equals(record.topicFilter()));
                list.add(record);
            }
        });
    }

    @Override
    public Flux<SubscriptionRecord> get(String clientId) {
        var list = subscriptions.get(clientId);
        if (list == null) {
            return Flux.empty();
        }
        return Flux.fromIterable(list);
    }

    @Override
    public Mono<Void> remove(String clientId, List<String> topicFilters) {
        return Mono.fromRunnable(() -> {
            var list = subscriptions.get(clientId);
            if (list != null) {
                list.removeIf(r -> topicFilters.contains(r.topicFilter()));
            }
        });
    }

    @Override
    public Mono<Void> remove(String clientId) {
        return Mono.fromRunnable(() -> subscriptions.remove(clientId));
    }

    @Override
    public Flux<String> matchingSubscribers(String topicName) {
        return Flux.fromIterable(subscriptions.entrySet())
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(r -> TopicMatcher.matches(r.topicFilter(), topicName)))
                .map(entry -> entry.getKey());
    }
}
