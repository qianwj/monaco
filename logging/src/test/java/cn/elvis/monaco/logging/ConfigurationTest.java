package cn.elvis.monaco.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {

    @Test
    void defaultConfigurationHasConsole() {
        var config = Configuration.defaultConfiguration();
        assertNotNull(config.console());
        assertEquals(LogLevel.INFO, config.root());
        assertFalse(config.async());
    }

    @Test
    void consoleBuilder() {
        var console = Configuration.Console.builder()
                .level(LogLevel.DEBUG)
                .pattern("%msg%n")
                .useAnsi(false)
                .build();

        assertEquals(LogLevel.DEBUG, console.level());
        assertNotNull(console.formatter());
    }

    @Test
    void fluentApi() {
        var config = new Configuration()
                .setRoot(LogLevel.TRACE)
                .setAsync(true)
                .addLogLevel("cn.elvis", LogLevel.DEBUG);

        assertEquals(LogLevel.TRACE, config.root());
        assertTrue(config.async());
        assertEquals(LogLevel.DEBUG, config.logLevels().get("cn.elvis"));
    }
}
