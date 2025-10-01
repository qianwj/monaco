package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.List;

public sealed interface TopicForest
        permits ShareableTopicForest, TopicForestImpl, TopicTree {

    void addSubscription(Subscription subscription);

    void removeSubscription(Topic topic, String clientIdentifier);

    List<Subscription> subscriptions(Topic topic);

    void print();

    static TopicForest create() {
        return new TopicForestImpl();
    }
}
