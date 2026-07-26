package cn.elvis.monaco.core.port;

import reactor.core.publisher.Mono;

/**
 * Unified store port for shard-local atomic operations.
 * <p>
 * All state mutations within a single command are committed atomically
 * through {@link #commit(StoreCommit)}. Implementations guarantee:
 * <ul>
 *   <li>Memory: lock + batch apply</li>
 *   <li>RocksDB: WriteBatch</li>
 *   <li>PostgreSQL: database transaction</li>
 *   <li>Replicated: Raft majority commit before completing Mono</li>
 * </ul>
 */
public interface BrokerStore {

    /**
     * Load the full state snapshot for a client session shard.
     */
    Mono<ShardSnapshot> load(String clientId);

    /**
     * Atomically commit all mutations in a single command.
     * Fails with {@link CommitResult.ConflictRevision} if expectedRevision
     * does not match the current stored revision.
     */
    Mono<CommitResult> commit(StoreCommit commit);
}
