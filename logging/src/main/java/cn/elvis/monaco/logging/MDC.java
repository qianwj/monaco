package cn.elvis.monaco.logging;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Mapped Diagnostic Context — stores per-thread key-value pairs
 * that can be included in log output via {@code %X{key}} in PatternFormatter.
 */
public final class MDC {

    private static final ThreadLocal<Map<String, String>> CONTEXT =
            ThreadLocal.withInitial(HashMap::new);

    private MDC() {}

    public static void put(String key, String value) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        CONTEXT.get().put(key, value);
    }

    public static String get(String key) {
        return CONTEXT.get().get(key);
    }

    public static void remove(String key) {
        CONTEXT.get().remove(key);
    }

    public static void clear() {
        CONTEXT.get().clear();
    }

    public static Map<String, String> getCopyOfContextMap() {
        return Collections.unmodifiableMap(new HashMap<>(CONTEXT.get()));
    }

    /**
     * Package-private: used by PatternFormatter to read current context.
     */
    static Map<String, String> getContextMap() {
        return CONTEXT.get();
    }
}
