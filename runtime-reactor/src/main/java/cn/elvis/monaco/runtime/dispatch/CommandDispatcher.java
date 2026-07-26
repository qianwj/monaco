package cn.elvis.monaco.runtime.dispatch;

import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import reactor.core.publisher.Mono;

/**
 * Dispatches a typed SessionCommand to the owning session's mailbox.
 * <p>
 * Local implementation routes to a shard lane with serial execution guarantee.
 * Cluster implementation may forward to a remote node via RSocket/gRPC.
 */
public interface CommandDispatcher {

    Mono<CommandResult> dispatch(SessionCommand command);

    void dispose();
}
