package cn.elvis.monaco.runtime.dispatch;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.function.Function;

public class LocalDispatcher implements CommandDispatcher {

    private final int shardCount;
    private final Scheduler[] shards;

    public LocalDispatcher(int shardCount) {
        this.shardCount = shardCount;
        this.shards = new Scheduler[shardCount];
        for (int i = 0; i < shardCount; i++) {
            this.shards[i] = Schedulers.newSingle("shard-" + i, true);
        }
    }

    @Override
    public <R> Mono<R> dispatch(String clientId, Function<String, Mono<R>> task) {
        int shard = Math.floorMod(clientId.hashCode(), shardCount);
        return Mono.defer(() -> task.apply(clientId))
                .subscribeOn(shards[shard]);
    }

    public void dispose() {
        for (Scheduler sched : shards) {
            sched.dispose();
        }
    }
}
