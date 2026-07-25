package cn.elvis.monaco.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JulLoggerTest {

    @Test
    void formatMessageWithPlaceholders() {
        assertEquals("hello world", JulLogger.formatMessage("hello {}", "world"));
        assertEquals("a=1, b=2", JulLogger.formatMessage("a={}, b={}", 1, 2));
        assertEquals("no args", JulLogger.formatMessage("no args"));
    }

    @Test
    void formatMessageWithExcessPlaceholders() {
        assertEquals("a=1, b={}", JulLogger.formatMessage("a={}, b={}", 1));
    }

    @Test
    void formatMessageWithEscapedPlaceholder() {
        assertEquals("literal {}", JulLogger.formatMessage("literal \\{}", "ignored"));
    }

    @Test
    void formatMessageWithNullArgs() {
        assertEquals("hello", JulLogger.formatMessage("hello", (Object[]) null));
        assertNull(JulLogger.formatMessage(null, "arg"));
    }

    @Test
    void formatMessageWithNullValue() {
        assertEquals("value=null", JulLogger.formatMessage("value={}", (Object) null));
    }

    @Test
    void isLevelEnabledReflectsJulLevel() {
        // Configure a logger with INFO level
        var julLogger = java.util.logging.Logger.getLogger("test.level");
        julLogger.setLevel(LogLevel.INFO);

        var logger = new JulLogger(julLogger);
        assertTrue(logger.isInfoEnabled());
        assertTrue(logger.isWarnEnabled());
        assertTrue(logger.isErrorEnabled());
        assertFalse(logger.isDebugEnabled());
        assertFalse(logger.isTraceEnabled());
    }

    @Test
    void logAtAllLevelsDoesNotThrow() {
        Logger logger = LoggerFactory.getLogger("test.allLevels");
        assertDoesNotThrow(() -> {
            logger.trace("trace {}", "msg");
            logger.debug("debug {}", "msg");
            logger.info("info {}", "msg");
            logger.warn("warn {}", "msg");
            logger.error("error {}", "msg");
            logger.error("error with throwable", new RuntimeException("test"));
            logger.trace("trace with throwable", new RuntimeException("test"));
        });
    }
}
