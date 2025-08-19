package cn.elvis.monaco.store;

import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.topics.Topic;

import java.util.List;
import java.util.Set;

/**
 * Subscription store
 *
 * @author qianwj
 * @since  0.0.1
 */
public interface SubscriptionStore {

    /**
     * Add client subscriptions
     * @param clientId client identifier
     * @param subscribeId subscribe identifier, 0 is not exist.
     * @param subscriptions added subscription list
     */
    void addSubscriptions(String clientId, int subscribeId, List<Subscription> subscriptions);

    /**
     * Check subscribed topic exist in current client session
     * @param clientId client identifier
     * @param topic subscribed topic
     * @return true is exist
     */
    boolean exists(String clientId, Topic topic);

    /**
     * Remove client subscriptions
     * @param clientId client identifier
     * @param topicFilters removed subscription topic filter
     * @return removed subscriptions
     */
    List<Subscription> removeSubscriptions(String clientId, Set<String> topicFilters);
}
