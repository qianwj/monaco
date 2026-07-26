package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.core.state.SessionRecord;
import reactor.core.publisher.Mono;

public interface SessionStore {
    Mono<SessionRecord> get(String clientId);
    Mono<Void> save(SessionRecord session);
    Mono<Void> remove(String clientId);
}
