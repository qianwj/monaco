package cn.elvis.monaco.topics;

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


    Topic(String topicFilter) {
        this.wildcard = Topics.isWildCardTopic(topicFilter);
        this.shareable = Topics.isShareTopic(topicFilter);
        if (shareable) {
            int start = topicFilter.indexOf('/');
            if (start == -1) {
                throw new IllegalArgumentException("Invalid topic filter: " + topicFilter);
            }
            int end = topicFilter.indexOf('/', start + 1);
            this.filter = topicFilter.substring(end + 1);
            this.shareGroup = topicFilter.substring(start + 1, end);
            if (Topics.isWildCardTopic(shareGroup)) {
                throw new IllegalArgumentException("Invalid topic filter: " + topicFilter);
            }
        } else {
            this.filter = topicFilter;
            this.shareGroup = null;
        }
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
}
