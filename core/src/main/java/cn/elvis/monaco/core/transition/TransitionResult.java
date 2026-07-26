package cn.elvis.monaco.core.transition;

import cn.elvis.monaco.core.command.Action;
import cn.elvis.monaco.core.state.LogicalConnection;
import cn.elvis.monaco.core.state.SessionRecord;

import java.util.List;

/**
 * Result of a state transition.
 *
 * @param sessionRecord  updated session metadata (null if session removed)
 * @param connection     logical connection state (null on disconnect)
 * @param actions        side-effect descriptions for runtime to execute
 */
public record TransitionResult(
        SessionRecord sessionRecord,
        LogicalConnection connection,
        List<Action> actions
) {
    public static TransitionResult of(SessionRecord session, LogicalConnection connection, Action... actions) {
        return new TransitionResult(session, connection, List.of(actions));
    }

    public static TransitionResult of(SessionRecord session, LogicalConnection connection, List<Action> actions) {
        return new TransitionResult(session, connection, List.copyOf(actions));
    }
}
