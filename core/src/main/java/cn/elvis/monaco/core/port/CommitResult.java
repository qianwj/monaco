package cn.elvis.monaco.core.port;

/**
 * Result of a {@link BrokerStore#commit(StoreCommit)} operation.
 */
public sealed interface CommitResult {

    record Success(long newRevision) implements CommitResult {}

    record ConflictRevision(long actualRevision) implements CommitResult {}

    record StoreError(String message, Throwable cause) implements CommitResult {}
}
