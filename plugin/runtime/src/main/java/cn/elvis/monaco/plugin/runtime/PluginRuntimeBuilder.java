package cn.elvis.monaco.plugin.runtime;

import cn.elvis.monaco.plugin.api.lifecycle.BrokerInfo;
import cn.elvis.monaco.plugin.runtime.catalog.PluginCatalog;
import cn.elvis.monaco.plugin.runtime.chain.AuthenticationChain;
import cn.elvis.monaco.plugin.runtime.chain.AuthenticationFallback;
import cn.elvis.monaco.plugin.runtime.chain.AuthorizationChain;
import cn.elvis.monaco.plugin.runtime.chain.AuthorizationFallback;
import cn.elvis.monaco.plugin.runtime.chain.EnhancedAuthenticationChain;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import cn.elvis.monaco.plugin.runtime.event.EventDispatcher;
import cn.elvis.monaco.plugin.runtime.lifecycle.DefaultPluginContextProvider;
import cn.elvis.monaco.plugin.runtime.lifecycle.LifecycleCoordinator;
import cn.elvis.monaco.plugin.runtime.lifecycle.PluginContextProvider;
import cn.elvis.monaco.plugin.runtime.registry.EnhancedAuthBindingRegistry;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.source.DirectoryPluginSource;
import cn.elvis.monaco.plugin.runtime.spi.PluginSource;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;

/** Explicit assembly point for local and future remote plugin sources. */
public final class PluginRuntimeBuilder {

    private final PluginRuntimeConfig config;
    private final BrokerInfo brokerInfo;
    private final AuthenticationFallback authenticationFallback;
    private final AuthorizationFallback authorizationFallback;
    private PluginTelemetry telemetry = PluginTelemetry.noop();
    private PluginSource source;
    private PluginContextProvider contexts;

    public PluginRuntimeBuilder(
            PluginRuntimeConfig config,
            BrokerInfo brokerInfo,
            AuthenticationFallback authenticationFallback,
            AuthorizationFallback authorizationFallback
    ) {
        if (config == null || brokerInfo == null
                || authenticationFallback == null || authorizationFallback == null) {
            throw new IllegalArgumentException("Plugin runtime builder components must not be null");
        }
        this.config = config;
        this.brokerInfo = brokerInfo;
        this.authenticationFallback = authenticationFallback;
        this.authorizationFallback = authorizationFallback;
    }

    public PluginRuntimeBuilder telemetry(PluginTelemetry telemetry) {
        this.telemetry = java.util.Objects.requireNonNull(telemetry, "telemetry");
        return this;
    }

    public PluginRuntimeBuilder source(PluginSource source) {
        this.source = java.util.Objects.requireNonNull(source, "source");
        return this;
    }

    public PluginRuntimeBuilder contexts(PluginContextProvider contexts) {
        this.contexts = java.util.Objects.requireNonNull(contexts, "contexts");
        return this;
    }

    public PluginRuntime build() {
        PluginSource runtimeSource = source == null
                ? new DirectoryPluginSource(config, telemetry)
                : source;
        PluginContextProvider runtimeContexts = contexts == null
                ? new DefaultPluginContextProvider(brokerInfo)
                : contexts;
        HookRegistry hooks = new HookRegistry();
        EnhancedAuthBindingRegistry bindings = new EnhancedAuthBindingRegistry();
        EventDispatcher events = new EventDispatcher(telemetry);
        AuthenticationChain authentication = new AuthenticationChain(hooks, authenticationFallback);
        EnhancedAuthenticationChain enhanced = new EnhancedAuthenticationChain(hooks, bindings);
        AuthorizationChain authorization = new AuthorizationChain(hooks, authorizationFallback);
        return new PluginRuntime(
                runtimeSource,
                config,
                telemetry,
                new PluginCatalog(),
                new LifecycleCoordinator(config),
                runtimeContexts,
                hooks,
                bindings,
                events,
                authentication,
                enhanced,
                authorization);
    }
}
