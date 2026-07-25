package cn.elvis.monaco.core.config;

public record TransportConfig(
        boolean enabled,
        int port,
        boolean tlsEnabled,
        String tlsCertPath,
        String tlsKeyPath,
        int instances
) {}
