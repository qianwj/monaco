# Monaco Testkit 实现方案

> 状态：Implementing（TK-P0 基础能力）
>
> 更新日期：2026-07-26
>
> 目标模块：`testkit`

## 1. 目标

testkit 为 Monaco 各模块提供可复用、确定性、与生产运行时隔离的测试能力。它不是测试业务逻辑的第二套实现，也不是生产应用的启动入口。

必须提供以下能力：

1. 使用同一套 `BrokerStoreContract` 验证单机 Memory/RocksDB Store；`cluster-testkit` 用扩展契约验证 PostgreSQL 和 sharded RocksDB。
2. 直接驱动 `BrokerEngine` 的协议场景 DSL，验证事务、ACK、事件和并发顺序。
3. 启动真实 Broker 的夹具，使用端口 `0` 并等待 `READY` 后返回。
4. MQTT 5 客户端和原始线协议客户端，分别覆盖合法互操作和非法报文。
5. 可控时钟、调度器、ID、出站端口及故障注入，测试中不依赖 `sleep`。
6. `PacketAssertions` 和异步 Probe，失败时输出报文、状态和事件轨迹。
7. P3 提供 `PluginHarness`；集群阶段由独立 `cluster-testkit` 复用基础能力并提供 Peer、Coordination、Outbox 和故障契约。

非目标：

- 不在 testkit 中复制 Topic Matcher、QoS 状态机或 Broker 路由逻辑。
- 不通过反射访问 core 私有状态来断言正确性。
- 不把 Testcontainers、JUnit、客户端库带入生产 `runtimeClasspath`。
- 不为测试增加生产专用的业务分支；可测试性通过公开端口、Clock、Scheduler 和 Probe 实现。
- 不用 EventBus、固定端口或基于时间猜测的等待代替确定性状态判断。

## 2. 当前基线与待统一项

当前 `testkit/` 已完成 Gradle test fixtures 隔离，以及确定性 Clock/Scheduler/ID、Probe/Trace、FaultPlan/Gate 和公共断言的第一批实现与自测试。`protocol` 的 P0 值对象、报文、属性、Topic Validator/Matcher 已有实现，Property Schema 和 QoS/AUTH 状态机仍待后续阶段完成；Core 的 Clock/Scheduler/ID、BrokerStore 和出站端口尚未冻结。因此现有基础类暂不实现 Core 接口，待生产端口落地后机械适配；Recording、Store Fixture/Contract 和故障端口包装器继续按阶段实施，不在 testkit 中预定义生产占位接口。

实现前需要统一三处文档差异：

| 编号 | 差异 | 本方案处理 |
| --- | --- | --- |
| D-01 | 仓库指南和 `mqtt5-architecture.md` 写 Java 21，当前新模块构建脚本写 Java 25 | testkit 不硬编码独立版本，统一继承根构建约定；P0 开始前由项目级决策确定 toolchain |
| D-02 | `plugin-system.md` 示例使用 Vert.x `Future`/`Vertx`，新架构公共异步端口要求 Reactor `Mono`/`Flux` 且生产运行时移除 Vert.x | `PluginHarness` 按 plugin-api 最终冻结的 Reactor API 实现；冻结前不提交兼容 Vert.x 的双栈测试 API |
| D-03 | `core` 是正式领域模块名；客户端接入合并到 `runtime-reactor/transport.netty` | testkit 只为 `runtime-reactor` 提供一套 Engine/Transport Harness；旧 transport 模块删除后不保留双套 Harness |

以上差异不阻塞 P0 的 Store Contract、Clock、Probe 和断言工具设计。

## 3. 设计原则

### 3.1 测试变体隔离

可复用类型全部放在 `testkit/src/testFixtures/java`，通过 `java-test-fixtures` 发布。`testkit/src/main/java` 保持为空，除非未来需要一个不含任何测试库的公共测试协议 artifact。

生产模块只能在测试配置中引用 testkit：

```kotlin
dependencies {
    testImplementation(testFixtures(project(":testkit")))
}
```

CI 增加依赖检查：任意模块的 `runtimeClasspath` 和发布 POM 都不能出现 `testkit`、JUnit、Reactor Test、HiveMQ Client 或 Testcontainers。

### 3.2 依赖反转

testkit 定义 Harness、Contract 和测试侧 SPI，不实例化具体生产适配器。被测试模块在自己的 `src/test` 或 `src/testFixtures` 中提供实现：

```text
testkit BrokerStoreContract <--- MemoryStoreContractTest
                            <--- RocksStoreContractTest

testkit BrokerTestFixture  <--- broker testFixtures: InProcessBrokerLauncher

testkit PluginHarness      <--- plugin-runtime test: LocalPluginHarnessFactory
                            <--- plugin-remote-grpc test: GrpcPluginHarnessFactory
```

因此 `testkit` 不需要反向依赖 `store-memory`、`store-rocksdb`、`broker` 或插件运行时，避免测试依赖环，也保证契约没有针对参考实现的特殊分支。

### 3.3 黑盒优先、端口可观测

- protocol 测试只观察输入、返回值、异常和纯状态转换。
- core 组件测试通过公开端口注入 Recording/Faulting 实现，观察 Store commit、`ConnectionSink` 和 `DomainEventSink`。
- Broker 端到端测试只通过 Listener、健康状态和持久目录观察行为。
- Cluster 测试只通过 assignment、epoch、Outbox、peer frame 和客户端结果观察行为。

不直接读取实现类的私有 Map、Netty Channel 或 RocksDB 内部句柄。

### 3.4 确定性

- 时间语义使用 `MutableBrokerClock` 和 `ManualBrokerScheduler.advanceBy`。
- ID 使用 `SequenceIdGenerator`，保证 Message/Delivery/Event ID 可预测。
- 异步结果使用 `StepVerifier`、`PacketProbe`、`EventProbe` 或 readiness probe。
- 并发边界使用 `Gate` 暂停和释放 Mono，不通过线程调度概率制造竞态。
- 网络和进程测试允许真实 deadline，但必须有有界超时和完整诊断。

## 4. 分层架构

```text
JUnit tests
   |
   +-- Store contracts -------- BrokerStore + StoreFixtureProvider
   +-- Core scenarios --------- BrokerEngine + fake outbound ports
   +-- Broker fixture --------- BrokerLauncher + real TCP/TLS/WS
   +-- Plugin harness --------- PluginRuntimeHarnessProvider
   `-- cluster-testkit -------- Peer/Coordination/Outbox/Profile contracts
              |
              v
       clock / scheduler / probes / fault plan / assertions
```

建议包结构：

```text
cn.elvis.monaco.testkit
├── assertion
│   ├── PacketAssertions
│   ├── StateAssertions
│   └── TraceAssertions
├── broker
│   ├── BrokerLauncher
│   ├── BrokerTestFixture
│   ├── RunningBroker
│   ├── TestBrokerConfig
│   └── BrokerReadiness
├── client
│   ├── Mqtt5ClientFixture
│   ├── TestClientConfig
│   ├── ReceivedPublish
│   └── RawMqtt5Client
├── engine
│   ├── BrokerEngineHarness
│   ├── BrokerScenario
│   ├── PacketBuilders
│   └── ScenarioResult
├── fault
│   ├── FaultPlan
│   ├── FaultPoint
│   ├── Gate
│   └── FaultInjectingBrokerStore
├── port
│   ├── RecordingConnectionSink
│   ├── RecordingDomainEventSink
│   ├── RecordingTelemetry
│   ├── StubAuthenticator
│   └── StubAuthorizer
├── probe
│   ├── EventProbe
│   ├── PacketProbe
│   ├── StateProbe
│   └── TraceProbe
├── store
│   ├── BrokerStoreContract
│   ├── StoreFixture
│   ├── StoreFixtureProvider
│   └── StoreCapabilities
├── time
│   ├── MutableBrokerClock
│   ├── ManualBrokerScheduler
│   └── SequenceIdGenerator
└── plugin                 # P3
    ├── PluginHarness
    ├── PluginHarnessProvider
    └── PluginInvokerContract
```

包名表示稳定测试职责，不要求第一阶段创建空类占位。

集群能力放在单独的 `cluster-testkit/src/testFixtures/java/cn/elvis/monaco/cluster/testkit`：

```text
cluster-testkit
├── ClusterFixture
├── TestClusterNode
├── PeerTransportContract
├── ClusterCoordinatorContract
├── ClusterOutboxContract
├── ReplicatedLogContract
├── ShardStateStoreContract
├── ClusterProfileContract
├── AssignmentProbe
├── OutboxProbe
└── NetworkFaultController
```

`cluster-testkit` 依赖 `cluster-runtime`、`cluster-protocol` 和基础 `testkit`；基础 testkit 不反向依赖任何集群模块。

C1 的 in-memory coordinator/peer 只验证确定性路由与端口语义。PostgreSQL、RSocket 和 Ratis 的多节点 integration/recovery case 必须使用独立 JVM 或 Testcontainers，不能在同一对象图中伪造多个生产节点身份。

## 5. 核心 API

以下签名是设计约束，落地时可以随 core 最终 record 字段做机械调整，但不能改变依赖方向和生命周期语义。

### 5.1 Store 契约

```java
public interface StoreFixtureProvider {
    String name();

    StoreCapabilities capabilities();

    StoreFixture create(StoreTestDirectory directory);
}

public interface StoreFixture extends AutoCloseable {
    BrokerStore store();

    BrokerStore restart();

    void crash();

    @Override
    void close();
}
```

`restart()` 保留数据并创建新 Store 实例；`crash()` 模拟未调用正常 `close()` 的进程终止。Memory Store 可以声明 `persistentRestart=false`，契约使用 JUnit assumption 跳过仅适用于持久 Store 的 case，但原子性、幂等性和关闭语义不能跳过。

```java
public record StoreCapabilities(
        boolean persistentRestart,
        boolean schemaMigration,
        boolean concurrentTransactions) {
}
```

`BrokerStoreContract` 是包含 `@Test`/`@TestFactory` 的抽象基类：

```java
public abstract class BrokerStoreContract {
    protected abstract StoreFixtureProvider provider();

    @Test
    final void transactionCommitsAllEntitiesAtomically() { /* shared case */ }
}
```

各单机 Store 只需提供薄子类：

```java
final class MemoryBrokerStoreContractTest extends BrokerStoreContract {
    @Override
    protected StoreFixtureProvider provider() {
        return MemoryStoreFixtureProvider.INSTANCE;
    }
}
```

契约禁止使用具体 Store 类型做 `instanceof`，差异只能通过显式 capability 表达。

### 5.2 确定性时间和调度

```java
public final class MutableBrokerClock implements BrokerClock {
    public static MutableBrokerClock startingAt(Instant instant);
    public Instant now();
    public void advance(Duration duration);
}

public final class ManualBrokerScheduler implements BrokerScheduler {
    public void schedule(String key, Duration delay, Supplier<Mono<Void>> action);
    public boolean cancel(String key);
    public int pendingTaskCount();
    public Mono<Void> advanceBy(Duration duration);
    public Mono<Void> runDueTasks();
}
```

规则：

- 同一 timer key 重复调度时替换旧任务。
- 到期时间相同的任务按注册序号执行。
- `advanceBy` 先推进 Clock，再依次执行到期任务，直到没有新的同一时刻任务。
- action 失败必须由 `advanceBy` 返回，不能吞掉错误。
- 提供最大级联任务数，防止错误任务在同一时刻无限重调度。

### 5.3 Probe 与统一轨迹

所有 Recording Port 可写入同一个 `TraceProbe`：

```java
public sealed interface TraceEntry {
    Instant at();
    long sequence();
}

public record StoreCommitted(/* transaction id */) implements TraceEntry {}
public record PacketSent(ConnectionId id, ServerPacket packet) implements TraceEntry {}
public record ConnectionClosed(ConnectionId id, ReasonCode reason) implements TraceEntry {}
public record EventPublished(DomainEvent event) implements TraceEntry {}
```

典型断言：

```java
trace.assertOrder(
    entry(StoreCommitted.class),
    packet(PUBACK, ReasonCode.SUCCESS),
    entry(EventPublished.class)
);
```

Probe 必须支持：

- `awaitCount(int, Duration)` 和 `awaitMatching(Predicate, Duration)`。
- 超时时输出当前快照、最后一次错误和未满足条件。
- 线程安全快照，不暴露可变内部列表。
- `assertNoEvent(Duration)` 只用于真实 I/O 边界；确定性组件优先检查 scheduler/probe 当前状态。

### 5.4 Core 场景 Harness

```java
public final class BrokerEngineHarness implements AutoCloseable {
    public static Builder builder(BrokerEngineHarnessProvider provider);

    public TestConnection open(String connectionName);
    public Mono<Void> receive(TestConnection connection, ClientPacket packet);
    public Mono<Void> close(TestConnection connection, DisconnectCause cause);
    public ScenarioSnapshot snapshot();

    public MutableBrokerClock clock();
    public ManualBrokerScheduler scheduler();
    public TraceProbe trace();
}
```

Builder 默认创建允许认证授权、Recording Ports 和确定性 ID；`BrokerEngine` 与 `BrokerStore` 由 provider 装配，不能由 testkit 反向构造 core 私有实现。core 的组件测试 provider 可选择 Memory Store，故障测试可以替换为 Faulting Store。Harness 不在方法内部调用 `block()`，测试通过 `StepVerifier` 或 `verifyComplete()` 决定等待边界。

DSL 只压缩样板代码，不隐藏 MQTT 报文和期望结果：

```java
scenario(harness)
    .opened("subscriber")
    .receives("subscriber", connect("client-b").cleanStart(false))
    .expects("subscriber", connAck().sessionPresent(false))
    .receives("subscriber", subscribe(1, "sensor/+/temp", QoS.AT_LEAST_ONCE))
    .expects("subscriber", subAck(1, ReasonCode.GRANTED_QOS_1))
    .run();
```

不提供把多步协议压成 `publishAndAssertSuccess()` 的黑盒捷径；ACK、Reason Code、DUP、Packet ID 和事件顺序必须在 case 中可见。

### 5.5 Broker 夹具

为保持 testkit 不依赖 `broker`，启动能力使用测试侧 SPI：

```java
public interface BrokerLauncher {
    Mono<RunningBroker> start(TestBrokerConfig config, TestServices services);
}

public interface RunningBroker extends AutoCloseable {
    BrokerAddress tcpAddress();
    Optional<URI> websocketUri();
    BrokerReadiness readiness();
    Mono<BrokerReadiness> awaitReady(Duration timeout);
    Mono<Void> stop();
    BrokerDiagnostics diagnostics();
}
```

`BrokerReadiness` 是 testkit 自有的生命周期枚举（`STARTING`、`READY`、`DEGRADED`、`STOPPING`、`STOPPED`），launcher 负责从生产 `HealthStatus` 映射，testkit 不直接依赖 broker 类型。Fixture 默认只接受 `READY`；专门验证降级启动的 case 可显式把 `DEGRADED` 配置为期望终态。

`broker` 模块在自己的 test fixtures 中提供 `InProcessBrokerLauncher`，使用真实 Composition Root 装配 transport、Memory Store、默认安全和插件运行时。testkit 的 `BrokerTestFixture` 只负责：

1. 强制 Listener 请求端口 `0`。
2. 调用 launcher 并等待健康状态 `READY`。
3. 读取实际 TCP/WS 监听地址。
4. 注册客户端和临时资源，按逆序关闭。
5. 启动失败或超时时附带生命周期轨迹和监听日志。

`TestBrokerConfig` 默认值：

| 配置 | 默认值 |
| --- | --- |
| TCP | enabled，port `0` |
| WebSocket | disabled，port `0` |
| TLS | disabled |
| Store | memory |
| Auth | anonymous + allow-all |
| Clock/Scheduler | 测试注入 |
| Shutdown timeout | 5 秒 |
| Client limits | 使用生产默认值，可按 case 覆盖 |

恢复测试另用 `ForkedBrokerFixture` 或 `BrokerContainerFixture`。只有独立进程才能验证真实 crash；进程内 `stop()` 只能验证 graceful restart。

### 5.6 MQTT 客户端夹具

客户端分成两类，不能用一种抽象混合：

1. `Mqtt5ClientFixture` 包装成熟 MQTT 5 客户端，负责合法 CONNECT/PUBLISH/SUBSCRIBE、自动重连开关、收到报文 Probe 和资源释放。建议以 HiveMQ MQTT Client 作为自动化主客户端。
2. `RawMqtt5Client` 直接发送确定字节，覆盖重复单值属性、非法 UTF-8、非法固定头、畸形 Remaining Length、CONNECT 前报文等成熟客户端拒绝构造的输入。

Paho、HiveMQ Client 和 `mosquitto_pub/sub -V mqttv5` 的互操作 case 保留各自原生调用，不把兼容性差异隐藏在统一 wrapper 中。

### 5.7 故障注入

`FaultPlan` 面向端口边界，不修改生产领域代码：

```java
FaultPlan plan = FaultPlan.builder()
    .pauseOnce(FaultPoint.STORE_BEFORE_COMMIT, gate)
    .failOnce(FaultPoint.CONNECTION_SEND, new IOException("reset"))
    .build();
```

首批故障点：

- `STORE_BEFORE_OPERATION`、`STORE_BEFORE_COMMIT`、`STORE_AFTER_COMMIT`。
- `SINK_BEFORE_SEND`、`SINK_AFTER_SEND`、`SINK_CLOSE`。
- `EVENT_PUBLISH`、`AUTHENTICATE`、`AUTHORIZE`。
- `SCHEDULER_BEFORE_ACTION`。

`STORE_AFTER_COMMIT` 表示提交已成功但调用方得到错误，用于验证幂等恢复；它不能伪造回滚。网络和集群阶段使用 TCP proxy/Testcontainers Toxiproxy 或 peer transport fake 注入延迟、断连和半开。

## 6. Gradle 方案

P0 最小依赖：

```kotlin
plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    testFixturesApi(project(":protocol"))
    testFixturesApi(project(":core"))

    testFixturesImplementation(platform(libs.reactor.bom))
    testFixturesImplementation(libs.reactor.test)
    testFixturesImplementation(libs.junit.jupiter)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

注意：

- 当前 `api(project(":protocol"))` 和 `api(project(":core"))` 转入 test fixtures 配置；删除 `implementation(project(":store-memory"))`，具体 Store 由消费者侧 provider 注入。
- `java-test-fixtures` 会让本项目的 `test` source set 自动看到 `testFixtures`；testkit 不添加对自身 project 的依赖。
- 客户端、Testcontainers、Awaitility 等新增库只放入 `testFixturesImplementation`。
- `store-memory` 和 `store-rocksdb` 分别在测试配置消费 testkit，不由 testkit 反向依赖。
- 插件和 Broker 的具体 launcher/provider 放在拥有模块的测试代码中。
- P3 增加 `testFixturesApi(project(":plugin-api"))`。
- C1 创建独立 `cluster-testkit`，其 test fixtures 依赖 `cluster-runtime`、`cluster-protocol` 和 `testFixtures(project(":testkit"))`；基础 testkit 不增加集群依赖。

建议测试任务：

| 任务 | 内容 | 默认进入 `check` |
| --- | --- | --- |
| `test` | 快速单元测试 | 是 |
| `componentTest` | core + Memory Store、Transport 组件 | 是 |
| `contractTest` | 基础 testkit 的 Store/Plugin 契约 | 是，外部依赖可按 capability/tag 分离 |
| `integrationTest` | 真实 TCP/TLS/WS、RocksDB 重启 | CI 是，本地可单独运行 |
| `interopTest` | Paho/HiveMQ/Mosquitto | nightly/release |
| `clusterTest` | `cluster-testkit` 中的 Peer/Store/Profile 契约、多节点网络和故障 | 集群阶段的 CI/nightly |

JUnit tags 固定为：`unit`、`component`、`contract`、`integration`、`recovery`、`interop`、`cluster`、`slow`。不能只靠类名决定 CI 分组。

## 7. 生命周期与资源管理

所有 fixture 实现 `AutoCloseable`，并遵循以下顺序：

```text
创建临时目录
  -> 创建 Store/Plugin runtime
  -> 创建 Broker core
  -> bind Listener
  -> READY
  -> 创建测试客户端
  -> 执行 case
  -> 关闭客户端
  -> 停止 Listener
  -> drain core/plugin
  -> 关闭 Store
  -> 删除临时目录
```

规则：

- 启动中途失败也按已完成阶段逆序清理。
- 测试失败时先采集 diagnostics，再清理。
- 临时目录由 JUnit `@TempDir` 或 testkit 的显式目录对象提供，禁止写固定共享路径。
- 不直接调用 `System.exit` 测 crash；使用受控子进程。
- 测试线程、Scheduler、EventLoop 和客户端 executor 必须在 `close()` 后终止。

## 8. 自测试要求

testkit 自身必须测试：

1. Manual Clock 前进、禁止倒退及边界溢出。
2. Scheduler 替换/cancel/同刻顺序/级联任务/失败传播。
3. Probe 并发写入、超时诊断和不可变快照。
4. FaultPlan 的 once/always/nth-match 触发语义。
5. Fixture 在正常、启动失败和测试异常时都逆序清理。
6. 动态端口并行启动无冲突。
7. `runtimeClasspath` 不包含 testkit 和测试库。

## 9. 分阶段实施

### TK-P0：确定性基础与 Store 骨架

产出：

- 调整 `testkit/build.gradle.kts` 到 test fixtures 依赖。
- `MutableBrokerClock`、`ManualBrokerScheduler`、`SequenceIdGenerator`。
- Probe、Recording Port、PacketAssertions。
- `StoreFixtureProvider` 和第一版 `BrokerStoreContract`。
- testkit 自测试，Memory Store 接入契约。

退出标准：testkit 不进入生产 runtime，Memory Store 通过基础事务契约，所有确定性工具无 `sleep`。

### TK-P1：Core DSL 与单机 E2E

产出：

- `BrokerEngineHarness`、Packet Builders、显式场景 DSL。
- `BrokerLauncher`、`BrokerTestFixture`、`TestBrokerConfig`。
- `Mqtt5ClientFixture` 和 `RawMqtt5Client`。
- TCP/WS 使用同一场景模板；覆盖 CONNECT、PING、QoS 0/1 和 SUBSCRIBE。

退出标准：端口 `0` 并行测试稳定，fixture 只在 READY 后返回，persist-before-ACK 可通过统一轨迹断言。

### TK-P2：持久 Store 与恢复

产出：

- 完整 Store Contract：Session、Subscription、Message、Delivery、Inflight、Retain、Will、原子性和迁移。
- `ForkedBrokerFixture`、RocksDB 临时目录和 crash/restart 控制。
- QoS 1/2 每个中间状态恢复场景。

退出标准：Memory/RocksDB 共享适用契约全部通过；持久能力差异只通过 capability 跳过；QoS 2 恢复不重复交付。

### TK-P3：完整 MQTT 5 与插件

产出：

- AUTH、Retain、Will、Alias、订阅选项的 Builders/Assertions。
- `PluginHarness` 和 `PluginInvokerContract`。
- 本地/远程插件一致性、ClassLoader、超时、事件队列测试。
- HiveMQ/Paho/Mosquitto 互操作任务。

退出标准：技术方案中的必测场景全部自动化，插件失败不改变已提交状态和 QoS 保证。

### CTK-C1~C6：独立 Cluster Testkit

只在集群模块进入构建后创建 `cluster-testkit`：

- C1：wire compatibility、本地 coordinator/peer fixture、PartitionResolver 和确定性多节点逻辑。
- C2：`ClusterCoordinatorContract`、`ClusterOutboxContract`、PostgreSQL Testcontainer 和 fencing。
- C3：`PeerTransportContract`、多节点 `ClusterFixture`、RSocket/mTLS、assignment/readiness/Outbox Probe。
- C4：共享订阅、Retain、Will、drain、滚动升级和部署 smoke test。
- C5：`ReplicatedLogContract`、`ShardStateStoreContract`、Raft commit/apply/snapshot 故障矩阵。
- C6：网络分区、磁盘满、长稳、容量和证书轮换。

退出标准：测试不使用 `sleep` 判断稳定；相同 Peer 契约可验证 RSocket 和 optional gRPC；shared-store 与 replicated-store 通过同一 `ClusterProfileContract` 和 MQTT 行为集；旧 epoch 在持久化边界必然被拒绝。

## 10. 验收标准

1. 所有生产模块的 `runtimeClasspath` 均不含 testkit 或测试依赖。
2. 单机 Store 复用同一个 `BrokerStoreContract`；集群 Store 复用对应 Cluster 契约，所有契约中都没有具体适配器判断。
3. Core 场景能确定性证明 Store commit 发生在 ACK/RouteAck/Domain Event 之前。
4. 测试 Broker 使用端口 `0`，且 fixture 返回时状态已经为 `READY`。
5. Clock/Scheduler 驱动 Keep Alive、Expiry 和 Will case，不使用 `Thread.sleep`。
6. 合法 MQTT 流量和非法 wire 报文使用不同客户端夹具。
7. Fixture 在成功、失败、取消和超时时都释放线程、端口、Store 和临时目录。
8. 单机 `testkit` 不编译依赖 Cluster 模块；集群能力只从 `cluster-testkit` 发布。
9. Plugin/Peer 的本地与远程实现通过同一语义契约。
10. 每个公开 testkit API 至少有一个自测试和一个真实消费者测试。

## 11. 风险与控制

| 风险 | 影响 | 控制 |
| --- | --- | --- |
| DSL 隐藏协议细节 | case 可读但无法发现 Reason Code、DUP 或顺序错误 | DSL 保持报文显式，复杂组合用参数化数据而非一键 helper |
| 抽象契约只适配 Memory Store | RocksDB/PostgreSQL 被迫特殊处理 | capability 只表达真实能力；共同语义不得跳过 |
| 进程内 restart 被误当 crash | 未验证 WAL/恢复语义 | crash case 必须使用子进程或容器 |
| 使用真实时间导致 flaky | Will、Expiry、timeout 偶发失败 | 组件测试使用虚拟时间；网络测试使用状态 Probe 和有界 deadline |
| test fixtures 依赖泄漏 | 增大生产包并破坏边界 | CI 检查 runtimeClasspath 和发布 POM |
| 并发测试依赖随机调度 | 竞态无法稳定复现 | Gate、Barrier 和统一 Trace 控制交错顺序 |
| 单机/集群 API 名称继续分叉 | 两个 testkit 形成两套 MQTT DSL | `cluster-testkit` 依赖并复用基础 packet/scenario/probe，只新增所有权与网络控制 |
