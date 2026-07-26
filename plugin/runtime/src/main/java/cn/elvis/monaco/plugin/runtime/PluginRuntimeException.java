package cn.elvis.monaco.plugin.runtime;

/** Indicates an invalid plugin package, configuration, or runtime transition. */
public class PluginRuntimeException extends RuntimeException {

    public PluginRuntimeException(String message) {
        super(message);
    }

    public PluginRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
