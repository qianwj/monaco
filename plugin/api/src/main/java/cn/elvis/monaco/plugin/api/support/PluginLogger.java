package cn.elvis.monaco.plugin.api.support;

/** Logger facade that automatically receives the owning plugin identifier. */
@FunctionalInterface
public interface PluginLogger {

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    void log(Level level, String message, Throwable error);

    default void debug(String message) {
        log(Level.DEBUG, message, null);
    }

    default void info(String message) {
        log(Level.INFO, message, null);
    }

    default void warn(String message) {
        log(Level.WARN, message, null);
    }

    default void error(String message, Throwable error) {
        log(Level.ERROR, message, error);
    }

    static PluginLogger noop() {
        return (level, message, error) -> { };
    }
}
