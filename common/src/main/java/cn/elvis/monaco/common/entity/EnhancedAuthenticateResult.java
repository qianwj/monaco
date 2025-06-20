package cn.elvis.monaco.common.entity;

import io.vertx.core.buffer.Buffer;

public record EnhancedAuthenticateResult(
        EnhancedAuthenticateState state,
        String message,
        String method,
        Buffer data
) {}
