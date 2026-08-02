package cn.elvis.monaco.plugin.runtime.source;

import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDescriptor;
import cn.elvis.monaco.plugin.api.hook.AuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.AuthorizationPolicy;
import cn.elvis.monaco.plugin.api.hook.ConnectionInterceptor;
import cn.elvis.monaco.plugin.api.hook.EnhancedAuthenticationProvider;
import cn.elvis.monaco.plugin.api.hook.PluginEventListener;
import cn.elvis.monaco.plugin.api.hook.PluginHook;
import cn.elvis.monaco.plugin.api.hook.PublishInboundInterceptor;
import cn.elvis.monaco.plugin.api.hook.SubscriptionInterceptor;
import cn.elvis.monaco.plugin.api.hook.WillInterceptor;
import cn.elvis.monaco.plugin.runtime.PluginRuntimeException;
import cn.elvis.monaco.plugin.runtime.config.PluginManifest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Verifies static plugin code identity and hook declarations before start. */
final class PluginValidator {

    private static final Pattern HOOK_ID = Pattern.compile("[A-Za-z][A-Za-z0-9._-]{0,127}");

    private PluginValidator() {
    }

    static List<PluginHook> validate(
            PluginManifest manifest,
            PluginDescriptor codeDescriptor,
            List<PluginHook> hooks
    ) {
        if (!manifest.descriptor().equals(codeDescriptor)) {
            throw new PluginRuntimeException(
                    "Plugin code descriptor does not match plugin.yaml: " + manifest.id());
        }
        if (hooks == null) {
            throw new PluginRuntimeException("Plugin hooks must not be null: " + manifest.id());
        }
        List<PluginHook> immutable = List.copyOf(hooks);
        Set<String> hookIds = new HashSet<>();
        Set<PluginCapability> actualCapabilities = new HashSet<>();
        for (PluginHook hook : immutable) {
            if (hook == null || hook.hookId() == null || !HOOK_ID.matcher(hook.hookId()).matches()) {
                throw new PluginRuntimeException("Plugin contains an invalid hook id: " + manifest.id());
            }
            if (!hookIds.add(hook.hookId())) {
                throw new PluginRuntimeException("Duplicate plugin hook id: " + hook.hookId());
            }
            int knownTypes = collectCapabilities(hook, actualCapabilities);
            if (knownTypes == 0) {
                throw new PluginRuntimeException("Unknown plugin hook type: " + hook.getClass().getName());
            }
        }
        if (!actualCapabilities.equals(manifest.capabilities())) {
            throw new PluginRuntimeException(
                    "Plugin hook capabilities do not match plugin.yaml for " + manifest.id()
                            + ": expected=" + manifest.capabilities() + ", actual=" + actualCapabilities);
        }
        return immutable;
    }

    private static int collectCapabilities(PluginHook hook, Set<PluginCapability> capabilities) {
        int types = 0;
        if (hook instanceof AuthenticationProvider) {
            capabilities.add(PluginCapability.AUTHENTICATION);
            types++;
        }
        if (hook instanceof EnhancedAuthenticationProvider enhanced) {
            if (enhanced.authenticationMethod() == null || enhanced.authenticationMethod().isBlank()) {
                throw new PluginRuntimeException("Enhanced authentication method must not be blank");
            }
            capabilities.add(PluginCapability.ENHANCED_AUTHENTICATION);
            types++;
        }
        if (hook instanceof AuthorizationPolicy) {
            capabilities.add(PluginCapability.AUTHORIZATION);
            types++;
        }
        if (hook instanceof ConnectionInterceptor) {
            capabilities.add(PluginCapability.CONNECTION_INTERCEPTION);
            types++;
        }
        if (hook instanceof PublishInboundInterceptor) {
            capabilities.add(PluginCapability.PUBLISH_INTERCEPTION);
            types++;
        }
        if (hook instanceof SubscriptionInterceptor) {
            capabilities.add(PluginCapability.SUBSCRIPTION_INTERCEPTION);
            types++;
        }
        if (hook instanceof WillInterceptor) {
            capabilities.add(PluginCapability.WILL_INTERCEPTION);
            types++;
        }
        if (hook instanceof PluginEventListener) {
            capabilities.add(PluginCapability.EVENT_LISTENER);
            types++;
        }
        return types;
    }
}
