package cn.elvis.monaco.plugin.runtime.registry;

import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.AuthorizationPolicy;
import cn.elvis.monaco.plugin.api.hook.ConnectionInterceptor;
import cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.api.hook.PublishInboundInterceptor;
import cn.elvis.monaco.plugin.api.hook.SubscriptionInterceptor;
import cn.elvis.monaco.plugin.api.hook.WillInterceptor;

import java.util.List;
import java.util.Map;

/** Immutable hook ordering published atomically after lifecycle start. */
public record HookSnapshot(
        long generation,
        List<HookBinding<AuthenticationProvider>> authentication,
        Map<String, HookBinding<EnhancedAuthenticationProvider>> enhancedAuthentication,
        List<HookBinding<AuthorizationPolicy>> authorization,
        List<HookBinding<ConnectionInterceptor>> connection,
        List<HookBinding<PublishInboundInterceptor>> publishInbound,
        List<HookBinding<SubscriptionInterceptor>> subscription,
        List<HookBinding<WillInterceptor>> will,
        List<HookBinding<PluginEventListener>> eventListeners
) {

    public HookSnapshot {
        authentication = List.copyOf(authentication);
        enhancedAuthentication = Map.copyOf(enhancedAuthentication);
        authorization = List.copyOf(authorization);
        connection = List.copyOf(connection);
        publishInbound = List.copyOf(publishInbound);
        subscription = List.copyOf(subscription);
        will = List.copyOf(will);
        eventListeners = List.copyOf(eventListeners);
    }

    public static HookSnapshot empty(long generation) {
        return new HookSnapshot(
                generation,
                List.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
