package cn.elvis.monaco.protocol.validation;

/**
 * Validates MQTT Topic Filters (used in SUBSCRIBE/UNSUBSCRIBE).
 * <p>
 * Rules:
 * - Must not be null or empty
 * - Must not contain U+0000
 * - UTF-8 encoded length must be 1-65535 bytes
 * - '#' must occupy an entire level and only at the last position
 * - '+' must occupy an entire level (not mixed with other characters)
 * - Shared subscriptions ($share/group/filter): group must be non-empty, no wildcards in group
 */
public final class TopicFilterValidator {

    private static final String SHARE_PREFIX = "$share/";

    private TopicFilterValidator() {
    }

    public static boolean isValid(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty()) {
            return false;
        }
        if (topicFilter.contains("\u0000")) {
            return false;
        }
        int byteLength = topicFilter.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (byteLength > 65535) {
            return false;
        }

        if (topicFilter.startsWith("$share/")) {
            return isValidSharedSubscription(topicFilter);
        }

        return isValidStandardFilter(topicFilter);
    }

    private static boolean isValidSharedSubscription(String filter) {
        // $share/group/actualFilter
        int groupStart = SHARE_PREFIX.length();
        int groupEnd = filter.indexOf('/', groupStart);
        if (groupEnd == -1) {
            return false; // no second '/' — missing actual filter
        }

        String group = filter.substring(groupStart, groupEnd);
        if (group.isEmpty()) {
            return false;
        }
        if (group.contains("+") || group.contains("#")) {
            return false;
        }

        String actualFilter = filter.substring(groupEnd + 1);
        if (actualFilter.isEmpty()) {
            return false;
        }

        return isValidStandardFilter(actualFilter);
    }

    private static boolean isValidStandardFilter(String filter) {
        String[] levels = filter.split("/", -1);

        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];

            if (level.equals("#")) {
                // '#' must be the last level
                if (i != levels.length - 1) {
                    return false;
                }
            } else if (level.equals("+")) {
                // '+' as entire level is valid at any position
            } else {
                // Regular level must not contain wildcards
                if (level.contains("+") || level.contains("#")) {
                    return false;
                }
            }
        }

        return true;
    }
}
