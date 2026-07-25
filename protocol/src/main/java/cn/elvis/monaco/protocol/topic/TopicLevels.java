package cn.elvis.monaco.protocol.topic;

/**
 * Utility for splitting topic names and filters into levels by '/'.
 */
public final class TopicLevels {

    private TopicLevels() {
    }

    /**
     * Split a topic string into levels. Uses -1 limit to preserve trailing empty strings.
     */
    public static String[] split(String topic) {
        return topic.split("/", -1);
    }

    /**
     * Returns the number of levels in a topic.
     */
    public static int count(String topic) {
        return split(topic).length;
    }
}
