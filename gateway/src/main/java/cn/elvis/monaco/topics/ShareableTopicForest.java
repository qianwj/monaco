package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.elvis.monaco.topics.TopicTreeNode.printTreeRecursive;

final class ShareableTopicForest implements TopicForest {

    private final Map<String, TopicTreeNode> roots = new HashMap<>();

    ShareableTopicForest() {}

    public void addSubscription(Subscription subscription) {
        subscription.topic().shareGroup()
                .map(shareGroup -> this.roots.putIfAbsent(shareGroup, new TopicTreeNode(Topics.SEGMENT_SEPARATOR, true)))
                .ifPresent(root -> {
                    var segments = subscription.topic().filter().split(Topics.SEGMENT_SEPARATOR);
                    root.addChild(segments, 0, subscription);
                });
    }

    public void removeSubscription(Topic topic, String clientIdentifier) {
        topic.shareGroup().map(this.roots::get).ifPresent(root -> {
           var segments = topic.filter().split(Topics.SEGMENT_SEPARATOR);
           root.removeSubscriptions(segments, 0, clientIdentifier);
        });
    }

    public List<Subscription> subscriptions(Topic topic) {
        return topic.shareGroup().map(this.roots::get)
                .map(root -> {
                    var segments = topic.filter().split(Topics.SEGMENT_SEPARATOR);
                    return root.subscriptions(segments, 0);
                }).orElse(List.of());
    }

    @Override
    public void print() {
        for (Map.Entry<String, TopicTreeNode> group : this.roots.entrySet()) {
            printTreeRecursive(group.getValue(), group.getKey(), true);
        }
    }
}
