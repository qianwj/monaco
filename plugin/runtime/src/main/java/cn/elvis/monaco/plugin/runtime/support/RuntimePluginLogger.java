package cn.elvis.monaco.plugin.runtime.support;

import cn.elvis.monaco.plugin.api.support.PluginLogger;

/** JDK logger adapter that permanently namespaces the owning plugin. */
public final class RuntimePluginLogger implements PluginLogger {

    private final System.Logger logger;

    public RuntimePluginLogger(String pluginId) {
        logger = System.getLogger("cn.elvis.monaco.plugin." + pluginId);
    }

    @Override
    public void log(Level level, String message, Throwable error) {
        System.Logger.Level jdkLevel = switch (level) {
            case DEBUG -> System.Logger.Level.DEBUG;
            case INFO -> System.Logger.Level.INFO;
            case WARN -> System.Logger.Level.WARNING;
            case ERROR -> System.Logger.Level.ERROR;
        };
        String safeMessage = message == null ? "" : message;
        if (error == null) {
            logger.log(jdkLevel, safeMessage);
        } else {
            logger.log(jdkLevel, safeMessage, error);
        }
    }
}
