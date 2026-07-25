package cn.elvis.monaco.protocol.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TopicMatcherTest {

    // --- Exact match ---

    @Test
    void exactMatch() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/b/c"));
    }

    @Test
    void exactMismatch() {
        assertFalse(TopicMatcher.matches("a/b/c", "a/b/d"));
    }

    @Test
    void differentLevelCount() {
        assertFalse(TopicMatcher.matches("a/b", "a/b/c"));
        assertFalse(TopicMatcher.matches("a/b/c", "a/b"));
    }

    // --- Single-level wildcard (+) ---

    @Test
    void singleWildcardMiddle() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/+/c"));
    }

    @Test
    void singleWildcardFirst() {
        assertTrue(TopicMatcher.matches("a/b/c", "+/b/c"));
    }

    @Test
    void singleWildcardLast() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/b/+"));
    }

    @Test
    void singleWildcardAll() {
        assertTrue(TopicMatcher.matches("a/b/c", "+/+/+"));
    }

    @Test
    void singleWildcardDoesNotMatchMultipleLevels() {
        assertFalse(TopicMatcher.matches("a/b/c", "+"));
    }

    @Test
    void singleWildcardMatchesEmptyLevel() {
        assertTrue(TopicMatcher.matches("a//c", "a/+/c"));
    }

    // --- Multi-level wildcard (#) ---

    @Test
    void multiWildcardAlone() {
        assertTrue(TopicMatcher.matches("a/b/c", "#"));
        assertTrue(TopicMatcher.matches("a", "#"));
    }

    @Test
    void multiWildcardAtEnd() {
        assertTrue(TopicMatcher.matches("a/b/c", "a/#"));
        assertTrue(TopicMatcher.matches("a/b", "a/#"));
        assertTrue(TopicMatcher.matches("a", "a/#"));
    }

    @Test
    void multiWildcardMatchesZeroLevels() {
        // "a/#" should match "a" — # matches zero or more levels
        assertTrue(TopicMatcher.matches("a", "a/#"));
    }

    @Test
    void multiWildcardDeeper() {
        assertTrue(TopicMatcher.matches("a/b/c/d/e", "a/b/#"));
    }

    // --- $ prefix topics ---

    @Test
    void dollarTopicNotMatchedByLeadingPlus() {
        assertFalse(TopicMatcher.matches("$SYS/broker/uptime", "+/broker/uptime"));
    }

    @Test
    void dollarTopicNotMatchedByLeadingHash() {
        assertFalse(TopicMatcher.matches("$SYS/broker/uptime", "#"));
    }

    @Test
    void dollarTopicMatchedByExactFilter() {
        assertTrue(TopicMatcher.matches("$SYS/broker/uptime", "$SYS/broker/uptime"));
    }

    @Test
    void dollarTopicMatchedByPrefixPlusWildcard() {
        assertTrue(TopicMatcher.matches("$SYS/broker/uptime", "$SYS/#"));
        assertTrue(TopicMatcher.matches("$SYS/broker/uptime", "$SYS/+/uptime"));
    }

    // --- Edge cases ---

    @Test
    void nullInputs() {
        assertFalse(TopicMatcher.matches(null, "a/b"));
        assertFalse(TopicMatcher.matches("a/b", null));
        assertFalse(TopicMatcher.matches(null, null));
    }

    @Test
    void rootLevel() {
        assertTrue(TopicMatcher.matches("/", "/"));
        assertTrue(TopicMatcher.matches("/a", "/a"));
    }

    @Test
    void trailingSlash() {
        assertFalse(TopicMatcher.matches("a/b/", "a/b"));
        assertTrue(TopicMatcher.matches("a/b/", "a/b/"));
    }
}
