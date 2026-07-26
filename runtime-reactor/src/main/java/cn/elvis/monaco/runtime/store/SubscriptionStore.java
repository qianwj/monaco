package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.core.state.SubscriptionRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface SubscriptionStore {
    Mono<Void> save(String clientId, List<SubscriptionRecord> subscriptions);
    Flux<SubscriptionRecord> get(String clientId);
    Mono<Void> remove(String clientId, List<String> topicFilters);
    Mono<Void> remove(String clientId);
    Flux<String> matchingSubscribers(String topicName);
}
