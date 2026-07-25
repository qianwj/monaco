package cn.elvis.monaco.logging;

import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Logger implementation backed by {@link java.util.logging.Logger}.
 * Supports {@code {}} placeholder substitution in format strings.
 */
class JulLogger implements Logger {

    private final java.util.logging.Logger jul;

    JulLogger(java.util.logging.Logger jul) {
        this.jul = jul;
    }

    @Override
    public String getName() {
        return jul.getName();
    }

    // ---- TRACE ----

    @Override
    public boolean isTraceEnabled() {
        return jul.isLoggable(LogLevel.TRACE);
    }

    @Override
    public void trace(String msg) {
        log(LogLevel.TRACE, msg, null);
    }

    @Override
    public void trace(String format, Object... args) {
        if (isTraceEnabled()) log(LogLevel.TRACE, formatMessage(format, args), null);
    }

    @Override
    public void trace(String msg, Throwable t) {
        log(LogLevel.TRACE, msg, t);
    }

    // ---- DEBUG ----

    @Override
    public boolean isDebugEnabled() {
        return jul.isLoggable(LogLevel.DEBUG);
    }

    @Override
    public void debug(String msg) {
        log(LogLevel.DEBUG, msg, null);
    }

    @Override
    public void debug(String format, Object... args) {
        if (isDebugEnabled()) log(LogLevel.DEBUG, formatMessage(format, args), null);
    }

    @Override
    public void debug(String msg, Throwable t) {
        log(LogLevel.DEBUG, msg, t);
    }

    // ---- INFO ----

    @Override
    public boolean isInfoEnabled() {
        return jul.isLoggable(LogLevel.INFO);
    }

    @Override
    public void info(String msg) {
        log(LogLevel.INFO, msg, null);
    }

    @Override
    public void info(String format, Object... args) {
        if (isInfoEnabled()) log(LogLevel.INFO, formatMessage(format, args), null);
    }

    @Override
    public void info(String msg, Throwable t) {
        log(LogLevel.INFO, msg, t);
    }

    // ---- WARN ----

    @Override
    public boolean isWarnEnabled() {
        return jul.isLoggable(LogLevel.WARN);
    }

    @Override
    public void warn(String msg) {
        log(LogLevel.WARN, msg, null);
    }

    @Override
    public void warn(String format, Object... args) {
        if (isWarnEnabled()) log(LogLevel.WARN, formatMessage(format, args), null);
    }

    @Override
    public void warn(String msg, Throwable t) {
        log(LogLevel.WARN, msg, t);
    }

    // ---- ERROR ----

    @Override
    public boolean isErrorEnabled() {
        return jul.isLoggable(LogLevel.ERROR);
    }

    @Override
    public void error(String msg) {
        log(LogLevel.ERROR, msg, null);
    }

    @Override
    public void error(String format, Object... args) {
        if (isErrorEnabled()) log(LogLevel.ERROR, formatMessage(format, args), null);
    }

    @Override
    public void error(String msg, Throwable t) {
        log(LogLevel.ERROR, msg, t);
    }

    // ---- internal ----

    private void log(Level level, String msg, Throwable thrown) {
        if (!jul.isLoggable(level)) return;
        var record = new LogRecord(level, msg);
        record.setLoggerName(jul.getName());
        record.setThrown(thrown);
        // Capture caller info, skipping JulLogger frames
        inferCaller(record);
        jul.log(record);
    }

    /**
     * Replace {@code {}} placeholders with arguments, similar to SLF4J.
     * If the last argument is a Throwable and there are more arguments than
     * placeholders, it is ignored here (caller should pass it separately).
     */
    static String formatMessage(String format, Object... args) {
        if (format == null || args == null || args.length == 0) return format;
        var sb = new StringBuilder(format.length() + 32);
        int argIdx = 0;
        int i = 0;
        while (i < format.length()) {
            if (i + 1 < format.length() && format.charAt(i) == '{' && format.charAt(i + 1) == '}') {
                if (argIdx < args.length) {
                    sb.append(args[argIdx++]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else if (format.charAt(i) == '\\' && i + 2 < format.length()
                    && format.charAt(i + 1) == '{' && format.charAt(i + 2) == '}') {
                // escaped \{} -> literal {}
                sb.append("{}");
                i += 3;
            } else {
                sb.append(format.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private void inferCaller(LogRecord record) {
        var stack = Thread.currentThread().getStackTrace();
        boolean found = false;
        for (var frame : stack) {
            if (found) {
                String className = frame.getClassName();
                if (!className.equals(JulLogger.class.getName())) {
                    record.setSourceClassName(className);
                    record.setSourceMethodName(frame.getMethodName());
                    return;
                }
            } else if (frame.getClassName().equals(JulLogger.class.getName())) {
                found = true;
            }
        }
    }
}
