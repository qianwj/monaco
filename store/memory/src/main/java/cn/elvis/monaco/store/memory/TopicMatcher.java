package cn.elvis.monaco.store.memory;

/**
 * MQTT topic filter matching utility.
 * Supports '+' (single-level) and '#' (multi-level) wildcards.
 */
final class TopicMatcher {

    private TopicMatcher() {}

    static boolean matches(String filter, String topicName) {
        String[] filterLevels = filter.split("/", -1);
        String[] topicLevels = topicName.split("/", -1);

        for (int i = 0; i < filterLevels.length; i++) {
            String f = filterLevels[i];

            if (f.equals("#")) {
                // '#' must be the last level and matches everything remaining
                return true;
            }

            if (i >= topicLevels.length) {
                return false;
            }

            if (!f.equals("+") && !f.equals(topicLevels[i])) {
                return false;
            }
        }

        return filterLevels.length == topicLevels.length;
    }
}
