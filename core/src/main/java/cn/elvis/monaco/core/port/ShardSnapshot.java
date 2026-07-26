package cn.elvis.monaco.core.port;

import cn.elvis.monaco.core.state.InflightRecord;
import cn.elvis.monaco.core.state.SessionRecord;
import cn.elvis.monaco.core.state.SubscriptionRecord;
import cn.elvis.monaco.core.state.WillRecord;

import java.util.List;

/**
 * Complete snapshot of a shard's state, loaded on demand.
 */
public record ShardSnapshot(
        SessionRecord session,
        List<SubscriptionRecord> subscriptions,
        List<InflightRecord> inflightRecords,
        WillRecord will
) {
    public static ShardSnapshot empty(String clientId) {
        return new ShardSnapshot(null, List.of(), List.of(), null);
    }
}
