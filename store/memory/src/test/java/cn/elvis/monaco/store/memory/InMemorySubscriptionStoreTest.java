package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.protocol.model.QoS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

class InMemorySubscriptionStoreTest {

    private InMemorySubscriptionStore store;

    @BeforeEach
    void setUp() {
        store = new InMemorySubscriptionStore();
    }

    @Test
    void saveAndGet() {
        var sub = subscription("client1", "sensor/+/temp");
        store.save("client1", List.of(sub)).block();

        StepVerifier.create(store.get("client1"))
                .expectNext(sub)
                .verifyComplete();
    }

    @Test
    void getEmptyReturnsNothing() {
        StepVerifier.create(store.get("unknown"))
                .verifyComplete();
    }

    @Test
    void saveReplacesDuplicate() {
        var sub1 = subscription("client1", "a/b");
        var sub2 = new SubscriptionRecord("client1", "a/b", QoS.EXACTLY_ONCE,
                false, false, 0, 0, 2, Instant.now());

        store.save("client1", List.of(sub1)).block();
        store.save("client1", List.of(sub2)).block();

        StepVerifier.create(store.get("client1"))
                .expectNext(sub2)
                .verifyComplete();
    }

    @Test
    void removeByTopicFilter() {
        var sub1 = subscription("client1", "a/b");
        var sub2 = subscription("client1", "c/d");
        store.save("client1", List.of(sub1, sub2)).block();

        store.remove("client1", List.of("a/b")).block();

        StepVerifier.create(store.get("client1"))
                .expectNext(sub2)
                .verifyComplete();
    }

    @Test
    void removeAll() {
        store.save("client1", List.of(subscription("client1", "a/b"))).block();
        store.remove("client1").block();

        StepVerifier.create(store.get("client1"))
                .verifyComplete();
    }

    @Test
    void matchingSubscribers() {
        store.save("client1", List.of(subscription("client1", "sensor/+/temp"))).block();
        store.save("client2", List.of(subscription("client2", "sensor/#"))).block();
        store.save("client3", List.of(subscription("client3", "other/topic"))).block();

        StepVerifier.create(store.matchingSubscribers("sensor/room1/temp").sort())
                .expectNext("client1", "client2")
                .verifyComplete();
    }

    @Test
    void matchingSubscribersNoMatch() {
        store.save("client1", List.of(subscription("client1", "a/b"))).block();

        StepVerifier.create(store.matchingSubscribers("x/y"))
                .verifyComplete();
    }

    private SubscriptionRecord subscription(String clientId, String topicFilter) {
        return new SubscriptionRecord(clientId, topicFilter, QoS.AT_LEAST_ONCE,
                false, false, 0, 0, 1, Instant.now());
    }
}
