package cn.elvis.monaco.store.memory;

import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.store.SubscriptionStore;
import cn.elvis.monaco.topics.Topic;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Subscription store use memory
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class MemorySubscriptionStore implements SubscriptionStore {

    private final Map<String, List<Subscription>> clientSubscriptionsStore = new ConcurrentHashMap<>();

    private final Map<String, Map<Integer, List<Subscription>>> subscriptionIdHashStore = new ConcurrentHashMap<>();

    public void addSubscriptions(String clientId, int subscribeId, List<Subscription> subscriptions) {
        var clientSubscriptions = clientSubscriptionsStore.getOrDefault(clientId, new ArrayList<>());
        var iter = clientSubscriptions.iterator();
        while (iter.hasNext()) {
            var previous = iter.next();
            for (Subscription current : subscriptions) {
                if (Objects.equals(previous.topic(), current.topic())) {
                    iter.remove();
                }
            }
        }
        clientSubscriptions.addAll(subscriptions);
        clientSubscriptionsStore.put(clientId, clientSubscriptions);
        if (subscribeId > 0) {
            Map<Integer, List<Subscription>> clientSubscriptionIdHash = subscriptionIdHashStore.getOrDefault(clientId, new ConcurrentHashMap<>());
            clientSubscriptionIdHash.put(subscribeId, subscriptions);
            subscriptionIdHashStore.put(clientId, clientSubscriptionIdHash);
        }
    }

    public boolean exists(String clientId, Topic topic) {
        var clientSubscriptions = clientSubscriptionsStore.getOrDefault(clientId, new ArrayList<>());
        for (Subscription clientSubscription : clientSubscriptions) {
            if (clientSubscription.topic().equals(topic)) {
                return true;
            }
        }
        return false;
    }

    public List<Subscription> removeSubscriptions(String clientId, Set<String> topicFilters) {
        var clientSubscriptions = clientSubscriptionsStore.getOrDefault(clientId, new ArrayList<>());
        List<Subscription> removedSubscriptions = new ArrayList<>();
        var iter = clientSubscriptions.iterator();
        while (iter.hasNext()) {
            var subscription = iter.next();
            if (topicFilters.contains(subscription.topic().unwrap())) {
                iter.remove();
                removedSubscriptions.add(subscription);
            }
        }
        return removedSubscriptions;
    }
}
