package cn.elvis.monaco.testkit.assertion;

public final class StateAssertions {

    private StateAssertions() {
    }

    public static void assertStateEquals(Object expected, Object actual) {
        DeepAssertions.assertDeepEquals(expected, actual, "state");
    }
}
