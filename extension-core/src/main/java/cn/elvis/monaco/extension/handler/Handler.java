package cn.elvis.monaco.extension.handler;

import cn.elvis.monaco.extension.dsl.Request;
import com.google.flatbuffers.FlatBufferBuilder;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;

import java.nio.ByteBuffer;
import java.util.function.Function;

abstract class Handler implements Function<Request, Future<ByteBuffer>> {

    final ThreadLocal<FlatBufferBuilder> builderPool = new ThreadLocal<>();

    final EventBus eventBus;

    Handler(EventBus eventBus) {
        this.eventBus = eventBus;
    }
}
