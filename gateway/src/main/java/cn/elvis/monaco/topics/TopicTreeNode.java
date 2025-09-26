package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.*;

public final class TopicTreeNode {

    private final List<TopicTreeNode> children = new ArrayList<>();

    private final List<Subscription> subscriptions = new ArrayList<>();

    private final String segment;

    private final boolean shareable;

    private final int level;

    public TopicTreeNode(String segment, int level, boolean shareable) {
        this.segment = segment;
        this.shareable = shareable;
        this.level = level;
    }

    public void addChild(String[] segment, int level, Subscription subscription) {
        String cur = segment[level];
        // todo: add child
    }
}
