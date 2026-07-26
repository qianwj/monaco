package cn.elvis.monaco.testkit.assertion;

import cn.elvis.monaco.testkit.probe.TraceEntry;
import cn.elvis.monaco.testkit.probe.TraceEvent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public final class TraceAssertions {

    private TraceAssertions() {
    }

    @SafeVarargs
    public static void assertContainsInOrder(
            List<TraceEntry> trace,
            Class<? extends TraceEvent>... expectedEventTypes) {
        int expectedIndex = 0;
        for (TraceEntry entry : trace) {
            if (expectedIndex < expectedEventTypes.length
                    && expectedEventTypes[expectedIndex].isInstance(entry.event())) {
                expectedIndex++;
            }
        }
        if (expectedIndex != expectedEventTypes.length) {
            fail("Expected trace event order " + List.of(expectedEventTypes)
                    + " but matched only " + expectedIndex + "; trace=" + trace);
        }
    }
}
