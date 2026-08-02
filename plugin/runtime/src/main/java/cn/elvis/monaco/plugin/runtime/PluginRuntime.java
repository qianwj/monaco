package cn.elvis.monaco.plugin.runtime;

import cn.elvis.monaco.plugin.api.context.PluginRequestContext;
import cn.elvis.monaco.plugin.api.decision.AuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.AuthorizationDecision;
import cn.elvis.monaco.plugin.api.decision.EnhancedAuthenticationDecision;
import cn.elvis.monaco.plugin.api.decision.PolicyDecision;
import cn.elvis.monaco.plugin.api.event.PluginEvent;
import cn.elvis.monaco.plugin.api.model.AuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.AuthorizationRequest;
import cn.elvis.monaco.plugin.api.model.ConnectView;
import cn.elvis.monaco.plugin.api.model.EnhancedAuthenticationRequest;
import cn.elvis.monaco.plugin.api.model.PublishView;
import cn.elvis.monaco.plugin.api.model.SubscriptionView;
import cn.elvis.monaco.plugin.api.model.WillView;
import cn.elvis.monaco.plugin.runtime.catalog.PluginCatalog;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.chain.AuthenticationChain;
import cn.elvis.monaco.plugin.runtime.chain.AuthorizationChain;
import cn.elvis.monaco.plugin.runtime.chain.ConnectionInterceptorChain;
import cn.elvis.monaco.plugin.runtime.chain.EnhancedAuthenticationChain;
import cn.elvis.monaco.plugin.runtime.chain.PublishInterceptorChain;
import cn.elvis.monaco.plugin.runtime.chain.SubscriptionInterceptorChain;
import cn.elvis.monaco.plugin.runtime.chain.WillInterceptorChain;
import cn.elvis.monaco.plugin.runtime.config.PluginRuntimeConfig;
import cn.elvis.monaco.plugin.runtime.event.EventDispatcher;
import cn.elvis.monaco.plugin.runtime.lifecycle.LifecycleCoordinator;
import cn.elvis.monaco.plugin.runtime.lifecycle.PluginContextProvider;
import cn.elvis.monaco.plugin.runtime.registry.EnhancedAuthBindingRegistry;
import cn.elvis.monaco.plugin.runtime.registry.HookRegistry;
import cn.elvis.monaco.plugin.runtime.spi.PluginSource;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Coordinates one immutable plugin generation for a Broker process. */
public final class PluginRuntime {

    private final PluginSource source;
    private final PluginRuntimeConfig config;
    private final PluginTelemetry telemetry;
    private final PluginCatalog catalog;
    private final LifecycleCoordinator lifecycle;
    private final PluginContextProvider contexts;
    private final HookRegistry hooks;
    private final EnhancedAuthBindingRegistry enhancedBindings;
    private final EventDispatcher events;
    private final AuthenticationChain authentication;
    private final EnhancedAuthenticationChain enhancedAuthentication;
    private final AuthorizationChain authorization;
    private final ConnectionInterceptorChain connection;
    private final PublishInterceptorChain publish;
    private final SubscriptionInterceptorChain subscription;
    private final WillInterceptorChain will;
    private final AtomicReference<PluginRuntimeState> state =
            new AtomicReference<>(PluginRuntimeState.NEW);

    PluginRuntime(
            PluginSource source,
            PluginRuntimeConfig config,
            PluginTelemetry telemetry,
            PluginCatalog catalog,
            LifecycleCoordinator lifecycle,
            PluginContextProvider contexts,
            HookRegistry hooks,
            EnhancedAuthBindingRegistry enhancedBindings,
            EventDispatcher events,
            AuthenticationChain authentication,
            EnhancedAuthenticationChain enhancedAuthentication,
            AuthorizationChain authorization
    ) {
        this.source = source;
        this.config = config;
        this.telemetry = telemetry;
        this.catalog = catalog;
        this.lifecycle = lifecycle;
        this.contexts = contexts;
        this.hooks = hooks;
        this.enhancedBindings = enhancedBindings;
        this.events = events;
        this.authentication = authentication;
        this.enhancedAuthentication = enhancedAuthentication;
        this.authorization = authorization;
        this.connection = new ConnectionInterceptorChain(hooks);
        this.publish = new PublishInterceptorChain(hooks);
        this.subscription = new SubscriptionInterceptorChain(hooks);
        this.will = new WillInterceptorChain(hooks);
    }

    public Mono<Void> start() {
        return Mono.defer(() -> {
            if (!state.compareAndSet(PluginRuntimeState.NEW, PluginRuntimeState.STARTING)) {
                return Mono.error(new IllegalStateException("Plugin runtime can only be started once"));
            }
            AtomicReference<List<PluginHandle>> loaded = new AtomicReference<>(List.of());
            return source.load()
                    .map(candidate -> new PluginHandle(
                            candidate,
                            config.deploymentFor(candidate.descriptor().id()),
                            telemetry))
                    .collectList()
                    .doOnNext(loaded::set)
                    .flatMap(handles -> lifecycle.start(handles, contexts))
                    .doOnNext(ordered -> {
                        catalog.replace(ordered);
                        events.publish(hooks.publish(ordered));
                        state.set(PluginRuntimeState.ACTIVE);
                    })
                    .then()
                    .onErrorResume(failure -> {
                        state.set(PluginRuntimeState.FAILED);
                        hooks.clear();
                        enhancedBindings.clearAll();
                        return lifecycle.stop(loaded.get())
                                .then(source.close())
                                .then(Mono.error(failure));
                    });
        });
    }

    public Mono<Void> stop() {
        return Mono.defer(() -> {
            PluginRuntimeState current = state.get();
            if (current == PluginRuntimeState.STOPPED || current == PluginRuntimeState.NEW) {
                state.set(PluginRuntimeState.STOPPED);
                return source.close();
            }
            if (current == PluginRuntimeState.STOPPING) {
                return Mono.error(new IllegalStateException("Plugin runtime stop is already in progress"));
            }
            if (!state.compareAndSet(current, PluginRuntimeState.STOPPING)) {
                return Mono.error(new IllegalStateException("Plugin runtime stop is already in progress"));
            }
            hooks.clear();
            enhancedBindings.clearAll();
            List<PluginHandle> handles = catalog.handles();
            return events.close()
                    .then(lifecycle.drain(handles))
                    .then(lifecycle.stop(handles))
                    .then(source.close())
                    .doFinally(ignored -> {
                        catalog.clear();
                        state.set(PluginRuntimeState.STOPPED);
                    });
        });
    }

    public PluginRuntimeState state() {
        return state.get();
    }

    public boolean ready() {
        return state.get() == PluginRuntimeState.ACTIVE && catalog.requiredPluginsActive();
    }

    public String requiredFingerprint() {
        return catalog.requiredFingerprint();
    }

    public Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context,
            AuthenticationRequest request
    ) {
        return whenActive(() -> authentication.authenticate(context, request, config.chainTimeout()));
    }

    public Mono<EnhancedAuthenticationDecision> exchangeAuthentication(
            PluginRequestContext context,
            EnhancedAuthenticationRequest request
    ) {
        return whenActive(() -> enhancedAuthentication.exchange(context, request, config.chainTimeout()));
    }

    public Mono<AuthorizationDecision> authorize(
            PluginRequestContext context,
            AuthorizationRequest request
    ) {
        return whenActive(() -> authorization.authorize(context, request, config.chainTimeout()));
    }

    public Mono<PolicyDecision<ConnectView>> interceptConnection(
            PluginRequestContext context,
            ConnectView value
    ) {
        return whenActive(() -> connection.intercept(context, value, config.chainTimeout()));
    }

    public Mono<PolicyDecision<PublishView>> interceptPublish(
            PluginRequestContext context,
            PublishView value
    ) {
        return whenActive(() -> publish.intercept(context, value, config.chainTimeout()));
    }

    public Mono<PolicyDecision<SubscriptionView>> interceptSubscription(
            PluginRequestContext context,
            SubscriptionView value
    ) {
        return whenActive(() -> subscription.intercept(context, value, config.chainTimeout()));
    }

    public Mono<PolicyDecision<WillView>> interceptWill(
            PluginRequestContext context,
            WillView value
    ) {
        return whenActive(() -> will.intercept(context, value, config.chainTimeout()));
    }

    public void connectionClosed(cn.elvis.monaco.protocol.model.ConnectionId connectionId) {
        enhancedAuthentication.connectionClosed(connectionId);
    }

    public Mono<Void> publishEvent(PluginEvent event) {
        return whenActive(() -> events.dispatch(event));
    }

    private <T> Mono<T> whenActive(java.util.function.Supplier<Mono<T>> operation) {
        return Mono.defer(() -> state.get() == PluginRuntimeState.ACTIVE
                ? operation.get()
                : Mono.error(new IllegalStateException("Plugin runtime is not active")));
    }
}
