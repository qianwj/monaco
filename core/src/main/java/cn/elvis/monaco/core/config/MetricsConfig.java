package cn.elvis.monaco.core.config;

public record MetricsConfig(
        boolean enabled,
        int port,
        String endpoint,
        boolean exportJvmMetrics
) {}
