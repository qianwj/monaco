package cn.elvis.monaco.plugin.runtime.registry;

import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.AuthorizationPolicy;
import cn.elvis.monaco.plugin.api.hook.ConnectionInterceptor;
import cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.api.hook.PublishInboundInterceptor;
import cn.elvis.monaco.plugin.api.hook.SubscriptionInterceptor;
import cn.elvis.monaco.plugin.api.hook.WillInterceptor;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.catalog.PluginState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Builds and atomically swaps immutable hook snapshots. */
public final class HookRegistry {

    private final AtomicLong generations = new AtomicLong();
    private final AtomicReference<HookSnapshot> current =
            new AtomicReference<>(HookSnapshot.empty(0));

    public HookSnapshot snapshot() {
        return current.get();
    }

    public HookSnapshot publish(List<PluginHandle> orderedHandles) {
        long generation = generations.incrementAndGet();
        List<HookBinding<AuthenticationProvider>> authentication = new ArrayList<>();
        Map<String, HookBinding<EnhancedAuthenticationProvider>> enhanced = new LinkedHashMap<>();
        List<HookBinding<AuthorizationPolicy>> authorization = new ArrayList<>();
        List<HookBinding<ConnectionInterceptor>> connection = new ArrayList<>();
        List<HookBinding<PublishInboundInterceptor>> publish = new ArrayList<>();
        List<HookBinding<SubscriptionInterceptor>> subscription = new ArrayList<>();
        List<HookBinding<WillInterceptor>> will = new ArrayList<>();
        List<HookBinding<PluginEventListener>> events = new ArrayList<>();

        orderedHandles.stream()
                .filter(handle -> handle.state() == PluginState.ACTIVE)
                .forEach(handle -> handle.hooks().forEach(hook -> {
                    if (hook instanceof AuthenticationProvider typed) {
                        authentication.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof EnhancedAuthenticationProvider typed) {
                        HookBinding<EnhancedAuthenticationProvider> duplicate = enhanced.putIfAbsent(
                                typed.authenticationMethod(), new HookBinding<>(handle, typed));
                        if (duplicate != null) {
                            throw new PluginRuntimeException(
                                    "Multiple enhanced authentication providers claim method: "
                                            + typed.authenticationMethod());
                        }
                    }
                    if (hook instanceof AuthorizationPolicy typed) {
                        authorization.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof ConnectionInterceptor typed) {
                        connection.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof PublishInboundInterceptor typed) {
                        publish.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof SubscriptionInterceptor typed) {
                        subscription.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof WillInterceptor typed) {
                        will.add(new HookBinding<>(handle, typed));
                    }
                    if (hook instanceof PluginEventListener typed) {
                        events.add(new HookBinding<>(handle, typed));
                    }
                }));

        HookSnapshot snapshot = new HookSnapshot(
                generation,
                authentication,
                enhanced,
                authorization,
                connection,
                publish,
                subscription,
                will,
                events);
        current.set(snapshot);
        return snapshot;
    }

    public HookSnapshot clear() {
        HookSnapshot empty = HookSnapshot.empty(generations.incrementAndGet());
        current.set(empty);
        return empty;
    }
}
