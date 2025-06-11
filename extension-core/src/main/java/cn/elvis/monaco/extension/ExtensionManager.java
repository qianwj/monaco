package cn.elvis.monaco.extension;

import cn.elvis.monaco.extension.dsl.ExtensionType;
import cn.elvis.monaco.extension.dsl.Request;
import cn.elvis.monaco.extension.dsl.RequestType;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.Table;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtensionManager {

    private final ThreadLocal<FlatBufferBuilder> builderPool = new ThreadLocal<>();

    private final Map<String, ExtensionSession> extensions = new ConcurrentHashMap<>();

    private final Map<String, Byte> extensionTypes = new HashMap<>();

    ExtensionManager() {
        for (int i = 0; i < ExtensionType.names.length; i++) {
            extensionTypes.put(ExtensionType.name(i), (byte) i);
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

    private <Req extends Table> void request(String extensionType, Req request) {
        if (!extensions.containsKey(extensionType)) {
            throw new ExtensionNotFoundException(extensionType);
        }
//        var builder = Optional.ofNullable(builderPool.get()).orElse(new FlatBufferBuilder());
//        Request message = Request.createRequest(
//                builder,
//        );
//        extensions.get(extensionType).request()
    }
}
