# Monaco MQTT 5.0 客户端状态设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置文档：[单节点架构](mqtt5-architecture.md)、[集群架构](mqtt5-cluster-architecture.md)、[集群通信协议](mqtt5-cluster-protocol.md)

## 1. 目标与约束

客户端状态必须支持 MQTT 5 会话恢复、QoS 1/2、连接接管、离线投递、Will Delay 和集群 Owner 迁移。设计遵循以下约束：

1. 网络连接与 MQTT Session 生命周期分离，持久化对象不得引用 Channel、EventLoop、ByteBuf 或 RSocket Payload。
2. 同一 `clientId` 只有当前 Session Shard Owner 可以写，命令按序执行。
3. Store/Raft 提交结果是权威状态；内存对象、timer 和订阅索引都可以重建。
4. QoS、订阅、Will 和离线队列使用独立有界记录，不嵌入一个不断增长的 Session 对象。
5. 影响协议响应的状态遵循 persist-before-acknowledge。

## 2. 状态分层与所有权

| 状态 | Owner | 生命周期 | 内容 |
| --- | --- | --- | --- |
| `PhysicalConnectionState` | Ingress | TCP/TLS/WS 连接 | Channel、codec、remote/TLS 信息、last-read、Keep Alive timer、写队列 |
| `LogicalConnectionState` | Session Owner | MQTT 网络连接 | `ConnectionRef`、认证阶段、协商限制、Topic Alias、Receive Maximum 窗口、action sequence |
| `SessionRecord` | Session Shard | MQTT Session | clientId、revision、connection generation、活动绑定、expiry interval、disconnectedAt、expiryAt |
| 业务记录 | Session Shard | 按协议状态 | Subscription、Inflight、Delivery、Will、Message、幂等结果 |
| 派生状态 | 当前 Owner | 可重建 | 活动会话缓存、Topic Filter 索引、deadline/timer 索引、指标 |

Ingress 独占真实网络连接；Owner 只通过本地或远程 `ConnectionSink` 发送逻辑动作。Keep Alive 的网络计时由 Ingress 执行，超时后向 Owner 提交带 generation 的 Detach。认证结果、Topic Alias 和连接协商值不跨重连恢复。

## 3. 持久化记录

建议的逻辑键如下，具体 Store 可以映射为 PostgreSQL 表、RocksDB Column Family 或内存 Map：

```text
session/{clientId}
subscription/{clientId}/{topicFilter}
inflight/{clientId}/{direction}/{packetId}
delivery/{clientId}/{deliveryId}
will/{clientId}
message/{messageId}
command-result/{connectionId}/{generation}/{commandSequence}
```

`SessionRecord` 只保存有界元数据：

```text
clientId, revision, connectionGeneration,
activeBinding(connectionId, ingressNode/incarnation, ownerEpochAtBind)?,
sessionExpiryIntervalSeconds, disconnectedAt?, expiryAt?
```

`0xFFFFFFFF` 的 Session Expiry 表示无到期时间，此时 `expiryAt` 缺失。`expiryAt` 从实际断开时间计算，不能使用 Session 创建时间。Clean Start、Session 到期或删除后仍保留一个有界时间的 generation/幂等 tombstone，防止重试窗口内的旧命令重新生效。

独立记录至少包含：

- `SubscriptionRecord`：filter、QoS、No Local、RAP、Retain Handling、Subscription Identifier 和 revision。
- `InflightRecord`：direction、packetId、messageId、QoS 状态、DUP、发送次数和更新时间。
- `DeliveryRecord`：deliveryId、messageId 或独立 Payload、最终 QoS、订阅标识、排队/发送状态和 expiryAt。
- `WillRecord`：Will 内容、connection generation、publishAt、状态和稳定 publish command ID。
- `CommandResultRecord`：稳定命令 ID、结果和过期时间，用于 Attach/重试幂等。

## 4. 状态转换

### 4.1 CONNECT 与接管

1. Ingress 创建唯一 `connectionId`；空 Client ID 先分配稳定 Assigned Client ID。
2. Session Owner 完成协议校验、认证和插件 Decision Hook。
3. Shard 事务读取 Session，校验 revision/epoch，并递增 connection generation。
4. `Clean Start=true` 先按 MQTT 状态机决定旧 Will 是取消还是转成持久 publication，再原子清除旧 Subscription、Inflight 和 Delivery；`false` 恢复未过期 Session。
5. 事务写入新 active binding、expiry interval、Will 和幂等结果。
6. 提交后更新热缓存，再向旧连接发送 Session Taken Over，向新连接发送 CONNACK。
7. 只有 `Clean Start=false` 且存在未过期旧 Session 时，CONNACK 的 Session Present 才为 1。

旧连接后续发送的 command/detach 因 `connectionId + generation` 不匹配返回 `STALE_GENERATION`，不能清理新绑定。

### 4.2 在线命令与 QoS

Connection Mailbox 保证物理报文顺序，Shard lane 保证同一 Session 的状态转换顺序。每个命令在事务中同时校验 shard epoch、session revision、connection generation 和 command sequence。

- 入站 QoS 2 在返回 PUBREC 前持久化 `RECEIVED_QOS2`，PUBREL 只生成一次路由副作用。
- 出站 QoS 1/2 先创建 Delivery/Inflight 并分配 Packet ID，再发送 PUBLISH 或 PUBREL。
- ACK 提交后释放 Inflight 和 Packet ID，再按 Receive Maximum 继续 drain。
- 连接中断时保留未完成 Inflight；重连后 PUBLISH 使用 DUP=1，PUBREL 按原状态重发。

Packet ID 只标识某客户端会话方向内的 Inflight，不能作为全局 messageId 或 deliveryId。

### 4.3 SUBSCRIBE 与路由索引

普通订阅记录位于客户端 Session Shard，Owner 在提交后更新不可变 Topic Filter 索引，再返回成功 SUBACK。索引更新失败时不得用旧索引继续确认成功；应丢弃缓存并从持久记录重建。

共享订阅还需要通过持久 Outbox 更新 Shared Group Shard 的成员记录。成功 SUBACK 必须等待该成员变更幂等提交，不能把最终一致但尚未生效的共享订阅报告为成功。

### 4.4 断线、Will 与过期

Owner 仅在 Detach 的完整 `ConnectionRef` 与 active binding 相同时解除绑定：

- 正常 DISCONNECT 清除 Will，并应用报文中新的 Session Expiry Interval。
- Disconnect with Will Message 立即提交 Will publication。
- 网络错误、Keep Alive 超时或协议错误将 `publishAt` 设置为 Will Delay 与 Session Expiry 的较早时间。
- Session Expiry 为 0 时原子删除会话业务记录；如果 Will 应当发布，必须先在同一 Shard 事务中创建稳定 Message/Outbox，再删除 Will 和 Session。其他情况写入 `disconnectedAt` 和绝对 `expiryAt`。

Timer 只负责提交 `ExpireSession/PublishWill` 命令，命令必须携带 expected generation、revision 和 deadline，并在事务内再次校验。重启时从持久 deadline 重建 timer；过期扫描和 timer 队列必须分页且有上限。

## 5. 内存与并发模型

每个 Owner 只缓存活动或近期访问的 `SessionRuntime`，不能在启动时把所有离线 Session、Delivery 和 Payload 装入堆。缓存按 Shard 和 clientId 有界，cache miss 从 Store 加载；只有提交成功后才能发布新快照，提交失败则丢弃计算结果。

同一 Data Shard 的命令进入固定单线程 lane，不为每个客户端创建线程，也不使用 `parallel()` 修改状态。缓存淘汰不得删除持久状态；有未完成命令、连接级 Topic Alias 或本地 action 的活动连接不可淘汰。订阅索引、deadline 索引和 Receive Maximum 计数均为持久记录的派生视图。

## 6. 单机与集群落地

- `standalone-memory`：用相同事务端口和状态转换，进程退出后不保证恢复。
- `standalone-rocksdb`：WriteBatch 原子更新同一 Session 的记录和幂等结果。
- `cluster-shared-store`：PostgreSQL 事务同时校验 partition mapping generation 与 shard epoch。
- `cluster-replicated`：Leader 将确定性 MutationBatch 写入 Ratis，多数派 commit 并在本地 RocksDB apply 后才完成命令。

Owner 切换时，新 Owner 从 Store/Raft 恢复。绑定在旧 owner epoch 的活动连接视为 orphaned；在新 epoch 下以确定的断线时间转换为离线状态并调度 Will/Expiry。Ingress 在 Session lane 失效后关闭实际 MQTT 连接，第一版不透明迁移 TCP Channel。

## 7. 当前模型迁移

当前 `core.state.Session` 是过渡模型，实施时必须拆分：

1. 将 receive maximum、maximum packet size、topic alias maximum 和 keep alive 移入连接态。
2. 将 Subscription、Inflight、Delivery 和 Will 改为独立 Store 记录。
3. 用 `disconnectedAt/expiryAt` 替代 `createdAt + sessionExpiryInterval` 的过期判断。
4. 引入 session revision、active binding、connection generation 和稳定命令幂等结果。
5. Store 契约测试同时覆盖 memory、RocksDB、PostgreSQL 和 replicated state machine。

## 8. 验收与故障测试

至少覆盖 Clean Start/Session Present 组合、DISCONNECT 修改 expiry、无限 expiry、连接接管与迟到 Detach、QoS 1/2 重连恢复、Will Delay 竞争、共享订阅成员提交、缓存淘汰后重载、Owner 切换和 timer 重建。

在 Store/Raft 提交前后、缓存更新前后和网络响应前后注入故障。任何结果都必须满足：已确认状态可恢复，未确认命令可用相同 ID 重试，旧 generation/epoch 不产生副作用，缓存丢失不改变权威状态。
