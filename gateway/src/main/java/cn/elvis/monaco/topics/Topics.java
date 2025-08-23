package cn.elvis.monaco.topics;

import java.util.regex.Pattern;

/**
 * mqtt topic tools
 *
 * @author qianwj
 * @since  0.0.1
 */
public final class Topics {

    // 定义合法字符集（UTF-8字符减去空字符和控制字符）
    private static final Pattern VALID_CHARS = Pattern.compile("[\\u0001-\\uD7FF\\uE000-\\uFFFF]+");

    public static final String SINGLE_WILDCARD_TOKEN = "+";

    public static final String MULTI_WILDCARD_TOKEN = "#";

    public static final String SHARE_PREFIX = "$share";

    public static boolean isWildcardTopic(String topicFilter) {
        return topicFilter.contains(SINGLE_WILDCARD_TOKEN) || topicFilter.contains(MULTI_WILDCARD_TOKEN);
    }

    public static boolean isShareTopic(String topicFilter) {
        return topicFilter.startsWith(SHARE_PREFIX);
    }

    public static Topic createTopic(String topicFilter) {
        return new Topic(topicFilter);
    }


    /**
     * 校验MQTT主题过滤器是否合法
     * @param topicFilter 要校验的主题过滤器
     * @return 是否合法
     */
    public static boolean isValidTopicFilter(String topicFilter) {
        if (topicFilter == null || topicFilter.isEmpty()) {
            return false;
        }

        // 检查是否包含空字符（U+0000）
        if (topicFilter.contains("\u0000")) {
            return false;
        }

        // 处理共享订阅主题 (格式: $share/<group>/topic)
        if (topicFilter.startsWith("$")) {
            return isValidSharedSubscription(topicFilter);
        }

        // 处理普通主题
        return isValidStandardTopic(topicFilter);
    }

    /**
     * 校验共享订阅主题
     */
    private static boolean isValidSharedSubscription(String topic) {
        String[] parts = topic.split("/", 3);
        if (parts.length < 3) {
            return false;
        }

        // 验证前缀 ($share)
        String prefix = parts[0];
        if (!SHARE_PREFIX.equals(prefix)) {
            return false;
        }

        // 验证组名 (不能为空且不能包含通配符)
        String group = parts[1];
        if (group.isEmpty() || containsWildcard(group)) {
            return false;
        }

        // 验证实际主题部分
        String actualTopic = parts[2];
        return !actualTopic.isEmpty() && isValidStandardTopic(actualTopic);
    }

    /**
     * 校验标准主题
     */
    private static boolean isValidStandardTopic(String topic) {
        String[] levels = topic.split("/");
        boolean foundMultiWildcard = false;

        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];

            // 检查多级通配符位置
            if (MULTI_WILDCARD_TOKEN.equals(level)) {
                if (i != levels.length - 1) {
                    return false; // # 必须在最后一级
                }
                foundMultiWildcard = true;
            }
            // 检查单级通配符
            else if (SINGLE_WILDCARD_TOKEN.equals(level)) {
                // 单级通配符位置合法，继续
            }
            // 检查普通层级
            else {
                if (!isValidTopicLevel(level)) {
                    return false;
                }
            }

            // 检查多级通配符后的内容
            if (foundMultiWildcard && i < levels.length - 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * 校验主题层级是否合法
     */
    private static boolean isValidTopicLevel(String level) {
        // 检查非法字符
        if (!VALID_CHARS.matcher(level).matches()) {
            return false;
        }

        // 检查是否包含非法通配符组合
        return !containsWildcard(level);
    }

    /**
     * 检查是否包含通配符（非法位置）
     */
    private static boolean containsWildcard(String level) {
        return level.contains(MULTI_WILDCARD_TOKEN) || level.contains(SINGLE_WILDCARD_TOKEN);
    }
}
