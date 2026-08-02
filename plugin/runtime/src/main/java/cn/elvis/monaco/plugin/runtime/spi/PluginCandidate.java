package cn.elvis.monaco.plugin.runtime.spi;

import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import reactor.core.publisher.Mono;

import java.util.List;

/** Validated source result before Broker deployment policy is attached. */
public record PluginCandidate(
        PluginDescriptor descriptor,
        List<PluginHook> hooks,
        PluginLifecycle lifecycle,
        PluginInvoker invoker,
        String fingerprint,
        ClassLoader executionClassLoader,
        PluginResource resource
) {

    public PluginCandidate {
        if (descriptor == null || lifecycle == null || invoker == null
                || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Plugin candidate components must be complete");
        }
        hooks = hooks == null ? List.of() : List.copyOf(hooks);
        executionClassLoader = executionClassLoader == null
                ? PluginCandidate.class.getClassLoader()
                : executionClassLoader;
        resource = resource == null ? PluginResource.none() : resource;
    }

    public Mono<Void> close() {
        return invoker.close().onErrorResume(ignored -> Mono.empty())
                .then(resource.close().onErrorResume(ignored -> Mono.empty()));
    }
}
