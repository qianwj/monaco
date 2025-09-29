package cn.elvis.monaco.topics;

import cn.elvis.monaco.entity.Subscription;

import java.util.*;

/**
 * Mqtt Topic Tree Node
 * @author qianwj
 * @since  0.0.1
 */
public final class TopicTreeNode {

    private final List<TopicTreeNode> children = new ArrayList<>();

    private final List<Subscription> subscriptions = new ArrayList<>();

    private final String segment;

    private final boolean shareable;

    public TopicTreeNode(String segment, boolean shareable) {
        this.segment = segment;
        this.shareable = shareable;
    }

    public void addChild(String[] segment, int level, Subscription subscription) {
        String cur = segment[level];
        if (Topics.SINGLE_WILDCARD_TOKEN.equals(cur)) {
            for (TopicTreeNode child : children) {
                child.addChild(segment, level + 1, subscription);
                subscriptions.add(subscription);
            }
        } else if (Topics.MULTI_WILDCARD_TOKEN.equals(cur)) {
            subscriptions.add(subscription);
            if (level < segment.length - 1) {
                String next = segment[1 + level];
                for (TopicTreeNode child : children) {
                    if (Objects.equals(child.segment, next)) {
                        child.addChild(segment, level + 1, subscription);
                    }
                }
            } else {
                for (TopicTreeNode child : children) {
                    child.addChild(segment, level, subscription);
                }
            }
        } else {
            for (TopicTreeNode child : children) {
                if (Objects.equals(cur, child.segment)) {
                    child.addChild(segment, level + 1, subscription);
                }
            }
        }
    }

//    public List<Subscription> subscriptions(Topic filter) {
//        List<Subscription> result = new ArrayList<>();
//
//    }
}
