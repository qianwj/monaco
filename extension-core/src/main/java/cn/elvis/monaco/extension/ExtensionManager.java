package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.dsl.*;
import com.google.flatbuffers.FlatBufferBuilder;
import io.vertx.core.Future;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtensionManager {

    private final ThreadLocal<FlatBufferBuilder> builderPool = new ThreadLocal<>();

    private final Map<String, ExtensionSession> extensions = new ConcurrentHashMap<>();

    private final Map<String, Byte> extensionTypes = new HashMap<>();

    private final Map<String, Byte> requestBodyTypes = new HashMap<>();

    ExtensionManager() {
        for (int i = 0; i < ExtensionType.names.length; i++) {
            extensionTypes.put(ExtensionType.name(i), (byte) i);
            requestBodyTypes.put(RequestBody.name(i).toUpperCase(), (byte) i);
        }
    }

    void register(final ExtensionSession session) {
        session.connect().compose(metadata -> {
            boolean register = false;
            for (String extensionType : metadata.extensionTypes()) {
                if (!extensions.containsKey(extensionType)) {
                    extensions.put(extensionType, session);
                    register = true;
                }
            }
            if (!register) {
                session.close();
            }
            return null;
        });
    }

    void unregisterAll() {
        extensions.values().forEach(ExtensionSession::close);
    }

    Future<Response> request(String extensionType, ByteBuffer request) {
        if (!extensions.containsKey(extensionType)) {
            throw new ExtensionNotFoundException(extensionType);
        }
        var builder = Optional.ofNullable(builderPool.get()).orElse(new FlatBufferBuilder());
        int offset = Request.createRequest(
                builder,
                extensionTypes.get(extensionType),
                requestBodyTypes.get(extensionType),
                builder.createByteVector(request)
        );
        builder.finish(offset);
        var data = builder.dataBuffer();
        builder.clear();
        builderPool.set(builder);
        return extensions.get(extensionType).request(data);
    }
}
