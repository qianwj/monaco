# cluster-testkit 模块实现任务

> 模块：`cluster-testkit`（未来模块，当前未加入 `settings.gradle.kts`）
>
> 基础包：`cn.elvis.monaco.cluster.testkit`
>
> 当前状态：待 C1 创建
>
> 依赖：`runtime-cluster`、`cluster-protocol`、`testFixtures(project(":testkit"))`
>
> 关联设计：[集群模块详细设计 §12](../mqtt5-cluster-module-design.md#12-测试设计)、[Testkit 实现方案](../tests/testkit-implementation.md)、[Cluster Case](../tests/test-cases.md#9-cluster-case)

## 1. 目标与边界

cluster-testkit 在基础 testkit 的 Clock、Probe、Trace、Scenario、Client 和 Fault 能力之上，提供集群公共端口契约、确定性本地模拟、多节点进程夹具、网络故障控制和 Profile 一致性测试。

必须发布六套契约：

1. `PeerTransportContract`
2. `ClusterCoordinatorContract`
3. `ClusterOutboxContract`
4. `ReplicatedLogContract`
5. `ShardStateStoreContract`
6. `ClusterProfileContract`

边界：

- 不复制基础 MQTT Scenario DSL 或客户端夹具。
- 不依赖具体 RSocket、PostgreSQL、Ratis 或 RocksDB 实现类型；不提供已放弃的 gRPC peer provider。
- 适配器在自身测试代码中提供 provider 并继承契约。
- C1 in-memory fixture 只验证端口和路由语义；真实多节点必须使用独立 JVM 或 Testcontainers。
- cluster-testkit 只能作为 test fixture/testImplementation 依赖，不能进入 Broker 生产启动入口。

## 2. 开始条件

| 编号 | 条件 | 阶段 | 状态 |
| --- | --- | --- | --- |
| CPRE-01 | C0 冻结 core Reactor 端口，建立 runtime/runtime-standalone，并将客户端接入合并到 `runtime/transport.netty` | C1 | ⬜ 待实现 |
| CPRE-02 | `cluster-protocol` v1 envelope/schema 和 codec 可编译 | C1 | ⬜ 待实现 |
| CPRE-03 | `runtime-cluster` 模型及 Peer/Coordinator/Outbox 端口冻结 | C1-C2 | ⬜ 待实现 |
| CPRE-04 | `store-postgres`、`cluster-outbox-postgres`、`cluster-coordination-postgres` 测试配置可用 | C2 | ⬜ 待实现 |
| CPRE-05 | RSocket peer transport 端口和 mTLS 配置冻结 | C3 | ⬜ 待实现 |
| CPRE-06 | Shared Group、drain/rebalance 端口冻结 | C4 | ⬜ 待实现 |
| CPRE-07 | Ratis log/apply/snapshot 和 sharded Store 端口冻结 | C5 | ⬜ 待实现 |

## 3. 任务总览

| 任务 | 产出 | 前置 | 主要 Case | 状态 |
| --- | --- | --- | --- | --- |
| CTK-C1-1 | 模块构建与依赖边界 | CPRE-01~CPRE-03 | runtimeClasspath 边界 | ⬜ 待实现 |
| CTK-C1-2 | Cluster wire fixtures/assertions | CPRE-02 | CL-011~CL-013 | ⬜ 待实现 |
| CTK-C1-3 | PartitionResolverContract | CPRE-03 | CL-014 | ⬜ 待实现 |
| CTK-C1-4 | InMemory Peer/Coordinator | CPRE-03、基础 testkit | CL-002、CL-006~CL-010 | ⬜ 待实现 |
| CTK-C1-5 | Cluster Fixture SPI 与状态 Probe | CTK-C1-1~C1-4 | 后续多节点 Case | ⬜ 待实现 |
| CTK-C2-1 | PostgreSQL Testcontainer provider | CPRE-04、CTK-C1-5 | ST-021~ST-023 | ⬜ 待实现 |
| CTK-C2-2 | ClusterCoordinatorContract | CTK-C1-4、CTK-C2-1 | CL-006~CL-010 | ⬜ 待实现 |
| CTK-C2-3 | ClusterOutboxContract | CTK-C2-1 | ST-021~ST-023 | ⬜ 待实现 |
| CTK-C2-4 | Shared-store profile fixture | CTK-C2-2~C2-3 | C2 component cases | ⬜ 待实现 |
| CTK-C3-1 | PeerTransportContract | CPRE-05、CTK-C1 | CL-001~CL-005 | ⬜ 待实现 |
| CTK-C3-2 | 独立 JVM ClusterFixture | CTK-C1-5、CTK-C2-4 | CL-020~CL-025 | ⬜ 待实现 |
| CTK-C3-3 | Assignment/Outbox/Peer Probes | CTK-C3-2 | 无 sleep 稳定条件 | ⬜ 待实现 |
| CTK-C3-4 | NetworkFaultController | CTK-C3-2 | CL-003~CL-005、CL-023~CL-025、CL-030 | ⬜ 待实现 |
| CTK-C3-5 | RSocket provider 契约接入 | CTK-C3-1~C3-4 | CL-001~CL-005、CL-020~CL-025 | ⬜ 待实现 |
| CTK-C4-1 | 跨节点 MQTT 语义场景 | CPRE-06、CTK-C3 | CL-026~CL-029 | ⬜ 待实现 |
| CTK-C4-2 | Drain/Rebalance/Rolling Upgrade fixture | CPRE-06、CTK-C3 | CL-032 | ⬜ 待实现 |
| CTK-C4-3 | Compose 部署 smoke | CTK-C4-1~C4-2 | CL-037 | ⬜ 待实现 |
| CTK-C4-4 | Kubernetes 部署 smoke | CTK-C4-1~C4-2 | CL-038 | ⬜ 待实现 |
| CTK-C4-5 | Swarm 部署 smoke | CTK-C4-1~C4-2 | CL-039 | ⬜ 待实现 |
| CTK-C5-1 | ReplicatedLogContract | CPRE-07、CTK-C1 | RF-001~RF-006 | ⬜ 待实现 |
| CTK-C5-2 | ShardStateStoreContract | CPRE-07、基础 Store assertions | RF-004~RF-006 | ⬜ 待实现 |
| CTK-C5-3 | Ratis 多节点 fixture/provider | CTK-C5-1 | RF-001~RF-008 | ⬜ 待实现 |
| CTK-C5-4 | Sharded RocksDB provider | CTK-C5-2 | RF-004~RF-009 | ⬜ 待实现 |
| CTK-C5-5 | ClusterProfileContract | CTK-C2-4、CTK-C5-3~C5-4 | CL-036 | ⬜ 待实现 |
| CTK-C5-6 | 跨 Shard crash matrix | CTK-C5-3~C5-5 | RF-008~RF-009 | ⬜ 待实现 |
| CTK-C6-1 | Network partition/slow peer suite | CTK-C3-4、CTK-C5 | CL-030~CL-031 | ⬜ 待实现 |
| CTK-C6-2 | mTLS 轮换与 identity suite | CTK-C3-2 | CL-033~CL-034 | ⬜ 待实现 |
| CTK-C6-3 | 长稳、容量与资源上限 fixture | CTK-C3、CTK-C5 | NF/cluster production | ⬜ 待实现 |
| CTK-C6-4 | 失败制品与集群诊断包 | CTK-C1-5、CTK-C3-3 | 所有 cluster/recovery | ⬜ 待实现 |

## 4. C1：集群契约与本地模拟

### CTK-C1-1：模块构建与依赖边界

产出：

- 创建 `cluster-testkit/build.gradle.kts` 并加入 `settings.gradle.kts`。
- 应用 `java-library` 和 `java-test-fixtures`。
- `testFixturesApi` 只依赖 `runtime-cluster`、`cluster-protocol` 和基础 testkit fixtures。
- Testcontainers、Reactor Test、JUnit 只进入测试变体。
- 增加 cluster-testkit 不进入 shared/replicated Broker runtimeClasspath 的检查。

示意配置：

```kotlin
dependencies {
    testFixturesApi(project(":runtime-cluster"))
    testFixturesApi(project(":cluster-protocol"))
    testFixturesApi(testFixtures(project(":testkit")))

    testFixturesImplementation(platform(libs.reactor.bom))
    testFixturesImplementation(libs.reactor.test)
    testFixturesImplementation(libs.junit.jupiter)
}
```

验收：`cluster-testkit-test-fixtures.jar` 可被 adapter 测试消费，Broker 生产 runtimeClasspath 不包含 cluster-testkit/Testcontainers/JUnit。

### CTK-C1-2：Cluster wire fixture

文件：

- `wire/ClusterWireBuilders.java`
- `wire/ClusterWireAssertions.java`
- `wire/WireCompatibilityContract.java`
- `wire/TestNodeIdentity.java`

实现 CL-011~CL-013：

- v1 identity/envelope/session/route/control round-trip。
- 新增未知字段由前一兼容版本读取/转发。
- major version、clusterId、capability 不兼容的确定拒绝。
- requestId、deadline、epoch、sequence、trace context 无损。

生成 Protobuf 类型只在 wire fixture 中出现，不进入 runtime-cluster Harness API。

### CTK-C1-3：PartitionResolverContract

文件：

- `contract/PartitionResolverContract.java`
- `fixture/PartitionTestVector.java`

要求：

- 固化 clientId、group+filter、routeKey 的跨版本 hash vectors。
- 验证 partitionCount 边界和非负结果。
- 不使用 `String.hashCode()` 或带进程随机种子的算法。
- partitionCount 变更作为不兼容配置明确失败，而不是重新映射测试数据。

### CTK-C1-4：确定性本地 Peer/Coordinator

文件：

- `fixture/InMemoryPeerTransport.java`
- `fixture/InMemoryClusterCoordinator.java`
- `fixture/DeterministicClusterClock.java`
- `fixture/PeerFrameProbe.java`

能力：

- duplicate、delay、drop、disconnect、stale epoch 和 sequence gap。
- 手工推进 lease/database time 和 assignment generation。
- 不启动真实 socket、数据库或后台心跳线程。
- 仅验证端口语义，不能作为 RSocket/PostgreSQL 契约通过的证据。

### CTK-C1-5：Cluster Fixture SPI 与 Probe

文件：

- `cluster/ClusterFixtureProvider.java`
- `cluster/ClusterFixture.java`
- `cluster/TestClusterNode.java`
- `cluster/TestClusterConfig.java`
- `probe/AssignmentProbe.java`
- `probe/OutboxProbe.java`
- `probe/PeerProbe.java`
- `probe/ClusterReadiness.java`

稳定条件：

1. 所有要求节点 READY。
2. assignment generation 达到目标且各节点观察一致。
3. Outbox pending 达到期望值，通常为 0。
4. 所需 peer handshake 完成。

禁止用固定 sleep 推断稳定。

C1 退出标准：CL-011~CL-014 通过；in-memory fixture 可确定性复现 duplicate/delay/stale epoch；cluster-testkit 无基础设施依赖泄漏。

## 5. C2：Shared-store 控制与状态

### CTK-C2-1：PostgreSQL Fixture Provider

文件：

- `postgres/PostgresClusterFixture.java`
- `postgres/PostgresTestConfig.java`
- `postgres/PostgresDiagnostics.java`

要求：

- 每个测试使用独立 database/schema。
- 等待数据库 health 和 migration 完成。
- 支持暂停连接、终止 session、重启 container 和收集 server log。
- credential 只通过临时 Secret，归档前脱敏。

### CTK-C2-2：ClusterCoordinatorContract

覆盖：

- lease 使用数据库/权威时间，单调续租。
- 双 coordinator 竞争只产生一个 assignment generation。
- epoch 单调，lease 过期不自动授予写权限。
- 旧 node incarnation/epoch 在持久化边界 fencing。
- drain 状态不再获得新 partition。

Provider：

- C1 `InMemoryClusterCoordinator`。
- `cluster-coordination-postgres` 的真实 provider。
- C5 Ratis Metadata provider。

### CTK-C2-3：ClusterOutboxContract

覆盖 ST-021~ST-023：

- Message、Inbound Inflight 和 Outbox 原子提交。
- routeTaskId/deliveryId 重试幂等。
- ack 只在接收端 Delivery commit 后产生。
- assignment 变化后未完成任务按 partition 解析新 Owner。
- commit 结果未知后使用相同 ID 收敛。

Provider：`store-postgres`，C5 增加 sharded RocksDB provider。

### CTK-C2-4：Shared-store Profile Fixture

组装多个独立 Broker JVM + PostgreSQL，不在同一对象图伪造 nodeId/incarnation。提供启动、READY、节点 crash/restart、数据库故障和 diagnostics 接口。

C2 退出标准：PostgreSQL coordinator/store 通过共同契约；双 coordinator、stale epoch、commit 结果未知不产生双 Owner 或重复 Delivery。

## 6. C3：RSocket 数据平面

### CTK-C3-1：PeerTransportContract

覆盖 CL-001~CL-005：

- Handshake identity/version/capability。
- 同 connection sequence 顺序、duplicate 和 gap。
- deadline、迟到响应和相同 requestId 重试。
- Request N/credit、Lease 和有界队列。
- disconnect、half-open、Resume 成功/失败。
- RouteAck 必须发生在业务提交之后。

契约只面向 runtime-cluster Peer port，不暴露 RSocket Payload 或 gRPC stub。

### CTK-C3-2：独立 JVM ClusterFixture

扩展 C2 Fixture：

- 动态分配 MQTT/peer/管理端口。
- 为每个节点生成唯一 nodeId/incarnation 和可达 advertise address。
- 启动前生成配置，启动后通过 readiness API 读取状态。
- 支持 stop、kill、restart 和按节点采集日志/指标/Trace。
- Client 连接任意 Ingress，实际 Channel 始终留在 Ingress。

### CTK-C3-3：Assignment/Outbox/Peer Probe

要求：

- 所有 wait API 有 deadline 和当前集群快照。
- assignment disagreement 输出各节点 generation/epoch/owner。
- Outbox timeout 输出 pending task ID、partition、attempt 和 last error。
- peer timeout 输出 handshake、credit、lane backlog 和最后 sequence。

### CTK-C3-4：NetworkFaultController

文件：

- `fault/NetworkFaultController.java`
- `fault/PeerLink.java`
- `fault/NetworkFault.java`

能力：latency、bandwidth、drop、reset、单向/双向 partition、half-open、slow consumer。实现可使用 Toxiproxy 或受控 peer proxy，但测试 API 不暴露具体库类型。

### CTK-C3-5：RSocket provider 接入

`cluster-transport-rsocket` 提供薄 contract subclass/provider，运行 PeerTransportContract 和 CL-020~CL-025。Adapter 特有测试额外覆盖 frame mapping、mTLS、Lease 和 Resume，不复制公共契约。

C3 退出标准：远程 CONNECT/takeover/QoS 通过；RouteAck 丢失和 Owner 退出可恢复；peer 缓慢/断开时队列有界且不阻塞 I/O EventLoop。

## 7. C4：集群语义与部署

### CTK-C4-1：跨节点 MQTT 语义

使用基础 `Mqtt5ClientFixture` 和相同 MQTT Scenario，覆盖：

- Retain 跨节点写入和 replay（CL-026）。
- Will Owner 切换和 deadline（CL-027）。
- 跨节点共享组唯一消费者选择（CL-028~CL-029）。
- Session takeover、离线队列和恢复回归。

### CTK-C4-2：Drain/Rebalance/Rolling Upgrade

文件：

- `cluster/DrainController.java`
- `cluster/RollingUpgradeFixture.java`

要求：

- drain 后节点不接受新 connection/lane/partition。
- assignment/Leader 转移完成后旧 epoch 才失效和释放资源。
- active TCP 不能透明迁移时观察合法 DISCONNECT。
- 当前版本和前一兼容版本滚动期间无双 Owner 写。

### CTK-C4-3~C4-5：部署 Smoke

| 任务 | 环境 | 验证 |
| --- | --- | --- |
| CTK-C4-3 | Docker Compose | service DNS、动态 incarnation、PostgreSQL health、滚动重启 |
| CTK-C4-4 | Kubernetes | StatefulSet、Headless Service、readiness、preStop、PDB、Secret |
| CTK-C4-5 | Docker Swarm | DNSRR、Task identity、Secret、rolling update、内部 peer 端口 |

部署测试必须复用同一 Cluster Probe 和 MQTT 场景，不为平台建立不同所有权规则。

C4 退出标准：CL-026~CL-029、CL-032、CL-037~CL-039 通过；三个部署环境观察相同 epoch/fencing/Outbox 语义。

## 8. C5：Ratis + sharded RocksDB Profile

### CTK-C5-1：ReplicatedLogContract

覆盖：

- 多数派 commit 后且本地 apply 完成 Mono 才成功。
- minority/旧 Leader 不能提交。
- log replay 与 lastAppliedIndex 单调。
- snapshot create/install 中断恢复。
- learner catch-up、voter change、Leader transfer。

### CTK-C5-2：ShardStateStoreContract

覆盖：

- commandId/index 幂等 apply。
- 一个 WriteBatch 原子应用一个 Shard transition。
- checkpoint/snapshot checksum 和 schema version。
- crash 后日志重放得到相同 RocksDB 状态。
- 不允许跨 Shard 伪装 ACID transaction。

### CTK-C5-3：Ratis 多节点 Fixture

使用独立 JVM/Testcontainers 启动 Metadata/Data groups，支持 kill Leader、暂停 follower、minority partition、磁盘故障、snapshot install 和成员变更。不得使用一个 Ratis server 对象模拟三个生产节点。

### CTK-C5-4：Sharded RocksDB Provider

每节点/Shard 使用独占临时目录，暴露 checkpoint、lastAppliedIndex 和只读 diagnostics，不向契约泄漏 ColumnFamilyHandle。

### CTK-C5-5：ClusterProfileContract

同一 MQTT 行为集分别运行：

1. standalone-memory/rocksdb。
2. cluster shared-store。
3. cluster replicated-store。

断言外部协议结果一致；Profile 差异只允许体现在明确的恢复、可用性和运维边界。

### CTK-C5-6：跨 Shard Crash Matrix

在源 commit、Raft commit、RocksDB apply、Outbox send、目标 apply、RouteAck 各边界终止节点。使用相同 commandId/routeTaskId/deliveryId 重试并验证最终一次生效。

C5 退出标准：RF-001~RF-009 和 CL-036 通过；失去多数派不成功 ACK；snapshot/replay 后状态确定一致。

## 9. C6：生产验收辅助

### CTK-C6-1：Network Partition/Slow Peer Suite

组合执行单向/双向分区、延迟、丢包、half-open、慢 peer 和队列满。断言 fencing、入口背压、内存上限、Outbox 保留和恢复收敛。

### CTK-C6-2：mTLS 轮换与 Identity

提供 clusterId/nodeId 正确、错误、过期和轮换证书。验证既有连接、新连接、Handshake identity 和不允许匿名降级。

### CTK-C6-3：长稳与容量 Fixture

支持固定随机种子、Payload/QoS/连接数分布、运行时长和验收阈值。收集 peer latency、lane backlog、Outbox、fencing、Raft、RocksDB apply/snapshot 和 EventLoop delay 指标。

### CTK-C6-4：失败制品

每个失败归档：

- Case ID 和随机种子。
- 所有节点配置、版本、nodeId/incarnation。
- assignment generation/epoch 和 ClusterView。
- pending Outbox、peer lane、Raft term/index 和 snapshot 状态。
- 节点/数据库/代理日志及时间线 Trace。

归档前删除 credential、Token、私钥、Authentication Data 和 Payload。

C6 退出标准：CL-030~CL-034 可重复；长稳失败能定位到节点、partition、epoch、requestId/messageId；所有队列和 executor 有硬上限及指标。

## 10. Provider 所有权

| 拥有模块 | Provider/Contract subclass | 禁止泄漏 |
| --- | --- | --- |
| `runtime-cluster` | PartitionResolver、本地 Peer/Coordinator provider | RSocket、SQL、Ratis 类型 |
| `cluster-transport-rsocket` | RSocket Peer provider | RSocket Payload 进入 contract API |
| `cluster-coordination-postgres` | PostgreSQL Coordinator provider | SQL row/client 进入 contract API |
| `store-postgres` | BrokerStore provider | connection/transaction handle |
| `cluster-outbox-postgres` | ClusterOutbox provider | SQL row/client 进入 contract API |
| `cluster-consensus-ratis` | ReplicatedLog/Metadata provider | Ratis Message 进入 runtime API |
| `store-rocksdb-sharded` | ShardStateStore/Outbox provider | RocksDB handle/Column Family |
| `broker` | shared/replicated ClusterFixture launcher | 第二个生产入口 |

## 11. 任务依赖图

```text
C0 runtime 拆分
  `-- CTK-C1-1 构建
      +-- CTK-C1-2 Wire Contract
      +-- CTK-C1-3 Partition Contract
      +-- CTK-C1-4 In-memory Peer/Coordinator
      `-- CTK-C1-5 Cluster Fixture SPI
              |
              +-- CTK-C2-1 PostgreSQL Fixture
              |   +-- CTK-C2-2 Coordinator Contract
              |   `-- CTK-C2-3 Outbox Contract
              |           `-- CTK-C2-4 Shared Profile
              |
              `-- CTK-C3-1 Peer Contract
                  +-- CTK-C3-2 JVM Cluster Fixture
                  +-- CTK-C3-3 Cluster Probes
                  +-- CTK-C3-4 Network Faults
                  `-- CTK-C3-5 RSocket Provider
                         |
                         `-- CTK-C4 MQTT Semantics/Drain/Deploy

CTK-C1 + Ratis/Rocks ports
  +-- CTK-C5-1 ReplicatedLog Contract
  +-- CTK-C5-2 ShardStateStore Contract
  |       +-- CTK-C5-3 Ratis Fixture
  |       `-- CTK-C5-4 Rocks Provider
  +-- CTK-C5-5 Profile Contract
  `-- CTK-C5-6 Cross-Shard Crash Matrix

CTK-C3 + CTK-C5
  `-- CTK-C6 network/TLS/long-run/diagnostics
```

## 12. 验证命令

模块落地后提供以下任务：

```bash
./gradlew :cluster-testkit:test :cluster-testkit:testFixturesJar
./gradlew :runtime-cluster:contractTest
./gradlew :cluster-coordination-postgres:contractTest
./gradlew :store-postgres:contractTest
./gradlew :cluster-outbox-postgres:contractTest
./gradlew :cluster-transport-rsocket:contractTest
./gradlew :cluster-consensus-ratis:contractTest
./gradlew :store-rocksdb-sharded:contractTest
./gradlew clusterTest
```

部署 smoke、长稳和完整 network partition matrix 可以作为独立 CI/nightly 任务，但 release 前必须全部通过。

## 13. 完成定义

1. 六套契约均至少有一个真实实现 provider；默认 RSocket、PostgreSQL 和 replicated profile 有故障测试。
2. 所有多节点 production integration/recovery case 使用独立 JVM 或 Testcontainers。
3. 任何稳定等待都基于 readiness、generation、Outbox 或确定性 Probe，不使用固定 sleep。
4. adapter-specific 类型不进入 contract API。
5. duplicate、delay、disconnect、stale epoch、Leader loss、minority partition、snapshot install、drain 和 restart 均有自动化。
6. standalone、shared-store、replicated-store 通过同一 MQTT 行为集。
7. cluster-testkit/Testcontainers/JUnit 不进入任何生产 runtimeClasspath。
