package cn.elvis.monaco.topics;

import java.util.Objects;

/**
 * Mqtt Topic
 * Creating from a topic filter. When publish or subscribe, message might be filtered by topic.
 * Topic looks like:
 *  - a/b/c
 *  - a/+/c
 *  - a/*\/c
 *  - $share/a/b/c
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class Topic {

    private final boolean wildcard;

    private final boolean shareable;

    private final String shareGroup;

    private final String filter;

    private final String raw;

    Topic(String topicFilter) {
        this.wildcard = Topics.isWildcardTopic(topicFilter);
        this.shareable = Topics.isShareTopic(topicFilter);
        if (shareable) {
            int start = topicFilter.indexOf('/');
            if (start == -1) {
                throw new IllegalArgumentException("Invalid topic filter: " + topicFilter);
            }
            int end = topicFilter.indexOf('/', start + 1);
            this.filter = topicFilter.substring(end + 1);
            this.shareGroup = topicFilter.substring(start + 1, end);
            if (Topics.isWildcardTopic(shareGroup)) {
                throw new IllegalArgumentException("Invalid topic filter: " + topicFilter);
            }
        } else {
            this.filter = topicFilter;
            this.shareGroup = null;
        }
        this.raw = topicFilter;
    }

    public boolean wildcard() {
        return wildcard;
    }

    public boolean shareable() {
        return shareable;
    }

    public String filter() {
        return filter;
    }

    public String shareGroupName() {
        return shareGroup;
    }

    public String unwrap() {
        return raw;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object instanceof Topic other) {
            return Objects.equals(other.raw, raw);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw);
    }
}
