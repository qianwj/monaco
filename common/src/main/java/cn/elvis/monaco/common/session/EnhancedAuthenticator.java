package cn.elvis.monaco.common.session;

import cn.elvis.monaco.common.entity.EnhancedAuthenticateResult;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

public interface EnhancedAuthenticator {

    String method();

    Future<EnhancedAuthenticateResult> authenticate(String clientId, Buffer data);
}
