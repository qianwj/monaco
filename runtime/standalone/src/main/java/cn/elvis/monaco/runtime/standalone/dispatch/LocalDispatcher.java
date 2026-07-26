package cn.elvis.monaco.runtime.standalone.dispatch;

import cn.elvis.monaco.core.command.CommandResult;
import cn.elvis.monaco.core.command.SessionCommand;
import cn.elvis.monaco.runtime.dispatch.CommandDispatcher;
import cn.elvis.monaco.runtime.dispatch.ShardMailbox;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Local dispatcher that routes SessionCommands to sharded mailboxes.
 * <p>
 * Each shard is a serial execution lane backed by a bounded mailbox.
 * Commands for the same clientId always land on the same shard,
 * guaranteeing session-level serial execution via concatMap drain.
 */
public class LocalDispatcher implements CommandDispatcher {

    private static final int DEFAULT_SHARD_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_MAILBOX_CAPACITY = 256;

    private final int shardCount;
    private final ShardMailbox[] shards;
    private final Function<SessionCommand, Mono<CommandResult>> handler;

    public LocalDispatcher(Function<SessionCommand, Mono<CommandResult>> handler) {
        this(DEFAULT_SHARD_COUNT, DEFAULT_MAILBOX_CAPACITY, handler);
    }

    public LocalDispatcher(int shardCount, int mailboxCapacity,
                           Function<SessionCommand, Mono<CommandResult>> handler) {
        this.shardCount = shardCount;
        this.handler = handler;
        this.shards = new ShardMailbox[shardCount];
        for (int i = 0; i < shardCount; i++) {
            this.shards[i] = new ShardMailbox(mailboxCapacity);
        }
    }

    @Override
    public Mono<CommandResult> dispatch(SessionCommand command) {
        int shard = Math.floorMod(command.clientId().hashCode(), shardCount);
        return shards[shard].submit(command, handler);
    }

    @Override
    public void dispose() {
        for (ShardMailbox mailbox : shards) {
            mailbox.dispose();
        }
    }
}
