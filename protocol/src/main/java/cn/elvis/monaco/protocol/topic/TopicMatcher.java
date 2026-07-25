package cn.elvis.monaco.protocol.topic;

/**
 * Matches a topic name against a topic filter according to MQTT 5.0 rules.
 * <p>
 * Rules:
 * - '+' matches exactly one topic level
 * - '#' matches zero or more levels, must be last level
 * - A filter starting with wildcard does NOT match topics starting with '$'
 * - Exact string match for non-wildcard levels
 */
public final class TopicMatcher {

    private TopicMatcher() {
    }

    /**
     * Check if a topic name matches a topic filter.
     *
     * @param topicName   the published topic name (no wildcards)
     * @param topicFilter the subscription filter (may contain + and #)
     * @return true if the topic name matches the filter
     */
    public static boolean matches(String topicName, String topicFilter) {
        if (topicName == null || topicFilter == null) {
            return false;
        }

        // Topics starting with '$' are not matched by filters starting with wildcard
        if (topicName.startsWith("$")) {
            if (topicFilter.startsWith("+") || topicFilter.startsWith("#")) {
                return false;
            }
        }

        String[] nameLevels = TopicLevels.split(topicName);
        String[] filterLevels = TopicLevels.split(topicFilter);

        return matchLevels(nameLevels, filterLevels, 0, 0);
    }

    private static boolean matchLevels(String[] name, String[] filter, int ni, int fi) {
        while (fi < filter.length) {
            String filterLevel = filter[fi];

            if ("#".equals(filterLevel)) {
                // '#' matches everything remaining (including nothing)
                return true;
            }

            if (ni >= name.length) {
                // Name exhausted but filter still has levels
                return false;
            }

            if ("+".equals(filterLevel)) {
                // '+' matches exactly one level — move both forward
                ni++;
                fi++;
            } else {
                // Exact match required
                if (!filterLevel.equals(name[ni])) {
                    return false;
                }
                ni++;
                fi++;
            }
        }

        // Both must be exhausted for a match
        return ni == name.length;
    }
}
