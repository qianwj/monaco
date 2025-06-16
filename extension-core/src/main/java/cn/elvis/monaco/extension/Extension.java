package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.dsl.Response;
import com.google.flatbuffers.FlatBufferBuilder;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.function.Function;

public sealed abstract class Extension
        permits Authenticator {

    public static final String TYPE_AUTHENTICATE = "AUTHENTICATE";

    public static final String TYPE_ENHANCED_AUTHENTICATE = "ENHANCED_AUTHENTICATE";

    protected static final ThreadLocal<FlatBufferBuilder> BUILDER_POOL = new ThreadLocal<>();

    protected <R> Future<R> useRequestBuilder(Function<FlatBufferBuilder, Future<R>> acceptor) {
        var builder = Optional.ofNullable(BUILDER_POOL.get()).orElse(new FlatBufferBuilder());
        try {
            return acceptor.apply(builder);
        } catch (Exception e) {
            return Future.failedFuture(e);
        } finally {
            builder.clear();
            BUILDER_POOL.set(builder);
        }
    }
}
