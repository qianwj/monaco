package cn.elvis.monaco.store.memory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicMatcherTest {

    @Test
    void exactMatch() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/b/c"));
    }

    @Test
    void exactMismatch() {
        assertFalse(TopicMatcher.matches("a/b/c", "a/b/d"));
    }

    @Test
    void singleLevelWildcard() {
        assertTrue(TopicMatcher.matches("a/+/c", "a/b/c"));
        assertTrue(TopicMatcher.matches("+/b/c", "a/b/c"));
        assertTrue(TopicMatcher.matches("a/b/+", "a/b/c"));
    }

    @Test
    void singleLevelWildcardNoMatch() {
        assertFalse(TopicMatcher.matches("a/+", "a/b/c"));
    }

    @Test
    void multiLevelWildcard() {
        assertTrue(TopicMatcher.matches("a/#", "a/b/c"));
        assertTrue(TopicMatcher.matches("a/#", "a"));
        assertTrue(TopicMatcher.matches("#", "a/b/c"));
    }

    @Test
    void multiLevelWildcardAtRoot() {
        assertTrue(TopicMatcher.matches("#", "any/topic/here"));
    }

    @Test
    void filterLongerThanTopic() {
        assertFalse(TopicMatcher.matches("a/b/c/d", "a/b/c"));
    }

    @Test
    void topicLongerThanFilter() {
        assertFalse(TopicMatcher.matches("a/b", "a/b/c"));
    }

    @Test
    void emptyLevels() {
        assertTrue(TopicMatcher.matches("a//b", "a//b"));
        assertFalse(TopicMatcher.matches("a/b", "a//b"));
    }
}
