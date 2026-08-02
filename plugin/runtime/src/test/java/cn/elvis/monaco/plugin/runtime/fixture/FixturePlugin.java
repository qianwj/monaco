package example.plugin.fixture;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

final class FixturePlugin implements MonacoPlugin {

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(
                "fixture",
                "Fixture Plugin",
                "1.0.0",
                ApiVersion.CURRENT,
                Set.of(),
                List.of());
    }

    @Override
    public List<PluginHook> hooks() {
        return List.of();
    }

    @Override
    public Mono<Void> start(PluginContext context) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> stop() {
        return Mono.empty();
    }
}
