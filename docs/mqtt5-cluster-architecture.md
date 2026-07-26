# Monaco MQTT 5.0 集群架构设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置文档：[单节点架构](mqtt5-architecture.md)、[客户端状态设计](mqtt5-client-state-design.md)、[集群技术设计](mqtt5-cluster-design.md)、[集群通信协议](mqtt5-cluster-protocol.md)、[插件系统](plugin-system.md)
>
> 模块落地：[集群模块详细设计](mqtt5-cluster-module-design.md)

## 1. 目标与决策

Monaco 使用同一发行包支持单机和集群，通过配置选择运行 Profile。集群只扩展命令定位、跨节点传输和持久化方式，不复制 MQTT 状态机。

1. `core` 保存同步纯 Java 的状态转换和稳定 Reactor 应用端口；只有 `core.port` 可以使用 `Mono`/`Flux`，领域包不依赖 Reactor。
2. `runtime` 是单机和集群共用的执行内核，包含 BrokerEngine、用例编排、ShardMailbox 与项目唯一的 Reactor Netty 客户端接入。
3. `runtime-standalone` 只提供 LocalDispatcher、本地所有权和单机 Profile；`runtime-cluster` 只处理成员视图、分片定位、远程派发、Outbox 和迁移，不解释 MQTT 报文。
4. Reactor Netty 负责客户端 TCP/TLS/WebSocket 接入，RSocket 是 Broker 节点间唯一的数据与控制传输。
5. `shared-store` 与 `replicated-store` 是互斥 Profile；前者以 PostgreSQL 为权威，后者以 Ratis Group 和本地 RocksDB 为权威。
6. 所有跨节点请求均按至少一次设计，依靠稳定 ID、epoch 和持久化幂等消除重复副作用。

## 2. 系统上下文

```text
MQTT clients
    |
Load balancer / direct connection
    |
+---------------- Monaco node ----------------+
| runtime                                      |
|   transport.netty -> Broker use cases        |
|          |                                   |
| ClusterDispatcher -- runtime-cluster         |
|      | local             | remote            |
|      v                   v                    |
| ShardProcessor      RSocket peer transport --+---- peer node
|      |                                        |
| selected persistence profile                 |
|   PostgreSQL  OR  Ratis + local RocksDB       |
+-----------------------------------------------+
```

客户端可以连接任意节点。接入节点称为 Ingress，保存实际 Netty Channel 和 `PhysicalConnectionState`；分片 Owner 保存 `LogicalConnectionState`、`SessionRecord` 和独立业务记录。Ingress 与 Owner 可以是同一节点，也可以通过 RSocket Session lane 通信，字段与生命周期以[客户端状态设计](mqtt5-client-state-design.md)为准。

## 3. 逻辑平面

| 平面 | 职责 | 权威状态 |
| --- | --- | --- |
| 接入平面 | socket、TLS、MQTT codec、连接背压 | Ingress 内存中的 Channel registry |
| 会话平面 | CONNECT、takeover、QoS、Session、Will | Session Shard Owner + Store |
| 路由平面 | 订阅索引、普通/共享投递、Retain replay | Route/Shared Shard Owner + Store |
| 控制平面 | 节点身份、租约、placement、epoch、drain | PostgreSQL coordination 或 Metadata Raft Group |
| 持久化平面 | Session、Inflight、Delivery、Outbox、snapshot | PostgreSQL 或每个 Raft Group 的 RocksDB |

控制平面只决定“当前谁可以写”。RSocket 可达性、DNS、容器健康检查和心跳都不能代替 assignment epoch 或 Raft term。

## 4. 运行 Profile

| Profile | Dispatcher | 协调权威 | 数据持久化 | 节点传输 |
| --- | --- | --- | --- | --- |
| `standalone-memory` | Local | 本进程 | Memory | 关闭 |
| `standalone-rocksdb` | Local | 本进程 | 单机 RocksDB | 关闭 |
| `cluster-shared-store` | Cluster | PostgreSQL lease/assignment | PostgreSQL | RSocket |
| `cluster-replicated` | Cluster | Metadata Ratis Group | Shard Ratis Group + 本地 RocksDB | RSocket + Raft TCP |

Profile 在启动时确定，运行中不能切换。`broker` 在单机模式装配 `runtime-standalone`，在集群模式装配 `runtime-cluster`，并选择对应 `CommandDispatcher`、`ClusterCoordinator` 和 `BrokerStore`。不得在 `core` 或共享 `runtime` 中散布 Profile 条件判断。

### 4.1 Shared-store Profile

PostgreSQL 同时保存 Broker 状态、Cluster Outbox、节点租约和分片 assignment。每次状态事务校验 `partitionId + epoch`，旧 Owner 即使仍存活也不能提交。数据库是共享故障域，生产环境必须独立提供 HA、备份和容量治理。

### 4.2 Replicated-store Profile

每个数据 Shard 对应一个 Ratis Group，默认三个副本。Leader 将已提交命令按序应用到本地 RocksDB；只有多数派提交且本地 apply 完成，相关 `Mono` 才完成。失去多数派的 Shard 停止写入，不能降级后继续发送 MQTT ACK。

Pekko 不与该 Profile 混用。若选择 Pekko Cluster Sharding，必须作为替换 `runtime-cluster + Ratis` 的独立 Actor 架构重新设计。

## 5. 组件边界

### 5.1 领域与运行时

`core` 输入 `Command + State`，输出 `Transition(newState, actions)`。它不知道命令来自本地连接还是远端节点，也不执行 I/O。

`runtime` 实现 Reactor Netty MQTT 接入、Broker 用例、Connection Processor、Shard lane 和 persist-before-send 流程。客户端传输实现仅位于 `cn.elvis.monaco.runtime.transport.netty`；其 Reactor Netty、Netty MQTT codec、Channel 和 `PhysicalConnectionState` 不得进入公共 API 或其他 runtime 包。runtime 定义由两个 Profile 实现的 `CommandDispatcher`；稳定的外部能力端口位于 `core.port`：

- `BrokerStore`：事务读取、提交、恢复和 snapshot。
- `ConnectionSink`：将服务端报文返回实际 Ingress。
- `PolicyRuntime`：执行认证、授权和修改 Hook。
- `RuntimeScheduler`：Keep Alive、Will、Session Expiry 和维护任务。

### 5.2 Profile 运行时

`runtime-standalone` 实现 LocalDispatcher 和本地分片所有权，不依赖任何集群模块。`runtime-cluster` 持有不可变的 `ClusterView` 和 `PartitionTable` 快照，提供 Session 派发、Publication fanout、Outbox drain 和 drain/rebalance 编排。两个模块都依赖共享 `runtime`，互不依赖；runtime-cluster 不依赖 RSocket、PostgreSQL、Ratis 或 RocksDB 的具体类型。

### 5.3 基础设施适配器

- `cluster-transport-rsocket` 实现 peer handshake、Session/Route channel、Lease、Resume 和 mTLS。
- `cluster-coordination-postgres` 实现 shared-store 的 lease、assignment、epoch 和 coordinator election。
- `cluster-consensus-ratis` 实现 replicated-store 的 Metadata/Data Raft Group、成员变更和 snapshot install。
- `store-postgres` 实现 core BrokerStore、共享状态事务、唯一约束，并在同一事务原子写入 Outbox 行。
- `cluster-outbox-postgres` 实现 runtime-cluster 的 Outbox claim、ack、retry 和 drain 端口；它复用 Outbox schema，但不参与 MQTT 状态写入。
- `store-rocksdb-sharded` 实现 shard-local WriteBatch、checkpoint、schema 和 snapshot。

适配器之间不直接调用。RSocket 不能访问 RocksDB，PostgreSQL coordination 不能操作 Netty Channel，Ratis adapter 只能通过 `runtime-cluster` 定义的 apply/snapshot 端口访问状态机。

## 6. 分片与所有权

逻辑 Partition 用于生成稳定路由目标，物理 Data Shard 用于 assignment、Raft Group、RocksDB 和迁移。两者数量在创建集群后都不可修改：

```text
sessionPartition = stableHash("session", clientId) % partitionCount
retainPartition  = stableHash("retain", topic) % partitionCount
sharedPartition  = stableHash("shared", group, filter) % partitionCount
dataShard         = partitionTable[partitionType, partitionId]
```

建议从每类 1024 个逻辑 Partition、全局 64 或 128 个物理 Data Shard 开始。默认映射可由稳定 hash 生成，后续只能通过带 generation 的元数据迁移变更，不能由各节点自行计算出不同结果。一个 Data Shard 承载多个类型的逻辑 Partition；一个 replicated-store Data Shard 对应一个 Ratis Group，而不是每个逻辑 Partition 对应一个 Group。

每个 assignment 至少包含 `shardId`、`ownerNodeId`、`ownerIncarnation`、`epoch`、`generation` 和 `state`。分片请求携带 `partitionType + partitionId + shardId + epoch`；Owner 在执行和提交时再次校验映射与 epoch，不能只相信发送方的路由快照。

所有权规则：

- Ingress 独占物理 Channel 和传输级 `ConnectionSink`。
- Session Owner 独占同一 `clientId` 的状态转换顺序。
- Route Owner 独占其订阅索引快照和 Delivery 创建顺序。
- Shared Shard Owner 独占组内消费者选择。
- shared-store 的 PostgreSQL 事务或 replicated-store 的 Raft Leader 是最终写权限来源。

## 7. 关键流程

### 7.1 远程 CONNECT

```text
Client -> Ingress: CONNECT
Ingress: assign stable clientId when CONNECT clientId is empty
Ingress -> ClusterDispatcher: sessionShard(clientId)
ClusterDispatcher -> Owner SessionLane: command(epoch, generation, sequence)
Owner -> PolicyRuntime: authenticate/authorize
Owner -> BrokerStore: takeover + session transaction
BrokerStore -> Owner: committed
Owner -> Ingress: CONNACK / close previous connection
Ingress -> Client: CONNACK
```

Netty Channel 不跨节点传输。远端 `ConnectionSink` 只发送带 `connectionId + connectionGeneration + sequence` 的逻辑响应。Owner 或 Session lane 失联时，Ingress 关闭 MQTT 连接，由客户端按协议重连。

### 7.2 PUBLISH 与跨 Shard 路由

1. Session Owner 执行协议校验、插件 Hook、授权和配额检查，并解析连接级 Topic Alias。
2. Message、清理后的 routed properties、入站 QoS 状态和 `ClusterOutbox` 在同一事务提交。
3. QoS 1/2 ACK 仅在事务成功后返回；此时 Broker 已承担后续投递责任。
4. Outbox 按逻辑目标 `partitionId` 解析当前 Owner，通过 Route channel 批量发送。
5. Route Owner 幂等创建 Delivery，再返回 `RouteAck`。
6. 发送端标记 Outbox 完成；超时、断线或迁移时以相同 ID 重试。

插件修改 Hook 只在入口 Session Owner、首次持久化前执行。远端路由、恢复、Retain replay 和重发不能重复执行修改 Hook。

### 7.3 Replicated-store 提交

```text
Shard lane -> Ratis Leader: append(commandId, epoch, command)
Ratis Leader -> replicas: replicate
majority -> committed log
each state machine -> RocksDB: WriteBatch apply
local apply -> Shard lane: committed result
Shard lane -> ConnectionSink/RouteAck: response
```

跨 Shard 事务不伪装成 ACID。源 Shard 通过持久 Outbox 驱动目标 Shard，目标以 `commandId/routeTaskId/deliveryId` 幂等，补偿和重试采用 Saga 语义。

## 8. 一致性与故障边界

| 事件 | 允许结果 | 禁止结果 |
| --- | --- | --- |
| Peer 请求超时 | 使用相同 requestId 重试或关闭连接 | 假定请求未执行并生成新业务 ID |
| Owner 失联 | 新 assignment/term 后接管 | 仅凭心跳在两个 Owner 同时写 |
| RouteAck 丢失 | 重发并命中唯一约束 | 重复创建 Delivery |
| Ratis 失去多数派 | 对受影响 Shard 停止写 | 单副本继续 ACK |
| Ingress 退出 | 客户端重连，Session 从 Store 恢复 | 跨进程迁移现有 TCP Channel |
| 插件不一致 | 节点拒绝加入 assignment | 同一 generation 使用不同 required Hook |

协议不承诺网络意义的 exactly-once。Monaco 保证提交点明确、重复命令幂等、旧 Owner 被 fencing，并保持 MQTT QoS 状态机可恢复。

## 9. Multi-Reactor 执行模型

```text
acceptor/selector
    -> Reactor Netty I/O EventLoops
    -> bounded MPSC shard mailboxes
    -> serialized Shard lanes
    -> RSocket/Ratis/RocksDB executors
```

Channel 在生命周期内固定到一个 Netty EventLoop，不同连接分散到多个 worker。网络 multi-reactor 与 Session 串行化是两层机制：同一 Shard 的命令必须进入同一个单线程 lane，不允许使用 `parallel()`/`parallelFlux()` 并发修改状态。

RSocket、Ratis 或 Store 完成回调必须重新调度到原 Shard lane，再应用 Transition 和 Action。I/O EventLoop 上禁止 RocksDB、JDBC、插件或证书文件等阻塞调用。所有 mailbox、peer lane 和 Outbox batch 都必须有界。

## 10. 生命周期与部署

启动顺序：配置校验 -> 节点身份 -> Store/Consensus 恢复 -> Coordination 加入 -> assignment 就绪 -> peer transport -> plugin runtime -> MQTT listener -> READY。失败时按相反顺序释放已启动资源。

Drain 顺序：停止接收新连接 -> 标记节点 DRAINING -> 转移分片/Leader -> 等待 Inflight 与 Outbox 到阈值 -> 关闭现有连接 -> 退出成员视图 -> 关闭 Store。超过 grace period 后强制退出，但不能绕过 fencing。

Docker、Compose、Swarm 和 Kubernetes 只提供进程、网络、Volume 与 Secret。外部 MQTT Service 和内部 peer/Raft Service 分离；RocksDB 目录不能跨节点共享，也不能位于 NFS。

## 11. 安全与可观测性

Peer 和 Raft 端口默认使用 mTLS，证书身份绑定 `clusterId + nodeId`。Handshake 还校验 incarnation、协议版本、能力位、required plugin fingerprint 和报文限制。peer 端口不得暴露公网。

日志和 trace 至少携带 `clusterId`、`nodeId`、`incarnation`、`shardId`、`epoch`、`requestId`、`connectionId` 和 `messageId`。指标分别覆盖成员/assignment、peer latency、lane backlog、Outbox、fencing、Raft term/commit、RocksDB apply/snapshot 和 EventLoop delay。

## 12. 架构约束与验收

1. 单机和集群复用同一 `core` 与 `runtime`，不维护两套 MQTT 状态机、Handler 或客户端接入。
2. 生产 runtimeClasspath 不包含 Vert.x；公共异步端口不出现 `Future` 或 `CompletionStage`。
3. `runtime-cluster` 不依赖任何传输、数据库或共识实现；`runtime-standalone` 与 `runtime-cluster` 互不依赖。
4. shared-store 与 replicated-store 可独立装配、构建和测试，不在同一进程同时启用。
5. 旧 epoch、旧 incarnation 和非 Leader 写入在持久化边界被拒绝。
6. QoS 1/2 ACK、RouteAck 和 Session 成功响应都发生在规定提交点之后。
7. 断线、重复、乱序、节点退出和分片迁移测试不产生重复 Delivery 或双 Owner 写入。
8. 所有异步队列有配置上限、指标和明确的满载策略。
9. 不存在 `cluster-transport-grpc`；Session、Route、Control 和 Health 均通过 RSocket。
