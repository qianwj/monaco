package cn.elvis.monaco.topics;

import org.junit.jupiter.api.Test;

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
}
