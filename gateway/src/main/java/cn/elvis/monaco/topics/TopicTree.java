package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.List;
import java.util.Objects;

public final class TopicTree {

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

    public List<Subscription> subscriptions(Topic topic) {
        String[] segments = topic.filter().split("/");
        return root.subscriptions(segments, 0);
    }
}
