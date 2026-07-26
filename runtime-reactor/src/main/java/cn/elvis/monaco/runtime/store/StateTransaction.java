package cn.elvis.monaco.runtime.store;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.state.SessionRecord;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Atomic state transaction port.
 * <p>
 * All mutations from a single command must be committed atomically
 * before any network effects are executed (persist-before-send).
 * <p>
 * Standalone: in-memory batch apply.
 * Cluster: Raft log commit or distributed transaction.
 */
public interface StateTransaction {

    /**
     * Atomically commit all state mutations produced by a transition.
     *
     * @param sessionRecord the updated session state (null if removed)
     * @param mutations     mutation actions to persist atomically
     * @return completes when all mutations are durably committed
     */
    Mono<Void> commit(SessionRecord sessionRecord, List<Action> mutations);
}
