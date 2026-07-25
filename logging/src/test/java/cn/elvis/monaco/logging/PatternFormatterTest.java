package cn.elvis.monaco.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;

class PatternFormatterTest {

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void basicPattern() {
        var formatter = new PatternFormatter("%level %logger - %msg%n", false);
        var record = new LogRecord(LogLevel.INFO, "hello");
        record.setLoggerName("test");

        String output = formatter.format(record);
        assertTrue(output.startsWith("INFO test - hello"));
    }

    @Test
    void mdcPlaceholder() {
        var formatter = new PatternFormatter("[%X{clientId}] %msg%n", false);

        MDC.put("clientId", "client-42");
        var record = new LogRecord(LogLevel.INFO, "connected");
        record.setLoggerName("test");

        String output = formatter.format(record);
        assertTrue(output.startsWith("[client-42] connected"));
    }

    @Test
    void mdcPlaceholderMissingKey() {
        var formatter = new PatternFormatter("[%X{missing}] %msg%n", false);
        var record = new LogRecord(LogLevel.INFO, "msg");
        record.setLoggerName("test");

        String output = formatter.format(record);
        assertTrue(output.startsWith("[] msg"));
    }

    @Test
    void datePattern() {
        var formatter = new PatternFormatter("%d{yyyy} %msg%n", false);
        var record = new LogRecord(LogLevel.INFO, "msg");
        record.setLoggerName("test");

        String output = formatter.format(record);
        // Should start with a 4-digit year
        assertTrue(output.matches("(?s)\\d{4} msg.*"));
    }

    @Test
    void ansiColoring() {
        var formatter = new PatternFormatter("%level %msg%n", true);
        var record = new LogRecord(LogLevel.ERROR, "fail");
        record.setLoggerName("test");

        String output = formatter.format(record);
        assertTrue(output.contains(ANSI.RESET));
    }

    @Test
    void errorWithStacktrace() {
        var formatter = new PatternFormatter("%msg%n", false);
        var record = new LogRecord(LogLevel.ERROR, "oops");
        record.setLoggerName("test");
        record.setThrown(new RuntimeException("boom"));

        String output = formatter.format(record);
        assertTrue(output.contains("RuntimeException"));
        assertTrue(output.contains("boom"));
    }
}
