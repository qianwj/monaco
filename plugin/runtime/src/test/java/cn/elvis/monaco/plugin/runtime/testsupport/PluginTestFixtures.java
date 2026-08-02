package cn.elvis.monaco.plugin.runtime.testsupport;

import cn.elvis.monaco.plugin.api.descriptor.ApiVersion;
import cn.elvis.monaco.plugin.api.descriptor.PluginCapability;
import cn.elvis.monaco.plugin.api.descriptor.PluginDependency;
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
import cn.elvis.monaco.plugin.api.lifecycle.BrokerInfo;
import cn.elvis.monaco.plugin.api.lifecycle.PluginContext;
import cn.elvis.monaco.plugin.api.support.PluginClock;
import cn.elvis.monaco.plugin.api.support.PluginConfig;
import cn.elvis.monaco.plugin.api.support.PluginLogger;
import cn.elvis.monaco.plugin.api.support.PluginMetrics;
import cn.elvis.monaco.plugin.api.support.PluginScheduledTask;
import cn.elvis.monaco.plugin.api.support.PluginScheduler;
import cn.elvis.monaco.plugin.runtime.catalog.PluginFingerprint;
import cn.elvis.monaco.plugin.runtime.catalog.PluginHandle;
import cn.elvis.monaco.plugin.runtime.config.PluginDeployment;
import cn.elvis.monaco.plugin.runtime.event.EventDropPolicy;
import cn.elvis.monaco.plugin.runtime.spi.HookInvocation;
import cn.elvis.monaco.plugin.runtime.spi.PluginInvoker;
import cn.elvis.monaco.plugin.runtime.spi.PluginLifecycle;
import cn.elvis.monaco.plugin.runtime.telemetry.PluginTelemetry;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class PluginTestFixtures {

    private PluginTestFixtures() {
    }

    public static PluginDeployment deployment(boolean required, int priority) {
        return new PluginDeployment(
                true,
                required,
                priority,
                Duration.ofMillis(250),
                Duration.ofMillis(250),
                2,
                8,
                8,
                EventDropPolicy.DROP_LATEST,
                Map.of());
    }

    public static PluginHandle handle(
            String id,
            String version,
            List<PluginDependency> dependencies,
            PluginDeployment deployment,
            List<PluginHook> hooks,
            PluginLifecycle lifecycle,
            PluginInvoker invoker,
            PluginTelemetry telemetry
    ) {
        PluginDescriptor descriptor = new PluginDescriptor(
                id,
                id,
                version,
                ApiVersion.CURRENT,
                capabilities(hooks),
                dependencies);
        return new PluginHandle(
                descriptor,
                hooks,
                lifecycle,
                invoker,
                new PluginFingerprint("a".repeat(64)),
                deployment,
                telemetry,
                null);
    }

    public static PluginHandle activeHandle(String id, List<PluginHook> hooks) {
        PluginHandle handle = handle(
                id,
                "1.0.0",
                List.of(),
                deployment(false, 100),
                hooks,
                lifecycle(Mono.empty(), Mono.empty()),
                new DirectPluginInvoker(),
                PluginTelemetry.noop());
        handle.start(null).block();
        return handle;
    }

    public static PluginLifecycle lifecycle(Mono<Void> start, Mono<Void> stop) {
        return new PluginLifecycle() {
            @Override
            public Mono<Void> start(cn.elvis.monaco.plugin.api.lifecycle.PluginContext context) {
                return start;
            }

            @Override
            public Mono<Void> stop() {
                return stop;
            }
        };
    }

    public static PluginContext context() {
        PluginScheduler scheduler = new PluginScheduler() {
            @Override
            public Mono<PluginScheduledTask> schedule(
                    String taskId, Duration delay, Supplier<? extends Mono<Void>> task) {
                return Mono.error(new UnsupportedOperationException("not used by test"));
            }

            @Override
            public Mono<PluginScheduledTask> scheduleAtFixedRate(
                    String taskId,
                    Duration initialDelay,
                    Duration period,
                    Supplier<? extends Mono<Void>> task) {
                return Mono.error(new UnsupportedOperationException("not used by test"));
            }
        };
        return new PluginContext(
                PluginConfig.empty(),
                PluginLogger.noop(),
                PluginMetrics.noop(),
                PluginClock.systemUtc(),
                scheduler,
                new BrokerInfo("test", ApiVersion.CURRENT, ""));
    }

    private static Set<PluginCapability> capabilities(List<PluginHook> hooks) {
        Set<PluginCapability> capabilities = new HashSet<>();
        hooks.forEach(hook -> {
            if (hook instanceof AuthenticationProvider) {
                capabilities.add(PluginCapability.AUTHENTICATION);
            }
            if (hook instanceof EnhancedAuthenticationProvider) {
                capabilities.add(PluginCapability.ENHANCED_AUTHENTICATION);
            }
            if (hook instanceof AuthorizationPolicy) {
                capabilities.add(PluginCapability.AUTHORIZATION);
            }
            if (hook instanceof ConnectionInterceptor) {
                capabilities.add(PluginCapability.CONNECTION_INTERCEPTION);
            }
            if (hook instanceof PublishInboundInterceptor) {
                capabilities.add(PluginCapability.PUBLISH_INTERCEPTION);
            }
            if (hook instanceof SubscriptionInterceptor) {
                capabilities.add(PluginCapability.SUBSCRIPTION_INTERCEPTION);
            }
            if (hook instanceof WillInterceptor) {
                capabilities.add(PluginCapability.WILL_INTERCEPTION);
            }
            if (hook instanceof PluginEventListener) {
                capabilities.add(PluginCapability.EVENT_LISTENER);
            }
        });
        return Set.copyOf(capabilities);
    }

    public static final class DirectPluginInvoker implements PluginInvoker {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public <R> Mono<R> invoke(HookInvocation<R> invocation) {
            return Mono.defer(() -> {
                if (closed.get()) {
                    return Mono.error(new IllegalStateException("closed"));
                }
                Mono<? extends R> action = invocation.action().get();
                return action.<R>map(value -> value);
            });
        }

        @Override
        public Mono<Void> drain(Duration timeout) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> close() {
            return Mono.fromRunnable(() -> closed.set(true));
        }

        public boolean isClosed() {
            return closed.get();
        }
    }
}
