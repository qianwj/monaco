package cn.elvis.monaco.plugin.runtime.support;

import cn.elvis.monaco.plugin.api.support.PluginScheduledTask;
import cn.elvis.monaco.plugin.api.support.PluginScheduler;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Plugin-owned, bounded scheduler for background tasks created during start. */
public final class RuntimePluginScheduler implements PluginScheduler, AutoCloseable {

    private final Scheduler scheduler;
    private final Map<String, RuntimeScheduledTask> tasks = new ConcurrentHashMap<>();

    public RuntimePluginScheduler(String pluginId, ClassLoader executionClassLoader) {
        scheduler = Schedulers.newBoundedElastic(
                1,
                256,
                new PluginThreadFactory("monaco-plugin-task-" + pluginId, executionClassLoader),
                60);
    }

    @Override
    public Mono<PluginScheduledTask> schedule(
            String taskId,
            Duration delay,
            Supplier<? extends Mono<Void>> task
    ) {
        return create(taskId, delay, null, task);
    }

    @Override
    public Mono<PluginScheduledTask> scheduleAtFixedRate(
            String taskId,
            Duration initialDelay,
            Duration period,
            Supplier<? extends Mono<Void>> task
    ) {
        requirePositive(period, "Task period");
        return create(taskId, initialDelay, period, task);
    }

    @Override
    public void close() {
        tasks.values().forEach(RuntimeScheduledTask::cancelNow);
        tasks.clear();
        scheduler.dispose();
    }

    private Mono<PluginScheduledTask> create(
            String taskId,
            Duration initialDelay,
            Duration period,
            Supplier<? extends Mono<Void>> task
    ) {
        return Mono.defer(() -> {
            requireTaskId(taskId);
            requireNonNegative(initialDelay, "Task delay");
            if (task == null) {
                return Mono.error(new IllegalArgumentException("Scheduled task must not be null"));
            }
            RuntimeScheduledTask handle = new RuntimeScheduledTask(taskId);
            RuntimeScheduledTask existing = tasks.putIfAbsent(taskId, handle);
            if (existing != null) {
                return Mono.error(new IllegalArgumentException("Duplicate plugin task id: " + taskId));
            }

            Flux<Long> ticks = period == null
                    ? Mono.delay(initialDelay, scheduler).flux()
                    : Flux.interval(initialDelay, period, scheduler);
            Disposable disposable = ticks.concatMap(ignored -> Mono.defer(task))
                    .doFinally(ignored -> tasks.remove(taskId, handle))
                    .subscribe(ignored -> { }, failure -> { });
            handle.attach(disposable);
            return Mono.just((PluginScheduledTask) handle);
        });
    }

    private static void requireTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Plugin task id must not be blank");
        }
    }

    private static void requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String label) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
    }

    private final class RuntimeScheduledTask implements PluginScheduledTask {
        private final String taskId;
        private volatile Disposable disposable;

        private RuntimeScheduledTask(String taskId) {
            this.taskId = taskId;
        }

        @Override
        public String taskId() {
            return taskId;
        }

        @Override
        public Mono<Void> cancel() {
            return Mono.fromRunnable(this::cancelNow);
        }

        @Override
        public boolean isCancelled() {
            Disposable current = disposable;
            return current != null && current.isDisposed();
        }

        private void attach(Disposable disposable) {
            this.disposable = disposable;
        }

        private void cancelNow() {
            Disposable current = disposable;
            if (current != null) {
                current.dispose();
            }
            tasks.remove(taskId, this);
        }
    }
}
