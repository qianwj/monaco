package cn.elvis.monaco.testkit.probe;

import cn.elvis.monaco.core.port.CommitResult;
import cn.elvis.monaco.core.port.ShardSnapshot;
import cn.elvis.monaco.core.port.StoreCommit;

import java.util.Objects;

public sealed interface StoreTraceEvent extends TraceEvent {

    record Load(String clientId) implements StoreTraceEvent {

        public Load {
            Objects.requireNonNull(clientId, "clientId");
        }
    }

    record Loaded(String clientId, ShardSnapshot snapshot) implements StoreTraceEvent {

        public Loaded {
            Objects.requireNonNull(clientId, "clientId");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    record Commit(StoreCommit commit) implements StoreTraceEvent {

        public Commit {
            Objects.requireNonNull(commit, "commit");
        }
    }

    record Committed(StoreCommit commit, CommitResult result) implements StoreTraceEvent {

        public Committed {
            Objects.requireNonNull(commit, "commit");
            Objects.requireNonNull(result, "result");
        }
    }
}
