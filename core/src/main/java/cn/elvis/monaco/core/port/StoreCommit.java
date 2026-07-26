package cn.elvis.monaco.core.port;

/**
 * Atomic commit request for a single shard.
 *
 * @param clientId          shard key (clientId-based partitioning)
 * @param expectedRevision  optimistic concurrency: must match current session revision
 * @param mutations         ordered mutations to apply atomically
 */
public record StoreCommit(
        String clientId,
        long expectedRevision,
        MutationBatch mutations
) {
    public static StoreCommit of(String clientId, long expectedRevision, MutationBatch mutations) {
        return new StoreCommit(clientId, expectedRevision, mutations);
    }
}
