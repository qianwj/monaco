package cn.elvis.monaco.testkit.assertion;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class StateAssertionsTest {

    @Test
    void comparesMapsByKeysAndDeepValues() {
        Map<String, byte[]> expected = new LinkedHashMap<>();
        expected.put("first", new byte[]{1, 2});
        expected.put("second", new byte[]{3, 4});
        Map<String, byte[]> actual = new LinkedHashMap<>();
        actual.put("second", new byte[]{3, 4});
        actual.put("first", new byte[]{1, 2});

        assertDoesNotThrow(() -> StateAssertions.assertStateEquals(expected, actual));
    }
}
