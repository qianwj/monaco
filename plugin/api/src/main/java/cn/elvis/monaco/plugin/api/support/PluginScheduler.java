package cn.elvis.monaco.plugin.api.support;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Supplier;

/** Restricted scheduler for plugin-owned background work. */
public interface PluginScheduler {

    Mono<PluginScheduledTask> schedule(
            String taskId,
            Duration delay,
            Supplier<? extends Mono<Void>> task);

    Mono<PluginScheduledTask> scheduleAtFixedRate(
            String taskId,
            Duration initialDelay,
            Duration period,
            Supplier<? extends Mono<Void>> task);
}
