package cn.elvis.monaco.core.store;

import cn.elvis.monaco.core.state.WillRecord;
import reactor.core.publisher.Mono;

public interface WillStore {
    Mono<Void> save(WillRecord will);
    Mono<WillRecord> get(String clientId);
    Mono<Void> remove(String clientId);
}
