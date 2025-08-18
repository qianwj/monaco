package cn.elvis.monaco.topics;

public final class Topics {

    public static final String SINGLE_WILDCARD_TOKEN = "+";

    public static final String MULTI_WILDCARD_TOKEN = "*";

    public static final String SHARE_PREFIX = "$share";

    public static boolean isWildCardTopic(String topicFilter) {
        return topicFilter.contains(SINGLE_WILDCARD_TOKEN) || topicFilter.contains(MULTI_WILDCARD_TOKEN);
    }

    public static boolean isShareTopic(String topicFilter) {
        return topicFilter.startsWith(SHARE_PREFIX);
    }

    public static Topic createTopic(String topicFilter) {
        return new Topic(topicFilter);
    }
}
