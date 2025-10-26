package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.List;

import static cn.elvis.monaco.topics.TopicTreeNode.printTreeRecursive;

final class TopicTree implements TopicForest {

    private final TopicTreeNode root;

    TopicTree(String prefixToken) {
        this(prefixToken, false);
    }

    TopicTree(String prefixToken, boolean shareable) {
        root = new TopicTreeNode(prefixToken, shareable);
    }

    public void addSubscription(Subscription subscription) {
        String filter = subscription.topic().filter();
        String[] segments = filter.split("/");
        root.addChild(segments, 0, subscription);
    }

    public void removeSubscription(Topic topic, String clientIdentifier) {
        String[] segments = topic.filter().split("/");
        root.removeSubscriptions(segments, 0, clientIdentifier);
    }

    public List<Subscription> subscriptions(Topic topic) {
        String[] segments = topic.filter().split("/");
        return root.subscriptions(segments, 0);
    }

    public void print() {
        printTreeRecursive(root, "", true);
    }
}
