package cn.elvis.monaco.plugin.runtime.support;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Daemon thread factory with a plugin-restricted context ClassLoader. */
public final class PluginThreadFactory implements ThreadFactory {

    private final String prefix;
    private final ClassLoader contextClassLoader;
    private final AtomicInteger sequence = new AtomicInteger();

    public PluginThreadFactory(String prefix, ClassLoader contextClassLoader) {
        this.prefix = prefix;
        this.contextClassLoader = contextClassLoader;
    }

    @Override
    public Thread newThread(Runnable task) {
        Thread thread = new Thread(task, prefix + '-' + sequence.incrementAndGet());
        thread.setDaemon(true);
        thread.setContextClassLoader(contextClassLoader);
        return thread;
    }
}
