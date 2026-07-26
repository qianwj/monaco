# Runtime 架构与实现评审

> 评审日期：2026-07-26
>
> 评审对象：当前工作区 `runtime-reactor`、规划中的集群运行时、相关 Gradle 依赖与架构文档
>
> 结论：目标拆分为共享 `runtime`、`runtime-standalone` 和 `runtime-cluster`；`core` 引入 Reactor 并承接稳定应用端口；当前实现仍需重新设计，不能作为集群阶段基线

## 1. 背景与结论

最初单机架构由 `core` 同时承担领域规则和 Reactor 用例编排。引入集群、Ratis 确定性状态机和多种 Store Profile 后，目标架构将其拆为：

```text
protocol <- core
            |-- command/state/transition/rule   pure Java
            `-- port                            Mono/Flux contracts
                 ^                    ^
                 |                    |
              runtime           store/security/plugin/obs
        use case/mailbox/transport
            ^              ^
            |              |
 runtime-standalone   runtime-cluster
 LocalDispatcher     placement/remote/outbox
```

该拆分以包边界保证领域纯度，并让单机/集群复用同一应用端口。问题不在模块是否存在，而在当前端口所有权、依赖、事务、连接定位和并发实现尚未满足这个边界。

## 2. Runtime 模块拆分决策

项目已确定 MQTT 客户端接入只支持 Reactor Netty，不提供可替换的客户端 Transport SPI，因此 `transport-reactor` 合并到共享 `runtime`。但共享运行时不能直接改名为 `runtime-standalone`：BrokerEngine、处理器、提交管线、ShardMailbox 和客户端接入同时被单机与集群使用。目标结构是一个共享模块加两个薄 Profile 模块：

```text
runtime/
  engine/                  BrokerEngine 与默认实现
  handler/                 CONNECT/PUBLISH/SUBSCRIBE 等编排
  dispatch/                typed CommandDispatcher 端口、ShardMailbox
  session/                 SessionProcessor、ShardMailbox
  transport/netty/         Reactor Netty、MQTT codec、PhysicalConnectionState

runtime-standalone/
  dispatch/                LocalDispatcher
  ownership/               本地分片所有权
  profile/                 standalone lifecycle/profile

runtime-cluster/
  model/                   ClusterView、PartitionTable、Assignment
  dispatch/                ClusterCommandDispatcher、Session lanes
  routing/                 fanout、Outbox、shared group
  lifecycle/               drain、rebalance、readiness
```

目标依赖为：

```text
runtime-standalone -> runtime -> core
runtime-cluster    -> runtime -> core
```

两个 Profile 模块互不依赖，也不复制 Broker 用例或 BrokerEngine。`runtime-standalone` 只提供 LocalDispatcher、本地所有权和单机 Profile；`runtime-cluster` 是原规划 `cluster-runtime` 的重命名，负责 placement、远程派发、Outbox 和迁移。`broker` 仍是唯一 Composition Root，在启动时装配一个 Profile。

合并只取消 Gradle 模块边界，不取消逻辑边界：

1. Netty `Channel`、`ByteBuf`、`MqttMessage` 只能出现在 `transport.netty` 包。
2. `runtime` 公共接口只暴露 protocol/core 类型、`ConnectionRef` 和 Mono/Flux；外层 adapter 实现的稳定端口归 `core.port`。
3. Reactor Netty 与 Netty MQTT codec 使用 `implementation`，不能从 `api` 泄漏。
4. `PhysicalConnectionState` 位于 `transport.netty`；`LogicalConnectionState` 和 Session 状态不能持有 Channel。
5. `cluster-transport-rsocket` 仍是独立模块并依赖 `runtime-cluster`；它解决 Broker peer 通信，不属于 MQTT 客户端接入。
6. 用包依赖测试阻止 `engine/handler/session/port` 导入 `io.netty.*`。

迁移时将当前 `runtime-reactor` 重命名为 `runtime`，把 `LocalDispatcher` 移到新建的 `runtime-standalone`，并将规划中的 `cluster-runtime` 命名为 `runtime-cluster`。当前 `transport-reactor` 还没有生产源码，现在合并成本最低。若未来改变“仅 Reactor Netty”这一产品约束，必须重新评审模块边界，不能先引入无类型 Transport SPI。

## 3. core 的 Reactor 与 Store 端口决策

这一项属于 `core` 模块整改，不属于 `runtime` 内部重构。此前把“领域包保持纯函数”扩大成“整个 core 模块不能依赖 Reactor”，导致 Store SPI 被放到当前 `runtime-reactor`，依赖方向失衡：当前 `store:memory` 和 `store:rocksdb` 只依赖 `core`，却无法实现 runtime 中的接口；若改为依赖 runtime，又会把 Engine、mailbox 和客户端传输带入 Store adapter 的编译边界。

目标调整为：

```text
core                    -> protocol + reactor-core
runtime                 -> core
runtime-standalone      -> runtime
store-*                 -> core
security/plugin/obs     -> core + 各自 API
runtime-cluster         -> core + runtime
broker                  -> runtime + selected profile + selected adapters
```

`core` 的含义调整为“领域模型 + 稳定应用端口”：

1. `command/state/transition/rule` 保持同步纯 Java，禁止导入 `reactor.*`。
2. `core.port` 可以使用 `Mono`/`Flux`；因此 `reactor-core` 是 `core` 的 `api` 依赖。
3. Store、安全、策略、领域事件和 Telemetry 等外层 adapter 实现的稳定端口归 `core.port`。
4. `runtime` 消费这些端口并实现用例编排，不再拥有 Store SPI；两个 Profile 模块也不得重新定义 Store 接口。
5. 通过包边界测试保证 Reactor 只能进入 `core.port`，不能污染确定性的 Transition。

Store 端口不能只是把现有多个 CRUD 接口原样移动到 core。`SessionStore`、`SubscriptionStore`、`FlightWindowStore`、`WillStore` 等必须收敛为一个表达 Shard-local 原子提交的 `BrokerStore`：

```java
public interface BrokerStore {
    Mono<ShardSnapshot> load(ShardId shardId);
    Mono<CommitResult> commit(StoreCommit commit);
    Flux<RecoveryRecord> recover();
}
```

`StoreCommit` 至少携带 `shardId`、`expectedRevision`、`expectedEpoch`、`MutationBatch` 和 Outbox 变更。Memory adapter 使用锁与不可变快照，RocksDB 使用一个 WriteBatch，PostgreSQL 使用一个数据库事务，replicated Profile 只有在 Ratis 多数派提交并 apply 后才完成 `Mono`。`ShardStateStore` 是 Ratis/RocksDB 适配器内部端口，仍归 cluster 模块，不进入通用 core API。

此前的 `runtime-reactor/.../store/InMemoryStateTransaction.java` 只是依次调用多个异步 Store，不具备原子性；该类应保持删除状态，并由 `store:memory` 实现新的 `BrokerStore`。

## 4. 阻断性问题

### P0-1：违反 persist-before-send

[`ActionExecutor`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/handler/ActionExecutor.java) 先顺序执行网络发送、关闭连接和各 Store 操作，最后才保存 Session。`SessionStore`、`SubscriptionStore`、`FlightWindowStore` 和 `WillStore` 又是彼此独立的调用，不能形成 Shard-local 原子事务。

这会产生 ACK 已发送但状态未提交、Session 已更新但 Inflight 未更新、崩溃后 QoS 无法恢复等错误。必须改为：

```text
Transition -> MutationBatch -> BrokerStore commit/raft commit
           -> update derived cache -> execute external actions
```

网络 Action、timer 和提交后事件只能在持久化成功后执行。

### P0-2：连接接管可能关闭新连接

[`ConnectHandler`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/handler/ConnectHandler.java) 在执行 takeover Action 前将 `clientId` 绑定到新 `connectionId`；[`ConnectionRegistry`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/connection/ConnectionRegistry.java) 和 Action 又只按 `clientId` 查找连接。因此 Session Taken Over 和 Close 很可能被发送给新连接。

所有网络 Action 必须指向完整的 `ConnectionRef(connectionId, generation, ingressNode)`。物理 Channel registry 应位于 `runtime` 的 `transport.netty` 包，其他 runtime 包只依赖 `ConnectionSink` 端口。

### P0-3：Dispatcher 集群契约仍需冻结

[`CommandDispatcher`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/dispatch/CommandDispatcher.java) 已从 lambda 改为类型化 `SessionCommand -> Mono<CommandResult>`，原始阻断已经修复。剩余工作是冻结分区定位、stale epoch/redirect、取消和生命周期语义，并建立 Local/Cluster contract test。

`SessionCommand` 必须保持纯数据并可映射到 cluster wire；Local 实现进入本地 Shard mailbox，Cluster 实现根据 clientId/PartitionTable 决定本地执行或映射到 RSocket frame。不能把本地 handler lambda 放入接口或 command。

### P0-4：ShardMailbox 已实现但缺少并发证明

[`LocalDispatcher`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/dispatch/LocalDispatcher.java) 已改为有界 [`ShardMailbox`](../../../runtime-reactor/src/main/java/cn/elvis/monaco/runtime/dispatch/ShardMailbox.java)，并使用 `concatMap` 等待前一个异步 command 终止。实现方向正确，但当前没有并发、溢出、取消、dispose 与未消费错误测试。

验收必须证明同一 Shard 的 active command 最大为 1，并验证队列容量、拒绝结果、指标和关闭期间未完成 command 的确定语义。

### P0-5：LogicalConnectionState 未参与处理

Publish、Subscribe、Unsubscribe 等 Handler 调用 Transition 时传入 `null` connection，无法校验 connection generation、Topic Alias、Receive Maximum 和连接级限制。当前也没有实现 `ConnectionProcessor` 或 `SessionProcessor` 保存这一运行时状态。

命令处理必须先按 `ConnectionRef` 取得绑定的 `LogicalConnectionState`，再在 Shard lane 内完成 generation 和 command sequence 校验。

### P0-6：Store 端口所有权和粒度错误

Store 接口位于当前 `runtime-reactor`，与现有 adapter 依赖方向不一致；多个 CRUD Store 和运行时拼装的 `StateTransaction` 又不能提供真实原子性。

该问题由 `core` 整改负责：core 引入 `reactor-core`，定义单一、类型化、可做 revision/epoch fencing 的 `BrokerStore`；`runtime` 只消费端口，具体 Store 模块负责原子提交实现。

## 5. 重要问题

### P1-1：runtime-reactor 未接入应用装配

[`broker/build.gradle.kts`](../../../broker/build.gradle.kts) 当前没有依赖 `runtime-reactor`，transport 也尚未并入，因此 runtime 仍是可独立编译的代码岛。`store:memory`、`store:rocksdb`、security 和 plugin runtime 直接依赖 `core` 与修订后的目标方向一致；缺失的是 core 中可供它们实现的稳定端口。

迁移后的目标依赖必须是：

```text
runtime                -> core
runtime-standalone     -> runtime
store-*                -> core ports
security/plugin/obs    -> core ports
runtime-cluster        -> runtime + core
broker                 -> runtime + selected profile + selected adapters
```

### P1-2：完成状态和测试不可信

当前任务文档把 Handler、ActionExecutor、Dispatcher 标记为完成，但仍有空 timer callback、Will pipeline TODO、订阅/Inflight 清理 TODO，且缺少 ConnectionProcessor、SessionProcessor 和真正的 ShardLane。

执行 `./gradlew :runtime-reactor:test :broker:test` 虽然成功，但 `runtime-reactor:test` 和 `broker:test` 均为 `NO-SOURCE`；transport、broker 和 Store adapter 也没有生产源代码。编译通过不能作为功能完成证明。

### P1-3：core 仍包含基础设施配置

当前 `core.config.BrokerConfig` 同时包含 transport、认证文件、RocksDB 路径和 metrics。虽然没有框架依赖，但这些不是领域输入。应由 broker 配置层拆成 adapter config 和不可变 `BrokerPolicy/ProtocolLimits`，Transition 只接收所需的领域策略。

## 6. 符合设计的部分

- `core` 的领域 Transition 当前不依赖 Reactor 或 I/O，这个包级约束应继续保留。
- 当前 `runtime-reactor` 已使用 Mono/Flux 编排；迁移到 `runtime` 时，其中稳定的外部 adapter 端口需要迁至 core。
- 当前 `runtime-reactor` 没有依赖 Vert.x、RSocket 或数据库驱动；目标 `runtime` 只允许 `transport.netty` 使用 Reactor Netty/Netty MQTT。
- `Command + State -> TransitionResult` 的纯领域方向可以继续保留。

这些只能证明模块边界方向基本正确，不能抵消事务和串行化问题。

## 7. 整改顺序

1. **修正 core 边界**：引入 Reactor Core，将稳定应用端口迁入 `core.port`，用包边界测试保护纯领域包。
2. **重定义 Store 端口**：以类型化 `BrokerStore`、`StoreCommit` 和 `MutationBatch` 替换 runtime 中分散的 CRUD Store。
3. **建立共享 runtime**：将 `runtime-reactor` 重命名为 `runtime`，吸收 `transport-reactor` 的 Netty 依赖和客户端接入。
4. **建立 Profile 模块**：把 LocalDispatcher 迁至 `runtime-standalone`，以 `runtime-cluster` 取代规划中的 `cluster-runtime`；二者只依赖共享 runtime。
5. **重定义运行时端口**：引入类型化 `SessionCommand`、`CommandResult`、`ConnectionRef` 和 `ConnectionSink`。
6. **重写提交管线**：Transition 只产生 State Mutation 和提交后 Action；一次命令只允许一个原子提交点。
7. **实现真正的处理器**：完成 ConnectionProcessor、SessionProcessor、有界 ShardMailbox 和确定的关闭流程。
8. **接入基础设施**：`transport.netty` 持有物理 Channel，Store 实现 core 事务端口，runtime-cluster 实现远程 Dispatcher。
9. **补齐测试**：完成单机契约、崩溃点、takeover、QoS 恢复、队列满载和集群 Dispatcher 测试后再进入集群模块实现。

## 8. 验收标准

1. settings 中包含 `runtime`、`runtime-standalone` 和 `runtime-cluster`，不再包含 `runtime-reactor`、`transport-reactor` 或 `cluster-runtime`。
2. `core` 只增加 Reactor Core 公共 API；领域包不导入 Reactor，runtimeClasspath 不包含 Netty、数据库和配置加载实现。
3. 同一 clientId 的并发命令测试证明 active count 最大为 1，而不是只验证运行在线程名相同的 Scheduler。
4. Store/commit 失败时不会发送 CONNACK、PUBACK、PUBREC、PUBCOMP 或 Delivery。
5. takeover 只关闭旧 `ConnectionRef`，迟到 Detach 不影响新 generation。
6. Local 与 Cluster Dispatcher 通过同一 typed-command contract test。
7. timer、Will、订阅和 Inflight 操作不存在空实现或 TODO 分支。
8. Reactor Netty/Netty MQTT 是 `runtime` 的 `implementation` 依赖，公共 API 和非 transport 包没有 `io.netty.*` 类型。
9. runtime、Store 和 broker 均有实际源代码与自动化测试，不再以 `NO-SOURCE` 构建作为完成标准。
10. `BrokerStore` 位于 `core.port`，store-* 不依赖任何 runtime 模块；runtime 中不存在可被 adapter 实现的 Store SPI。
11. Memory、RocksDB、PostgreSQL 和 replicated Store 通过同一原子提交契约，故障测试证明不会部分提交 MutationBatch。
12. runtime-standalone 与 runtime-cluster 均通过同一 BrokerEngine/Dispatcher contract；两个 Profile 模块互不依赖且不复制 MQTT Handler。
