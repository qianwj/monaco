package example.plugin.fixture;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory;

public final class FixturePluginFactory implements MonacoPluginFactory {

    @Override
    public MonacoPlugin create() {
        return new FixturePlugin();
    }
}
