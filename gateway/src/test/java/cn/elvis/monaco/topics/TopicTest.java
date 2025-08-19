package cn.elvis.monaco.topics;

import org.junit.jupiter.api.Test;

import static cn.elvis.monaco.topics.Topics.isValidTopicFilter;

public class TopicTest {

    @Test
    public void testTopic() {
        var topic = Topics.createTopic("/a/b/c");
        assert !topic.wildcard();
        assert !topic.shareable();
        assert "/a/b/c".equals(topic.filter());

        var wildcard = Topics.createTopic("/a/+/c");
        assert wildcard.wildcard();
        assert !wildcard.shareable();
        assert "/a/+/c".equals(wildcard.filter());

        var share = Topics.createTopic("$share/a/b/c");
        assert !share.wildcard();
        assert share.shareable();
        assert "a".equals(share.shareGroupName());
        assert "b/c".equals(share.filter());
    }

    @Test
    public void testTopicValidator() {
        // 有效主题
        String[] validTopics = {
                "sport/tennis/player1",
                "sport/+/player1",
                "sport/#",
                "$share/group1/sport/tennis",
        };

        // 无效主题
        String[] invalidTopics = {
                null,
                "",
                "sport//player",          // 空层级
                "sport/tennis#",          // #不在末尾
                "sport/tennis/#/player",  // #后还有层级
                "sport/tennis+/player",   // +不在单独层级
                "sport/++/player",        // 多个+
                "$invalid/group/topic",   // 无效共享前缀
                "$share//topic",           // 空组名
                "$share/group#/topic",    // 组名包含通配符
                "topic\u0000"             // 空字符
        };

        System.out.println("===== 有效主题测试 =====");
        for (String topic : validTopics) {
            boolean valid = isValidTopicFilter(topic);
            System.out.printf("%s : %s%n", topic, valid ? "有效" : "无效");
            assert valid;
        }

        System.out.println("\n===== 无效主题测试 =====");
        for (String topic : invalidTopics) {
            boolean valid = isValidTopicFilter(topic);
            System.out.printf("%s : %s%n", topic, valid ? "有效" : "无效");
            assert !valid;
        }
    }
}
