package cn.elvis.monaco.core.command;

import cn.elvis.monaco.core.transition.TransitionResult;

/**
 * Result of processing a {@link SessionCommand}.
 * <p>
 * Used by the dispatcher to communicate outcome back to the transport layer.
 */
public sealed interface CommandResult {

    /**
     * Command processed successfully. Contains the transition result
     * with updated state and post-commit actions.
     */
    record Success(TransitionResult result) implements CommandResult {
    }

    /**
     * Command rejected due to a domain or concurrency violation.
     * The connection should typically be closed with a reason code.
     */
    record Rejected(RejectReason reason, String message) implements CommandResult {
    }

    /**
     * Command cannot be processed on this node — redirect to the shard owner.
     * Only produced in cluster mode.
     */
    record Redirect(String targetNode) implements CommandResult {
    }

    /**
     * Rejection reasons for failed commands.
     */
    enum RejectReason {
        /** ConnectionRef generation is stale (takeover happened). */
        STALE_CONNECTION,
        /** Session not found or expired. */
        SESSION_NOT_FOUND,
        /** Store transaction failed. */
        COMMIT_FAILED,
        /** Protocol violation. */
        PROTOCOL_ERROR
    }
}
