package cn.elvis.monaco.plugin.example;

import cn.elvis.monaco.plugin.api.lifecycle.MonacoPlugin;
import cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory;

import java.util.concurrent.atomic.AtomicInteger;

public final class ExamplePluginFactory implements MonacoPluginFactory {

    private static final AtomicInteger START_COUNT = new AtomicInteger();
    private static final AtomicInteger STOP_COUNT = new AtomicInteger();

    @Override
    public MonacoPlugin create() {
        return new ExamplePlugin(START_COUNT, STOP_COUNT);
    }

    public static void reset() {
        START_COUNT.set(0);
        STOP_COUNT.set(0);
    }

    public static int startCount() {
        return START_COUNT.get();
    }

    public static int stopCount() {
        return STOP_COUNT.get();
    }
}
