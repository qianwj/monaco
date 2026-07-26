# Monaco MQTT 5.0 集群模块详细设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置文档：[集群架构设计](mqtt5-cluster-architecture.md)、[集群技术设计](mqtt5-cluster-design.md)、[集群通信协议](mqtt5-cluster-protocol.md)

## 1. 模块化原则

Gradle 模块用于约束代码所有权和依赖方向，不代表每个模块是独立进程。Monaco 仍以 `broker` 作为唯一应用入口和 Composition Root。

1. 领域状态转换、Reactor 运行时和持久化分别归属不同模块；唯一客户端传输作为运行时内部基础设施隔离在专用包中。
2. `cluster-runtime` 定义端口，RSocket、PostgreSQL、Ratis 和 RocksDB 只能作为外层适配器依赖它。
3. wire message 与内部 command 分离；Protobuf 生成类不能进入 `core` 或 `runtime-reactor` API。
4. shared-store 与 replicated-store 模块可以同时编译，但一个 Broker 进程只装配一种 Profile。
5. optional adapter 不得通过 `api` 依赖污染公共 classpath。

## 2. 目标模块清单

### 2.1 基础运行时模块

| 模块 | 职责 | 主要依赖 |
| --- | --- | --- |
| `protocol` | MQTT 5 不可变报文和值对象 | Java 标准库 |
| `core` | command、state、transition、action、纯规则 | `protocol` |
| `runtime-reactor` | Broker 用例、Connection Processor、Shard lane、端口，以及 Reactor Netty MQTT TCP/TLS/WS、codec、Channel registry | `core`、Reactor Core；Reactor Netty/Netty MQTT 为内部实现依赖 |
| `store-memory` | 单机测试与开发状态存储 | `runtime-reactor` |
| `store-rocksdb` | 单机持久状态、恢复和 schema | `runtime-reactor`、RocksDB |
| `security-default` | 默认认证与 ACL 适配器 | `runtime-reactor` |
| `plugin-api` | 稳定插件 Hook、Decision 与 Event DTO | `protocol`、Reactor Core |
| `plugin-runtime` | 插件装载、隔离、Hook chain 与运行时端口适配 | `runtime-reactor`、`plugin-api` |
| `plugin-remote-grpc` | 独立进程插件适配器 | `plugin-runtime`、gRPC |
| `observability-micrometer` | 指标、健康检查和 trace context 适配 | `runtime-reactor`、Micrometer |
| `testkit` | 单机契约、场景 DSL 和客户端夹具 | `protocol`、`runtime-reactor` |
| `broker` | 配置、Profile 装配、生命周期、发行包 | 所选运行时与适配器 |

`core` 是正式的领域模块名；异步用例编排位于 `runtime-reactor`。项目只支持 Reactor Netty 客户端接入，不提供可替换的客户端 Transport SPI，因此 `transport-reactor` 合并到 `runtime-reactor` 的 `transport.netty` 包并删除原 Gradle 模块。该合并不影响 `cluster-transport-rsocket`：后者负责 Broker peer 通信，协议、生命周期和故障域均不同，继续作为独立适配器。

### 2.2 集群模块

| 模块 | 职责 | 主要依赖 | 禁止包含 |
| --- | --- | --- | --- |
| `cluster-protocol` | peer/Raft Protobuf schema、版本化 envelope、codec | `protocol`、Protobuf | placement、I/O 编排 |
| `cluster-runtime` | ClusterView、分片定位、派发、fanout、Outbox、drain | `runtime-reactor`、Reactor Core | RSocket、SQL、Ratis、RocksDB |
| `cluster-transport-rsocket` | peer 连接、request-channel、Lease、Resume、mTLS | `cluster-runtime`、`cluster-protocol`、RSocket | Session 状态、Store |
| `cluster-coordination-postgres` | lease、assignment、epoch、coordinator election | `cluster-runtime`、PostgreSQL client | MQTT 状态机 |
| `store-postgres` | shared-store 事务、fencing、Outbox、恢复 | `runtime-reactor`、`cluster-runtime`、PostgreSQL client | peer transport |
| `cluster-consensus-ratis` | Metadata/Data Raft Group、复制日志、成员变更、snapshot install | `cluster-runtime`、`cluster-protocol`、Ratis | RocksDB schema、MQTT codec |
| `store-rocksdb-sharded` | shard-local 状态、WriteBatch、checkpoint、snapshot | `cluster-runtime`、`core`、RocksDB | Raft 选举、网络 |
| `cluster-testkit` | 集群契约、故障注入、测试节点与断言 | `cluster-runtime`、`cluster-protocol`、`testkit` | 生产启动入口 |

集群只有 `cluster-transport-rsocket` 一个 PeerTransport 实现，不设计 `cluster-transport-grpc`。基础模块中的 `plugin-remote-grpc` 只服务插件进程隔离，不得被 cluster-runtime 或 RSocket adapter 依赖。`store-postgres` 与 `cluster-coordination-postgres` 分开，使 Store 事务和成员控制能够独立替换与测试。

## 3. 依赖方向

箭头表示左侧依赖右侧：

```text
core -------------------------------> protocol
runtime-reactor --------------------> core
store-memory -----------------------> runtime-reactor
store-rocksdb ----------------------> runtime-reactor
security-default -------------------> runtime-reactor
plugin-runtime ---------------------> runtime-reactor + plugin-api
observability-micrometer -----------> runtime-reactor

cluster-protocol -------------------> protocol
cluster-runtime --------------------> runtime-reactor + core
cluster-transport-rsocket ----------> cluster-runtime + cluster-protocol
cluster-coordination-postgres ------> cluster-runtime
store-postgres ---------------------> runtime-reactor + cluster-runtime
cluster-consensus-ratis ------------> cluster-runtime + cluster-protocol
store-rocksdb-sharded --------------> cluster-runtime + core

broker -----------------------------> runtime-reactor + cluster-runtime
                                      + selected adapters
cluster-testkit --------------------> cluster-runtime + cluster-protocol + testkit
```

禁止出现以下反向依赖：

- `core -> runtime-reactor/cluster-runtime`
- `cluster-runtime -> cluster-transport-*`
- `cluster-runtime -> PostgreSQL/Ratis/RocksDB`
- `store-* -> transport-*`
- `cluster-protocol -> cluster-runtime`
- 任何库模块依赖 `broker`

## 4. 目录与构建

目标根目录：

```text
protocol/
core/
runtime-reactor/
store-memory/
store-rocksdb/
security-default/
cluster-protocol/
cluster-runtime/
cluster-transport-rsocket/
cluster-coordination-postgres/
store-postgres/
cluster-consensus-ratis/
store-rocksdb-sharded/
plugin-api/
plugin-runtime/
plugin-remote-grpc/
observability-micrometer/
broker/
testkit/
cluster-testkit/
```

集群模块在对应阶段开始时才加入 `settings.gradle.kts`。依赖版本集中在 `gradle/libs.versions.toml`，至少需要 RSocket Core/Netty transport、Protobuf Gradle plugin、Ratis server/client、PostgreSQL reactive driver、RocksDB JNI、Reactor Test 和 Testcontainers。引入前必须验证 Java 25、Netty 4.2 与 Reactor BOM 的兼容矩阵。

构建约束：

- 所有模块使用 `java-library`，只有 `broker` 使用 `application`。
- `cluster-protocol` 使用 Protobuf plugin；生成目录不手工修改。
- `cluster-testkit` 使用 `java-test-fixtures`，不进入生产 `runtimeClasspath`。
- RSocket/Ratis/数据库依赖一律使用 `implementation`；gRPC 依赖只能存在于 `plugin-remote-grpc`。
- Reactor Netty 与 Netty MQTT codec 是 `runtime-reactor` 的 `implementation` 依赖；Netty 类型只能出现在 `cn.elvis.monaco.runtime.transport.netty`，不得进入公共 API 或其他 runtime 包。
- CI 增加依赖边界检查，并分别构建 shared-store 与 replicated-store runtimeClasspath。

## 5. `cluster-protocol`

基础包：`cn.elvis.monaco.cluster.protocol`；生成类型位于 `cn.elvis.monaco.cluster.wire.v1`。

Schema 按用途拆分，字段和兼容规则以 [集群通信协议](mqtt5-cluster-protocol.md) 为准：

| 文件 | 消息 |
| --- | --- |
| `common.proto` | version、identity、header、partition、status |
| `handshake.proto` | Setup、Handshake、Limits、Capabilities |
| `mqtt_packet.proto` | ClientPacket、ServerPacket 和 MQTT properties |
| `session.proto` | Open/Close、ClientCommand、ServerAction、sequence |
| `route.proto` | PublicationBatch、RouteTask、RouteAck、sequence |
| `control.proto` | Drain、AssignmentHint、Health、diagnostics |
| `raft.proto` | deterministic mutation batch、snapshot manifest |
| `error.proto` | channel/fatal error |

所有 envelope 必须携带 `clusterId`、source node/incarnation、protocol version、requestId；分片请求额外携带 `partitionType + partitionId + dataShardId + mappingGeneration + epoch`。未知字段按 Protobuf 兼容规则保留，未知必需 capability 在 handshake 阶段拒绝。

`ClusterWireCodec` 只负责 envelope framing、schema version 和 `protocol` 值对象编码，不依赖 `core` 或 `cluster-runtime`。domain/wire 映射分别由 RSocket adapter 的 `PeerMessageMapper` 和 Ratis adapter 的 `RatisCommandMapper` 完成。生成类与 RSocket `Payload`、Ratis `Message` 都停留在适配器边界，不能作为 `cluster-runtime` 方法参数。

## 6. `cluster-runtime`

基础包：`cn.elvis.monaco.cluster.runtime`。

### 6.1 包结构

```text
model/          NodeIdentity、ClusterView、Assignment、ShardId、Epoch
port/in/        ClusterCommandHandler、PeerRequestHandler
port/out/       PeerTransport、ClusterCoordinator、ClusterOutboxStore、ReplicatedLog
dispatch/       ClusterCommandDispatcher、PartitionResolver、SessionLanePool
routing/        ClusterPublicationRouter、RouteBatcher、SharedGroupCoordinator
outbox/         ClusterOutboxDispatcher、RetryPolicy、IdempotencyKeys
lifecycle/      ClusterLifecycle、DrainCoordinator、ReadinessGate
```

### 6.2 公共模型

```java
public record NodeIdentity(
        ClusterId clusterId,
        NodeId nodeId,
        Incarnation incarnation,
        NodeEndpoint endpoint) {}

public record ShardAssignment(
        ShardId shardId,
        NodeId owner,
        Incarnation ownerIncarnation,
        long epoch,
        long generation,
        AssignmentState state) {}

public record ClusterView(
        long generation,
        PartitionTable partitionTable,
        Map<NodeId, ClusterNode> nodes,
        Map<ShardId, ShardAssignment> assignments) {}
```

`PartitionTable` 将 `PartitionKey(type, partitionId)` 映射为物理 `ShardId`，并携带独立 generation。模型不可变。调用方每次处理命令时获取一个 `ClusterView` 快照；不得持有可变成员 Map 或直接修改 mapping/assignment。

### 6.3 出站端口

```java
public interface ClusterCoordinator {
    Mono<NodeRegistration> join(NodeIdentity self, NodeCapabilities capabilities);
    Flux<ClusterView> views();
    Mono<Void> updateState(NodeState state);
    Mono<Void> beginDrain();
    Mono<Void> leave();
}

public interface PeerTransport {
    Mono<Void> start(PeerRequestHandler handler);
    Mono<SessionChannel> openSessionChannel(SessionTarget target);
    Mono<RouteAck> route(RouteBatch batch);
    Mono<Void> sendControl(ControlCommand command);
    Mono<Void> stop();
}

public interface ClusterOutboxStore {
    Flux<OutboxBatch> claim(OutboxClaim claim);
    Mono<Void> acknowledge(RouteAck ack);
    Mono<Void> release(OutboxBatchId id, RetryAt retryAt);
}
```

replicated-store 额外使用：

```java
public interface ReplicatedLog {
    Mono<ReplicatedResult> append(ShardId shardId, ShardCommand command);
    Mono<Void> changeMembers(ShardId shardId, ReplicaSet replicas);
}

public interface ShardStateStore {
    Mono<ApplyResult> apply(CommittedCommand command);
    Mono<ShardSnapshot> checkpoint(ShardId shardId, SnapshotIndex index);
    Mono<Void> install(ShardSnapshot snapshot);
}
```

`ReplicatedLog.append` 只有在多数派 commit 且本节点 `ShardStateStore.apply` 完成后才返回。接口只表达业务语义，不出现 RSocket `Payload`、SQL connection、Ratis server 或 RocksDB handle。

### 6.4 核心实现

- `ClusterCommandDispatcher` 实现 `runtime-reactor` 的 `CommandDispatcher`。本地 assignment 进入 `ShardProcessor`，远端 assignment 进入 `SessionLanePool`。
- `PartitionResolver` 先用稳定 hash 生成 `PartitionKey`，再通过当前 `PartitionTable` 和 assignment 定位 Owner；收到 stale mapping/epoch 时刷新视图并重试。
- `ClusterPublicationRouter` 将逻辑目标分区写入 Outbox，不直接绑定 nodeId。
- `ClusterOutboxDispatcher` 按当前 Owner 合并 batch，限制 peer/partition 并发并保持稳定 task ID。
- `DrainCoordinator` 先阻止新 assignment，再迁移/释放所有权，最后允许 transport 和 Store 停止。

## 7. `cluster-transport-rsocket`

基础包：`cn.elvis.monaco.cluster.adapter.transport.rsocket`。

| 类 | 职责 |
| --- | --- |
| `RSocketPeerTransport` | 实现 `PeerTransport`，管理 listener 和 peer pool |
| `PeerConnectionManager` | 每对节点复用连接、重连、Resume token、mTLS |
| `RSocketHandshakeHandler` | 身份、版本、capability、plugin fingerprint 校验 |
| `RSocketSessionChannel` | request-channel 上的固定 lane 与 sequence 校验 |
| `RSocketRouteChannel` | Publication batch、RouteAck、Request N |
| `PeerPayloadCodec` | Protobuf 与 RSocket Payload 转换、引用计数释放 |
| `RSocketLeaseController` | 根据 mailbox/Outbox 水位控制新 request/channel 的 Lease 准入 |

每个 peer 连接只创建固定数量的 Session/Route lanes，不为每个 MQTT 连接建立 RSocket。`request-channel` 的完成只表示流结束；业务成功必须以编码后的 `CommandResult` 或已持久化 `RouteAck` 表达。

队列和 Payload 都必须有所有权规则。decode 后立即释放网络 Payload；大消息可以使用受控的引用计数对象，但不能跨 adapter 泄漏 Netty `ByteBuf`。

## 8. Shared-store 模块

### 8.1 `cluster-coordination-postgres`

基础包：`cn.elvis.monaco.cluster.adapter.coordination.postgres`。

| 类 | 职责 |
| --- | --- |
| `PostgresClusterCoordinator` | 实现 join/views/state/drain/leave，并在内部调度 lease renew |
| `LeaseRepository` | 使用数据库时间续约，不使用节点本地时钟判断过期 |
| `AssignmentRepository` | CAS 更新 generation、owner、epoch 和 state |
| `CoordinatorElector` | advisory lock 或租约式单协调器选举 |
| `RebalancePlanner` | 根据存活节点与容量生成确定性 placement plan |

表至少包括 `cluster_node`、`cluster_assignment`、`cluster_metadata`。所有更新包含 `cluster_id`，assignment 变更通过事务和 generation CAS 防止并发协调器覆盖。

### 8.2 `store-postgres`

基础包：`cn.elvis.monaco.cluster.adapter.store.postgres`。

| 类 | 职责 |
| --- | --- |
| `PostgresBrokerStore` | 实现运行时状态事务和恢复 |
| `PostgresClusterOutboxStore` | claim、ack、retry 与 skip-locked batch |
| `PostgresTransactionContext` | 同一事务内执行状态、fencing 和 Outbox 变更 |
| `PostgresSchemaMigrator` | 带版本 schema migration |
| `PostgresValueCodec` | 稳定二进制/JSONB 编码与 schemaVersion |

状态写入 SQL 必须同时验证 `owner_node_id + owner_incarnation + epoch`。`RouteAck` 只能在目标 Delivery 唯一键提交后返回。连接池耗尽、事务冲突和数据库不可用通过 `Mono.error` 传播，不允许在 Reactor I/O EventLoop 上同步等待 JDBC。

## 9. Replicated-store 模块

### 9.1 `cluster-consensus-ratis`

基础包：`cn.elvis.monaco.cluster.adapter.consensus.ratis`。

| 类 | 职责 |
| --- | --- |
| `RatisClusterCoordinator` | 以 Metadata Group 实现成员与 placement 视图 |
| `RatisReplicatedLog` | 实现 `ReplicatedLog`，管理 Data Shard Group |
| `RatisGroupManager` | 创建、删除、成员变更和 Leader transfer |
| `RatisStateMachineBridge` | committed log -> 注入的 `ShardStateStore.apply` |
| `RatisSnapshotBridge` | checkpoint/install 协调和 snapshot metadata 校验 |
| `RatisThreadingAdapter` | 将 callback 转为 Mono，并切回目标 Shard lane |

Ratis thread、Shard lane 和 RocksDB apply executor 不能混用。Ratis adapter 不解释 MQTT 规则，只复制版本化 `ShardCommand` 并报告 commit/apply 结果。

### 9.2 `store-rocksdb-sharded`

基础包：`cn.elvis.monaco.cluster.adapter.store.rocksdb`。

| 类 | 职责 |
| --- | --- |
| `RocksShardStateStore` | 实现 `ShardStateStore`，每 Shard/Group 顺序 apply |
| `ShardDbRegistry` | 管理 shardId 到 RocksDB handle 的生命周期 |
| `RocksWriteBatchApplier` | 原子应用 Session、Delivery、Inflight、Outbox |
| `RocksCheckpointManager` | 创建、校验、安装 checkpoint |
| `ShardKeyCodec` | 前缀、schemaVersion 和稳定排序编码 |
| `ShardMigrationManager` | snapshot copy、catch-up、切换与旧副本清理 |

一个 RocksDB 目录只能由一个 Broker 进程打开。日志提交顺序是命令顺序来源，RocksDB sequence number 不能替代 Raft index。snapshot 包含 `clusterId + shardId + groupId + lastAppliedTerm/index + schemaVersion + checksum`。

## 10. `broker` 装配

`broker` 新增：

```text
config/      BrokerProfile、ClusterConfig、PeerConfig、RatisConfig、PostgresConfig
profile/     StandaloneProfile、SharedStoreClusterProfile、ReplicatedClusterProfile
lifecycle/   BrokerLifecycle、StartupRollback、ShutdownCoordinator
```

装配矩阵：

| Profile | Coordinator | Dispatcher | Store/Log | Peer |
| --- | --- | --- | --- | --- |
| standalone-memory | Local | Local | Memory | Disabled |
| standalone-rocksdb | Local | Local | Single RocksDB | Disabled |
| cluster-shared-store | PostgreSQL | Cluster | PostgreSQL | RSocket |
| cluster-replicated | Ratis Metadata | Cluster | Ratis + sharded RocksDB | RSocket |

启动时先构造 Profile graph，再统一执行生命周期；禁止先启动全部 adapter 后在运行中禁用一半。两个集群 Profile 都只装配 `cluster-transport-rsocket`；是否启用 `plugin-remote-grpc` 是独立插件配置，不能改变 PeerTransport。

## 11. 配置模型

```java
public record ClusterConfig(
        BrokerProfile profile,
        ClusterId clusterId,
        NodeId nodeId,
        NodeEndpoint bind,
        NodeEndpoint advertise,
        int partitionCount,
        int dataShardCount,
        int replicationFactor,
        int sessionLaneCount,
        int routeLaneCount,
        int mailboxCapacity,
        Duration peerTimeout,
        Duration drainTimeout) {}
```

启动时集中校验：

- cluster Profile 必须提供唯一 nodeId 和可达 advertise address。
- `partitionCount` 和 `dataShardCount` 与已创建集群元数据完全一致；`dataShardCount` 不得大于 `partitionCount`。
- replicated-store 的 replication factor 为奇数且不大于可用节点数。
- shared-store 必须提供 PostgreSQL TLS/credential；replicated-store 必须提供独占数据目录和 Raft 端口。
- lane、mailbox、Outbox batch 和最大 Payload 必须为正且有上限。
- required plugin fingerprint 不一致时不能进入 READY。

## 12. 测试设计

`cluster-testkit` 提供以下契约：

| 契约 | 实现 |
| --- | --- |
| `PeerTransportContract` | RSocket |
| `ClusterCoordinatorContract` | PostgreSQL、Ratis Metadata |
| `ClusterOutboxContract` | PostgreSQL、sharded RocksDB |
| `ReplicatedLogContract` | Ratis |
| `ShardStateStoreContract` | sharded RocksDB |
| `ClusterProfileContract` | shared-store、replicated-store |

组件测试使用动态端口和临时目录。PostgreSQL/Ratis 多节点测试使用 Testcontainers 或独立 JVM，不在同一对象图伪造节点身份。故障场景至少覆盖 duplicate、delay、disconnect、stale epoch、Leader loss、minority partition、snapshot install、drain 和重启恢复。

端到端测试使用真实 MQTT 5 客户端连接任意 Ingress，验证远程 CONNECT、QoS 1/2、takeover、Retain、Will、共享订阅和跨节点离线恢复。

## 13. 实施任务

### C0：统一 Reactor 基线

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C0-1 | `core` 保持纯领域层，异步编排迁至 `runtime-reactor` | core 无 Reactor，runtime 统一 Mono/Flux |
| C0-2 | 将 `transport-reactor` 合并到 `runtime-reactor/transport/netty` 并删除原模块 | TCP/TLS/WS 共用 BrokerEngine；settings 无旧模块 |
| C0-3 | Shard lane 与有界 mailbox | 同 Shard 串行，满载策略可测试 |

### C1：集群契约与本地模拟

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C1-1 | `cluster-protocol` schema 与兼容测试 | v1 round-trip、未知字段测试通过 |
| C1-2 | `cluster-runtime` 模型、端口、PartitionResolver | 无基础设施依赖 |
| C1-3 | in-memory coordinator/peer fixtures | 多节点逻辑流程可确定性测试 |

### C2：Shared-store 控制与状态

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C2-1 | PostgreSQL schema/migration | 全新库与升级路径通过 |
| C2-2 | coordination lease/assignment/fencing | 双 coordinator、旧 Owner 测试通过 |
| C2-3 | Store transaction + Outbox | persist-before-ACK 与幂等通过 |

### C3：RSocket 数据平面

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C3-1 | handshake、peer pool、mTLS | 不兼容节点被拒绝 |
| C3-2 | Session request-channel | 远程 CONNECT/takeover/QoS 通过 |
| C3-3 | Route channel、Lease、batch | 队列有界，重连不重复 Delivery |

### C4：集群语义与部署

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C4-1 | shared subscription、Retain、Will | 跨节点语义测试通过 |
| C4-2 | drain、rebalance、rolling upgrade | 无双 Owner 写入 |
| C4-3 | Compose/Swarm/Kubernetes manifests | advertise、Secret、readiness 验证通过 |

### C5：Ratis + RocksDB Profile

| 任务 | 产出 | 退出标准 |
| --- | --- | --- |
| C5-1 | Metadata Ratis Group | membership/placement 可恢复 |
| C5-2 | Data Shard Group + Rocks apply | majority commit 后才 ACK |
| C5-3 | snapshot、成员变更、迁移 | follower catch-up 和 Leader transfer 通过 |
| C5-4 | 跨 Shard Outbox/Saga | 故障重试不丢失、不重复 Delivery |

### C6：生产验收

- 长稳、容量、TLS、证书轮换和滚动升级报告。
- 网络分区、数据库故障、磁盘满、慢插件和进程崩溃演练。
- shared-store 与 replicated-store 分别生成运行手册和恢复手册。
- 默认镜像不包含 Vert.x、Pekko 或任何 gRPC cluster transport。

## 14. 模块验收标准

1. Gradle 依赖图符合第 3 节，不存在循环和反向依赖。
2. `core` 只有 Java 标准库与 `protocol`；`cluster-runtime` 不含基础设施依赖。
3. Protobuf、RSocket、Ratis、SQL 和 RocksDB 类型不越过各自 adapter 边界。
4. `broker` 是唯一 `main` 所在模块，集群不新增 `broker-app` 或独立应用入口。
5. standalone、shared-store、replicated-store Profile 均通过同一 MQTT 行为测试集。
6. 每个 port 至少有一个 contract test；RSocket 和两个存储 Profile 有故障测试。
7. 所有 executor、mailbox、连接池和 batch 参数可配置、可观测且有硬上限。
8. 集群模块图和 runtimeClasspath 中不存在 `cluster-transport-grpc`；gRPC 只能由插件远程适配器引入。
9. settings 与 broker runtimeClasspath 不存在独立 `transport-reactor`；Reactor Netty/Netty MQTT 不从 `runtime-reactor` 公共 API 泄漏。
10. `io.netty.*` import 只允许出现在 `cn.elvis.monaco.runtime.transport.netty`，由包边界测试持续验证。
