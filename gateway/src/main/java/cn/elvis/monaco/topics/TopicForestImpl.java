package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;
import cn.elvis.monaco.store.SubscriptionStore;

import java.util.List;

final class TopicForestImpl implements TopicForest {

    private final TopicTree normalTopicTree = new TopicTree("/");

    private final ShareableTopicForest shareGroupTopicTree = new ShareableTopicForest();

    @Override
    public void addSubscription(Subscription subscription) {
        if (subscription.topic().shareable()) {
            shareGroupTopicTree.addSubscription(subscription);
            return;
        }
        normalTopicTree.addSubscription(subscription);
    }

    @Override
    public void removeSubscription(Topic topic, String clientIdentifier) {
        if (topic.shareable()) {
            shareGroupTopicTree.removeSubscription(topic, clientIdentifier);
            return;
        }
        normalTopicTree.removeSubscription(topic, clientIdentifier);
    }

    @Override
    public List<Subscription> subscriptions(Topic topic) {
        if (topic.shareable()) {
            return shareGroupTopicTree.subscriptions(topic);
        }
        return normalTopicTree.subscriptions(topic);
    }

    public void print() {
        System.out.println("======================== NORMAL TOPIC FOREST ======================");
        normalTopicTree.print();
        System.out.println("======================== NORMAL TOPIC FOREST ======================");
        System.out.println();
        System.out.println("====================== SHARE_GROUP TOPIC FOREST ===================");
        shareGroupTopicTree.print();
        System.out.println("====================== SHARE_GROUP TOPIC FOREST ===================");
    }
}
