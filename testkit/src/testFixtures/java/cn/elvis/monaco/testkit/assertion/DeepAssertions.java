package cn.elvis.monaco.testkit.assertion;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.fail;

final class DeepAssertions {

    private DeepAssertions() {
    }

    static void assertDeepEquals(Object expected, Object actual, String path) {
        if (expected == actual) {
            return;
        }
        if (expected == null || actual == null) {
            fail(path + ": expected <" + expected + "> but was <" + actual + ">");
        }
        if (!expected.getClass().equals(actual.getClass())) {
            fail(path + ": expected type <" + expected.getClass().getName() + "> but was <"
                    + actual.getClass().getName() + ">");
        }

        Class<?> type = expected.getClass();
        if (type.isArray()) {
            assertArraysEqual(expected, actual, path);
        } else if (expected instanceof Optional<?> expectedOptional) {
            Optional<?> actualOptional = (Optional<?>) actual;
            if (expectedOptional.isPresent() != actualOptional.isPresent()) {
                fail(path + ": optional presence differs");
            }
            if (expectedOptional.isPresent()) {
                assertDeepEquals(expectedOptional.orElseThrow(), actualOptional.orElseThrow(), path + ".value");
            }
        } else if (expected instanceof Map<?, ?> expectedMap) {
            assertMapsEqual(expectedMap, (Map<?, ?>) actual, path);
        } else if (expected instanceof Iterable<?> expectedIterable) {
            assertIterablesEqual(expectedIterable, (Iterable<?>) actual, path);
        } else if (type.isRecord()) {
            assertRecordsEqual(expected, actual, path);
        } else if (!Objects.equals(expected, actual)) {
            fail(path + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertMapsEqual(Map<?, ?> expected, Map<?, ?> actual, String path) {
        if (!expected.keySet().equals(actual.keySet())) {
            fail(path + ": map keys differ; expected <" + expected.keySet()
                    + "> but was <" + actual.keySet() + ">");
        }
        for (Map.Entry<?, ?> entry : expected.entrySet()) {
            assertDeepEquals(entry.getValue(), actual.get(entry.getKey()),
                    path + "[" + entry.getKey() + "]");
        }
    }

    private static void assertArraysEqual(Object expected, Object actual, String path) {
        int expectedLength = Array.getLength(expected);
        int actualLength = Array.getLength(actual);
        if (expectedLength != actualLength) {
            fail(path + ": expected array length <" + expectedLength + "> but was <" + actualLength + ">");
        }
        for (int index = 0; index < expectedLength; index++) {
            assertDeepEquals(Array.get(expected, index), Array.get(actual, index), path + "[" + index + "]");
        }
    }

    private static void assertIterablesEqual(Iterable<?> expected, Iterable<?> actual, String path) {
        Iterator<?> expectedIterator = expected.iterator();
        Iterator<?> actualIterator = actual.iterator();
        int index = 0;
        while (expectedIterator.hasNext() && actualIterator.hasNext()) {
            assertDeepEquals(expectedIterator.next(), actualIterator.next(), path + "[" + index + "]");
            index++;
        }
        if (expectedIterator.hasNext() || actualIterator.hasNext()) {
            fail(path + ": iterable lengths differ at index " + index);
        }
    }

    private static void assertRecordsEqual(Object expected, Object actual, String path) {
        for (RecordComponent component : expected.getClass().getRecordComponents()) {
            Object expectedValue;
            Object actualValue;
            try {
                expectedValue = component.getAccessor().invoke(expected);
                actualValue = component.getAccessor().invoke(actual);
            } catch (IllegalAccessException | InvocationTargetException error) {
                throw new AssertionError("Cannot inspect " + path + "." + component.getName(), error);
            }
            assertDeepEquals(expectedValue, actualValue, path + "." + component.getName());
        }
    }
}
