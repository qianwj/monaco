package cn.elvis.monaco.plugin.api.support;

import reactor.core.publisher.Mono;

/** Handle for a task owned and bounded by the plugin runtime. */
public interface PluginScheduledTask {

    String taskId();

    Mono<Void> cancel();

    boolean isCancelled();
}
