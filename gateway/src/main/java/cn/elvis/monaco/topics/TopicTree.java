package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

public final class TopicTree {

    private final TopicTreeNode root;

    TopicTree(String prefixToken) {
        this(prefixToken, false);
    }

    TopicTree(String prefixToken, boolean shareable) {
        root = new TopicTreeNode(prefixToken, 0, shareable);
    }

    public void addSubscription(Subscription subscription) {
        String filter = subscription.topic().filter();
        String[] segments = filter.split("/");
        root.addChild(segments, 0, subscription);
    }
}
