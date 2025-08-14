package cn.elvis.monaco;

import cn.elvis.monaco.settings.EnvironmentSettings;

/**
 * MQTT Broker application entry
 *
 * @author qianwj
 * @since  0.0.1
 */
public class Application {

    public static void main(String[] args) {
        MonacoServer server = new MonacoServer(EnvironmentSettings.getInstance());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
    }
}
