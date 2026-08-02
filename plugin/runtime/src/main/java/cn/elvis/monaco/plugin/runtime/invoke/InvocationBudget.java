package cn.elvis.monaco.plugin.runtime.invoke;

import java.time.Duration;

/** Monotonic deadline shared by every hook in one chain invocation. */
public final class InvocationBudget {

    private final long deadlineNanos;

    private InvocationBudget(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    public static InvocationBudget start(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Invocation budget must be positive");
        }
        long now = System.nanoTime();
        long durationNanos = duration.toNanos();
        long deadline = durationNanos > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + durationNanos;
        return new InvocationBudget(deadline);
    }

    public Duration remaining() {
        long remaining = deadlineNanos - System.nanoTime();
        return remaining <= 0 ? Duration.ZERO : Duration.ofNanos(remaining);
    }

    public Duration limit(Duration pluginTimeout) {
        Duration remaining = remaining();
        if (remaining.isZero()) {
            throw new PluginInvocationException(
                    PluginInvocationException.Kind.TIMEOUT, "Plugin chain deadline is exhausted");
        }
        return pluginTimeout.compareTo(remaining) <= 0 ? pluginTimeout : remaining;
    }
}
