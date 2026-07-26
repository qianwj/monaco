package cn.elvis.monaco.plugin.runtime.event;

/** Overflow behavior for a plugin's post-commit event mailbox. */
public enum EventDropPolicy {
    DROP_LATEST,
    DROP_OLDEST
}
