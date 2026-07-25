package cn.elvis.monaco.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LoggerFactory {

    private static final ConcurrentMap<String, Logger> LOGGERS = new ConcurrentHashMap<>();

    private LoggerFactory() {}

    public static Logger getLogger(String name) {
        return LOGGERS.computeIfAbsent(name, n ->
                new JulLogger(java.util.logging.Logger.getLogger(n)));
    }

    public static Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }
}
