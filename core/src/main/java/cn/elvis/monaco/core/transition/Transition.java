package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Command;
import cn.elvis.monaco.core.config.BrokerConfig;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;

/**
 * Pure function: (Command, SessionRecord, LogicalConnection, Config) → TransitionResult.
 * No side effects, no I/O.
 *
 * @param <C> the command type this transition handles
 */
@FunctionalInterface
public interface Transition<C extends Command> {

    /**
     * @param command    the domain command
     * @param session    current session record (nullable for CONNECT when new)
     * @param connection current logical connection (nullable for CONNECT)
     * @param config     broker configuration
     */
    TransitionResult apply(C command, SessionRecord session, LogicalConnection connection, BrokerConfig config);
}
