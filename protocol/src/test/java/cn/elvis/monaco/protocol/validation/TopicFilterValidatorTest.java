package cn.elvis.monaco.protocol.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TopicFilterValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "a/b/c",
            "#",
            "+",
            "a/#",
            "a/+/c",
            "+/+/+",
            "a/b/+",
            "+/b/#",
            "/",
            "/a",
            "a/",
            "$SYS/#",
            "$share/group/a/b/c",
            "$share/group/+/b",
            "$share/group/#"
    })
    void validTopicFilters(String filter) {
        assertTrue(TopicFilterValidator.isValid(filter), "Should be valid: " + filter);
    }

    @Test
    void rejectsNull() {
        assertFalse(TopicFilterValidator.isValid(null));
    }

    @Test
    void rejectsEmpty() {
        assertFalse(TopicFilterValidator.isValid(""));
    }

    @Test
    void rejectsNullCharacter() {
        assertFalse(TopicFilterValidator.isValid("a/\u0000/b"));
    }

    @Test
    void rejectsHashNotAtEnd() {
        assertFalse(TopicFilterValidator.isValid("a/#/b"));
        assertFalse(TopicFilterValidator.isValid("#/a"));
    }

    @Test
    void rejectsWildcardMixedInLevel() {
        assertFalse(TopicFilterValidator.isValid("a/b+c/d"));
        assertFalse(TopicFilterValidator.isValid("a/b#/d"));
        assertFalse(TopicFilterValidator.isValid("a/+b/d"));
    }

    @Test
    void rejectsSharedWithEmptyGroup() {
        assertFalse(TopicFilterValidator.isValid("$share//topic"));
    }

    @Test
    void rejectsSharedWithWildcardInGroup() {
        assertFalse(TopicFilterValidator.isValid("$share/+/topic"));
        assertFalse(TopicFilterValidator.isValid("$share/#/topic"));
    }

    @Test
    void rejectsSharedWithNoFilter() {
        assertFalse(TopicFilterValidator.isValid("$share/group"));
        assertFalse(TopicFilterValidator.isValid("$share/group/"));
    }
}
