package cn.elvis.monaco.logging;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LogConfigurator {

    private Configuration configuration;

    public LogConfigurator() {
        this.configuration = Configuration.defaultConfiguration();
    }

    public LogConfigurator(Configuration config) {
        this.configuration = Objects.requireNonNull(config);
    }

    public void setConfig(Configuration config) {
        this.configuration = config;
    }

    public void configure() {
        var root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            try { handler.flush(); } catch (Exception ignored) {}
            try { handler.close(); } catch (Exception ignored) {}
            root.removeHandler(handler);
        }
        root.setUseParentHandlers(false);
        root.setLevel(configuration.root());

        if (Objects.nonNull(configuration.console())) {
            var console = new ConsoleHandler();
            console.setLevel(configuration.console().level());
            console.setFormatter(configuration.console().formatter());

            Handler handler = configuration.async() ? new AsyncHandler(console) : console;
            root.addHandler(handler);
        }

        configuration.logLevels().forEach((pkg, level) -> {
            Logger.getLogger(pkg).setLevel(level);
        });
    }
}
