package cn.elvis.monaco.protocol.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class TopicNameValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "a/b/c",
            "a",
            "/",
            "/a",
            "a/",
            "a/b/c/d/e",
            "$SYS/broker/uptime",
            "sport/tennis/player1",
            "sensor/temperature"
    })
    void validTopicNames(String name) {
        assertTrue(TopicNameValidator.isValid(name), "Should be valid: " + name);
    }

    @Test
    void rejectsNull() {
        assertFalse(TopicNameValidator.isValid(null));
    }

    @Test
    void rejectsEmpty() {
        assertFalse(TopicNameValidator.isValid(""));
    }

    @Test
    void rejectsNullCharacter() {
        assertFalse(TopicNameValidator.isValid("a/b\u0000/c"));
    }

    @Test
    void rejectsSingleLevelWildcard() {
        assertFalse(TopicNameValidator.isValid("a/+/c"));
        assertFalse(TopicNameValidator.isValid("+"));
    }

    @Test
    void rejectsMultiLevelWildcard() {
        assertFalse(TopicNameValidator.isValid("a/#"));
        assertFalse(TopicNameValidator.isValid("#"));
    }

    @Test
    void rejectsWildcardInLevel() {
        assertFalse(TopicNameValidator.isValid("a/b+c/d"));
        assertFalse(TopicNameValidator.isValid("a/b#c/d"));
    }
}
