package cn.elvis.monaco.plugin.runtime.invoke;

/** Normalized local or remote invocation failure. */
public final class PluginInvocationException extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        QUEUE_FULL,
        CIRCUIT_OPEN,
        CLOSED,
        FAILURE
    }

    private final Kind kind;

    public PluginInvocationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PluginInvocationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
