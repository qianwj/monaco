package cn.elvis.monaco.logging;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Level;

public class Configuration {

    private Level root = LogLevel.INFO;
    private Console console;
    private boolean async;
    private Map<String, Level> logLevels = new HashMap<>();

    public Level root() {
        return root;
    }

    public Console console() {
        return console;
    }

    public boolean async() {
        return async;
    }

    public Map<String, Level> logLevels() {
        return logLevels;
    }

    public Configuration setRoot(Level root) {
        this.root = root;
        return this;
    }

    public Configuration setConsole(Console console) {
        this.console = console;
        return this;
    }

    public Configuration setAsync(boolean async) {
        this.async = async;
        return this;
    }

    public Configuration setLogLevels(Map<String, Level> logLevels) {
        this.logLevels = logLevels;
        return this;
    }

    public Configuration addLogLevel(String packageName, Level level) {
        this.logLevels.put(packageName, level);
        return this;
    }

    /**
     * Returns a sensible default: INFO root level, ANSI-colored console output.
     */
    public static Configuration defaultConfiguration() {
        return new Configuration()
                .setRoot(LogLevel.INFO)
                .setConsole(Console.withDefaults());
    }

    public static class Console {

        private Level level;
        private Formatter formatter;

        private Console() {}

        public Level level() {
            return level;
        }

        public Formatter formatter() {
            return formatter;
        }

        /**
         * Console with INFO level and default ANSI-colored pattern.
         */
        public static Console withDefaults() {
            return builder().build();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private Level level = LogLevel.INFO;
            private String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %level %logger - %msg%n";
            private boolean useAnsi = true;

            private Builder() {}

            public Builder level(Level level) {
                this.level = level;
                return this;
            }

            public Builder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }

            public Builder useAnsi(boolean useAnsi) {
                this.useAnsi = useAnsi;
                return this;
            }

            public Console build() {
                var console = new Console();
                console.level = this.level;
                console.formatter = new PatternFormatter(this.pattern, this.useAnsi);
                return console;
            }
        }
    }
}
