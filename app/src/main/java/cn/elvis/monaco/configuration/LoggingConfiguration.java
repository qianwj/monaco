package cn.elvis.monaco.configuration;

import io.vertx.core.json.JsonObject;

public class LoggingConfiguration {

    public static LoggingConfiguration parse(JsonObject config) {
        return config.getJsonObject("logging").mapTo(LoggingConfiguration.class);
    }

    private String rootLevel;

    private ConsoleLoggerConfiguration console;

    public static class ConsoleLoggerConfiguration {

        private String level;

        private boolean enabled;

        private String pattern;

        public String getLevel() {
            return level;
        }

        public ConsoleLoggerConfiguration setLevel(String level) {
            this.level = level;
            return this;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public ConsoleLoggerConfiguration setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public String getPattern() {
            return pattern;
        }

        public ConsoleLoggerConfiguration setPattern(String pattern) {
            this.pattern = pattern;
            return this;
        }
    }

    public String getRootLevel() {
        return rootLevel;
    }

    public LoggingConfiguration setRootLevel(String rootLevel) {
        this.rootLevel = rootLevel;
        return this;
    }

    public ConsoleLoggerConfiguration getConsole() {
        return console;
    }

    public LoggingConfiguration setConsole(ConsoleLoggerConfiguration console) {
        this.console = console;
        return this;
    }
}
