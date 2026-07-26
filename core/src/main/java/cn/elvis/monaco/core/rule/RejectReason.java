package cn.elvis.monaco.core.rule;

import cn.elvis.monaco.protocol.reason.ReasonCode;

import java.util.Optional;

/**
 * Result of a rule validation. Empty means pass, present means reject.
 */
public record RejectReason(ReasonCode code, String message) {

    public static Optional<RejectReason> pass() {
        return Optional.empty();
    }

    public static Optional<RejectReason> reject(ReasonCode code, String message) {
        return Optional.of(new RejectReason(code, message));
    }
}
