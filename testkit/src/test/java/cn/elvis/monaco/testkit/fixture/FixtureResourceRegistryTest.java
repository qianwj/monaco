package cn.elvis.monaco.testkit.fixture;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FixtureResourceRegistryTest {

    @Test
    void closesResourcesInReverseOrderAndOnlyOnce() throws Exception {
        List<String> closed = new ArrayList<>();
        FixtureResourceRegistry registry = new FixtureResourceRegistry();
        TestResource first = resource("first", closed);
        TestResource second = resource("second", closed);

        assertSame(first, registry.register(first));
        assertSame(second, registry.register(second));
        assertFalse(registry.isClosed());

        registry.close();
        registry.close();

        assertEquals(List.of("second", "first"), closed);
        assertTrue(registry.isClosed());
    }

    @Test
    void preservesStartupFailureAndSuppressesEveryCleanupFailure() {
        List<String> closed = new ArrayList<>();
        FixtureResourceRegistry registry = new FixtureResourceRegistry();
        TestException firstCleanup = new TestException("first cleanup");
        TestException secondCleanup = new TestException("second cleanup");
        registry.register(resource("first", closed, firstCleanup));
        registry.register(resource("second", closed, secondCleanup));
        TestException startupFailure = new TestException("second stage startup");

        registry.closeAfter(startupFailure);

        assertEquals(List.of("second", "first"), closed);
        assertEquals(List.of(secondCleanup, firstCleanup), List.of(startupFailure.getSuppressed()));
        assertTrue(registry.isClosed());
    }

    @Test
    void closeThrowsFirstCleanupFailureAndSuppressesLaterFailures() {
        FixtureResourceRegistry registry = new FixtureResourceRegistry();
        TestException firstCleanup = new TestException("first cleanup");
        TestException secondCleanup = new TestException("second cleanup");
        registry.register(() -> {
            throw firstCleanup;
        });
        registry.register(() -> {
            throw secondCleanup;
        });

        TestException thrown = assertThrows(TestException.class, registry::close);

        assertSame(secondCleanup, thrown);
        assertEquals(List.of(firstCleanup), List.of(thrown.getSuppressed()));
    }

    @Test
    void rejectsResourcesRegisteredAfterClose() throws Exception {
        FixtureResourceRegistry registry = new FixtureResourceRegistry();
        registry.close();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> registry.register(() -> {
                }));

        assertEquals("fixture resource registry is already closed", error.getMessage());
    }

    private static TestResource resource(String name, List<String> closed) {
        return resource(name, closed, null);
    }

    private static TestResource resource(String name, List<String> closed, RuntimeException closeFailure) {
        return new TestResource(name, closed, closeFailure);
    }

    private record TestResource(
            String name,
            List<String> closed,
            RuntimeException closeFailure) implements AutoCloseable {

        @Override
        public void close() {
            closed.add(name);
            if (closeFailure != null) {
                throw closeFailure;
            }
        }
    }

    private static final class TestException extends RuntimeException {

        private TestException(String message) {
            super(message);
        }
    }
}
