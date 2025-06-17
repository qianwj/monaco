package cn.elvis.monaco.logging;

import cn.elvis.monaco.configuration.LoggingConfiguration;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class LoggerInitializer {

    public static void init(JsonObject appConfig) {
        var config = appConfig.getJsonObject("logging").mapTo(LoggingConfiguration.class);
        ConfigurationBuilder<BuiltConfiguration> configBuilder =
                ConfigurationBuilderFactory.newConfigurationBuilder();
        Configuration configuration = configBuilder
                .add(
                        configBuilder
                                .newAppender("CONSOLE", "Console")
                                .add(
                                        configBuilder.newLayout("PatternLayout")
                                                .addAttribute("pattern", "%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n")
                                )
                )
                .add(
                        configBuilder
                                .newRootLogger(config.getRootLevel())
                                .add(configBuilder.newAppenderRef("CONSOLE")))
                .build(false);
        Configurator.reconfigure(configuration);
    }
}
