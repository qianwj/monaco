# testkit 模块实现任务

> 模块：`testkit`
>
> 基础包：`cn.elvis.monaco.testkit`
>
> 当前状态：TK-P0 基础能力实现中
>
> 生产依赖：无；所有可复用能力从 Gradle `testFixtures` 变体发布
>
> 关联设计：[Testkit 实现方案](../tests/testkit-implementation.md)、[测试 Case](../tests/test-cases.md)、[模块详细设计 §2.12](../mqtt5-module-design.md#212-testkit-模块)

## 1. 目标与边界

testkit 为单机 Broker、Store、Core、Transport 和 Plugin 测试提供公共夹具。它不实现 MQTT 业务规则，不依赖具体 Store/Broker/Plugin Runtime，也不能进入任何生产 `runtimeClasspath`。

本任务文档覆盖 TK-P0 至 TK-P4。集群能力由独立的 [cluster-testkit 任务文档](cluster-testkit.md) 管理。

交付约束：

1. 公共测试类型全部位于 `testkit/src/testFixtures/java`。
2. `testkit/src/main/java` 保持为空。
3. 具体适配器通过 provider/launcher 接入，testkit 不反向依赖 `store-*`、`broker` 或 `plugin-runtime`。
4. 组件测试使用确定性 Clock/Scheduler/Gate，不使用 `Thread.sleep` 制造时序。
5. 每个任务必须同时提交自测试或真实消费者测试。

## 2. 开始条件

| 编号 | 条件 | 影响范围 | 状态 |
| --- | --- | --- | --- |
| PRE-01 | 项目统一 Java 21 或 Java 25 toolchain，testkit 继承根约定 | 全阶段 | ⬜ 待决策 |
| PRE-02 | core 的 `BrokerClock`、`BrokerScheduler`、`IdGenerator`、`BrokerStore` 端口冻结 | TK-P0 | ✅ 已实现 |
| PRE-03 | `BrokerEngine`、Connection/Domain Event 等端口冻结 | TK-P1 | ⬜ 待实现 |
| PRE-04 | broker 暴露可测试的生命周期和实际监听地址 | TK-P1 | ⬜ 待实现 |
| PRE-05 | QoS 2、恢复模型和 RocksDB Store 端口冻结 | TK-P2 | ⬜ 待实现 |
| PRE-06 | plugin-api 异步模型统一为 Reactor，Hook/Decision DTO 冻结 | TK-P3 | ⬜ 待决策 |

PRE-01 和 PRE-06 是项目级决策，不在 testkit 内建立兼容双栈。其他前置可以与对应阶段并行开发，但 provider 任务必须等待生产端口可编译。

当前实现说明：Core 已提供 `BrokerClock`、`BrokerScheduler`、`IdGenerator`、原子 `BrokerStore`、`ConnectionSink`、`MutationBatch` 和状态 record。确定性时间能力已适配正式端口，Store/Connection recording 与故障包装器也已落地。认证、授权、Domain Event 和 Telemetry 端口仍未定义，因此对应 Stub/Recording 继续等待生产契约。当前 `BrokerStore.load(clientId)` 只返回 Session/Subscription/Inflight/Will，无法观察 Retain/Pending Message；旧 ST-001~ST-003 中的 `recover()` 和 transaction callback 也已不属于新 API，必须先修订 Case 后再实现共同契约。

## 3. 任务总览

| 任务 | 产出 | 前置 | 主要 Case | 状态 |
| --- | --- | --- | --- | --- |
| TK-P0-1 | Gradle test fixtures 与依赖边界 | PRE-01 | TK-014 | ✅ 已完成 |
| TK-P0-2 | 确定性 Clock、Scheduler、ID | PRE-02 | TK-001~TK-006 | ✅ 已完成 |
| TK-P0-3 | Probe 与统一 Trace | TK-P0-2 | TK-007~TK-008 | ✅ 已完成 |
| TK-P0-4 | Recording/Stub 出站端口 | PRE-02、TK-P0-3 | CO-010~CO-011、CO-036 | 🟨 Connection/Store 完成，其余端口待定义 |
| TK-P0-5 | FaultPlan、Gate 和故障包装器 | PRE-02、TK-P0-3 | TK-009、ST-003~ST-004 | ✅ 已完成 |
| TK-P0-6 | Packet/State/Trace Assertions | protocol P0 | TK-013 | ✅ 已完成 |
| TK-P0-7 | Store Fixture SPI | PRE-02 | ST-001~ST-004 | ✅ 已完成 |
| TK-P0-8 | BrokerStoreContract 基线 | TK-P0-5~TK-P0-7 | ST-001~ST-004 | ⬜ 等待 Store Case/可观察面统一 |
| TK-P0-9 | Fixture 清理与依赖自检 | TK-P0-1~TK-P0-8 | TK-010、TK-014 | 🟨 清理/依赖自检完成，泄漏审计待实现 |
| TK-P1-1 | Packet Builders | protocol packet/property | CO/E2E P0-P1 | ✅ 已完成 |
| TK-P1-2 | BrokerEngineHarnessProvider 与 Harness | PRE-03、TK-P0 | CO-001~CO-036 | ⬜ 待实现 |
| TK-P1-3 | 显式 Broker Scenario DSL | TK-P1-1~TK-P1-2 | CO-001~CO-087 | ⬜ 待实现 |
| TK-P1-4 | Store Contract P1 扩展 | TK-P0-8、Store 模型 | ST-005~ST-010 | ⬜ 待实现 |
| TK-P1-5 | BrokerLauncher/RunningBroker SPI | PRE-04、TK-P0-3 | BR-001~BR-008 | ⬜ 待实现 |
| TK-P1-6 | BrokerTestFixture 与动态端口 | TK-P1-5 | TK-011~TK-012、TR-001~TR-002 | ⬜ 待实现 |
| TK-P1-7 | 合法 MQTT 5 Client Fixture | TK-P1-6 | E2E-001~E2E-003、E2E-013 | ⬜ 待实现 |
| TK-P1-8 | Raw MQTT 5 Client | TK-P1-6 | PR-029、TR-009、E2E-014 | ⬜ 待实现 |
| TK-P1-9 | 测试 source set 与任务约定 | TK-P0-1 | unit/component/contract/integration | ⬜ 待实现 |
| TK-P2-1 | Store Contract 完整语义 | PRE-05、TK-P1-4 | ST-011~ST-020 | ⬜ 待实现 |
| TK-P2-2 | ForkedBrokerFixture | PRE-04、TK-P1-5 | E2E-007~E2E-008 | ⬜ 待实现 |
| TK-P2-3 | Crash/Restart 控制协议与诊断 | TK-P2-2、TK-P0-5 | recovery cases | ⬜ 待实现 |
| TK-P2-4 | QoS 1/2 恢复场景模板 | TK-P1-3、TK-P2-1~TK-P2-3 | E2E-004~E2E-008 | ⬜ 待实现 |
| TK-P3-1 | MQTT 5 完整 Builders/Assertions | protocol/core P3 | CO-046~CO-072 | ⬜ 待实现 |
| TK-P3-2 | PluginHarnessProvider 与 PluginHarness | PRE-06、TK-P0 | PL-001~PL-016、PL-021~PL-022 | ⬜ 待实现 |
| TK-P3-3 | PluginInvokerContract | TK-P3-2 | PL-017~PL-020 | ⬜ 待实现 |
| TK-P3-4 | 互操作运行器 | TK-P1-7、broker P3 | IOP-001~IOP-004 | ⬜ 待实现 |
| TK-P4-1 | TLS/证书测试材料与连接配置 | TK-P1-6~TK-P1-8 | TR-008 | ⬜ 待实现 |
| TK-P4-2 | 资源泄漏与阻塞检测辅助 | TK-P0-3、TK-P1-6 | NF-001~NF-003 | ⬜ 待实现 |
| TK-P4-3 | 失败制品与敏感信息清理 | TK-P1-6、TK-P2-3 | NF-004~NF-005 | ⬜ 待实现 |

## 4. TK-P0：确定性基础与 Store 骨架

### TK-P0-1：Gradle test fixtures 与依赖边界

产出：

- 修改 `testkit/build.gradle.kts`，只在 `testFixturesApi` 引入 `protocol`、`core`。
- Reactor Test、JUnit 等只使用 `testFixturesImplementation`/`testImplementation`。
- 删除生产变体中的 `api(project(":protocol"))`、`api(project(":core"))` 和 `implementation(project(":store-memory"))`。
- 创建 `src/testFixtures/java`、`src/test/java` 标准目录。
- 增加 production runtimeClasspath/POM 不含 testkit 和测试库的自动检查。

验收：

```bash
./gradlew :testkit:testFixturesJar :testkit:test
./gradlew :broker:dependencies --configuration runtimeClasspath
```

- `testkit.jar` 不包含测试夹具类，`testkit-test-fixtures.jar` 包含公共夹具。
- 生产 runtimeClasspath 不出现 testkit、JUnit、Reactor Test、HiveMQ Client 或 Testcontainers。

### TK-P0-2：确定性 Clock、Scheduler 与 ID

文件：

| 文件 | 职责 |
| --- | --- |
| `time/MutableBrokerClock.java` | 固定起点、单调前进、禁止倒退 |
| `time/ManualBrokerScheduler.java` | key 替换、cancel、稳定顺序、级联任务和失败传播 |
| `time/SequenceIdGenerator.java` | 按前缀和序号生成可预测 ID |

验收：

- 通过 TK-001~TK-006。
- Scheduler 不创建后台线程，不在内部调用 `block()`。
- 同一时刻任务按注册序号执行，级联任务有硬上限。

### TK-P0-3：Probe 与统一 Trace

文件：

| 文件 | 职责 |
| --- | --- |
| `probe/TraceEntry.java` | Store、Packet、Close、Event 等 sealed trace 类型 |
| `probe/TraceProbe.java` | 全局单调 sequence、线程安全快照和顺序断言输入 |
| `probe/PacketProbe.java` | 等待并筛选 ServerPacket/ReceivedPublish |
| `probe/EventProbe.java` | 等待 Domain Event/Plugin Event |
| `probe/StateProbe.java` | 等待 READY、Store/Outbox 等可观察状态 |

验收：

- 通过 TK-007~TK-008。
- timeout 错误包含条件、当前快照、最后错误和等待时长。
- snapshot 不暴露可变内部集合。

### TK-P0-4：Recording 与 Stub 出站端口

文件：

- `port/RecordingConnectionSink.java`
- `port/RecordingDomainEventSink.java`
- `port/RecordingTelemetry.java`
- `port/StubAuthenticator.java`
- `port/StubAuthorizer.java`

当前进度：已实现 `RecordingConnectionSink`、`RecordingBrokerStore`、Connection/Store TraceEvent 和 Probe 接入。Core 尚未定义 Domain Event、Telemetry、Authenticator、Authorizer 端口，对应实现不创建测试侧占位接口。

要求：

- 默认 allow，但测试可按调用序号、clientId、Topic/Filter 返回结果或错误。
- 所有动作写入同一个 TraceProbe。
- ConnectionSink 对现行 send/close 提供成功、延迟和失败控制；Core 后续新增 flush 时同步扩展。
- Stub 不复制生产认证、授权或 telemetry 逻辑。

### TK-P0-5：故障计划与并发 Gate

文件：

- `fault/FaultPoint.java`
- `fault/FaultPlan.java`
- `fault/Gate.java`
- `fault/FaultInjectingBrokerStore.java`
- 按需要增加 ConnectionSink、EventSink、Auth 端口包装器。

当前进度：`FaultPlan`、`Gate`、`FaultInjectingBrokerStore` 和 `FaultInjectingConnectionSink` 已实现。`STORE_AFTER_COMMIT` 只在 delegate 返回 `CommitResult.Success` 后触发；Revision Conflict/Store Error 不伪装成已提交。

要求：

- 支持 once、always、nth-match、pause 和 fail。
- `STORE_AFTER_COMMIT` 必须表示真实提交后返回错误，不能伪造回滚。
- Gate 可由测试显式 release/fail/cancel，并暴露到达 Probe。
- 并发测试不能依赖随机线程调度。

### TK-P0-6：公共断言

文件：

- `assertion/PacketAssertions.java`
- `assertion/StateAssertions.java`
- `assertion/TraceAssertions.java`

要求：

- Payload 按字节内容比较。
- User Property 和 Subscription Identifier 保留顺序及重复值。
- Packet type、Reason Code、Packet ID、DUP、Retain 和属性差异必须单独报告。
- Trace 可断言 commit-before-send/event 和不存在禁止事件。

### TK-P0-7：Store Fixture SPI

文件：

- `store/StoreFixtureProvider.java`
- `store/StoreFixture.java`
- `store/StoreCapabilities.java`
- `store/StoreTestDirectory.java`

要求：

- Provider 只返回公共 `BrokerStore`，不暴露具体数据库句柄。
- `restart()` 保留数据；`crash()` 不调用正常 close。
- capability 只表达持久重启、schema migration、并发事务等真实能力。
- 契约不得使用 Store 类型名或 `instanceof` 分支。

当前进度：四个公共 SPI 类型均已实现；`StoreTestDirectory` 限制路径位于自有目录内并支持幂等递归清理。具体 Memory/RocksDB provider 仍由各 Store 模块拥有。

### TK-P0-8：BrokerStoreContract 基线

实现 ST-001~ST-004：

1. 空 Store recovery。
2. 多实体原子提交。
3. transaction callback 异常全量回滚。
4. commit 前故障无部分写入。

Memory Store 的消费者任务：

- `store-memory` 增加 `MemoryStoreFixtureProvider` 和 `MemoryBrokerStoreContractTest`。
- 该 provider 位于 `store-memory/src/test` 或其 test fixtures，不移动到 testkit。

当前阻塞：现行 `BrokerStore` 没有 `recover()`/transaction callback，`ShardSnapshot` 也不包含 Retain/Pending Message，因此不能如实实现当前 ST-001~ST-003。需要先把 Case 改写为 `load/commit/revision` 语义，或扩展 Store 的公共可观察面；testkit 不通过反射或具体 Store API 绕过该边界。

### TK-P0-9：自测试与清理

实现：

- `fixture/FixtureResourceRegistry.java`：注册即持有、严格逆序关闭、幂等关闭。
- 公共 fixture 正常、启动失败、测试异常时的逆序关闭测试。
- 清理异常作为 suppressed error 保留。
- 所有 Scheduler/Executor/临时目录资源泄漏检查。
- TK-010、TK-014 自动化。

当前进度：`FixtureResourceRegistry`、TK-010 和依赖隔离自检已完成；Scheduler/Executor/临时目录的统一泄漏快照留待 TK-P4-2 的资源审计能力一并实现。

TK-P0 退出标准：

1. testkit 只从 test fixtures 变体发布。
2. TK-001~TK-010、TK-013~TK-014 通过。
3. Memory Store 通过 ST-001~ST-004。
4. 测试没有固定端口和 `Thread.sleep`。

## 5. TK-P1：Core DSL 与单机 E2E

### TK-P1-1：Packet Builders

实现 `engine/PacketBuilders.java`，覆盖 Connect、Publish、Subscribe、Unsubscribe、ACK、Ping、Disconnect 的合法默认值和显式覆盖。

当前进度：已实现并通过自测试；默认值保持合法，Packet ID、QoS、flags、Reason Code、Payload、属性和订阅列表均可显式覆盖。

要求：

- Builder 只构造 protocol record，不执行 Validator/状态机。
- Packet ID、QoS、属性和 flags 在 case 中可见。
- 不提供隐藏 ACK/Reason Code 的 `publishAndAssertSuccess` 一键方法。

### TK-P1-2：BrokerEngine Harness

文件：

- `engine/BrokerEngineHarnessProvider.java`
- `engine/BrokerEngineHarness.java`
- `engine/TestConnection.java`
- `engine/ScenarioSnapshot.java`

要求：

- provider 负责装配生产 BrokerEngine/Store，testkit 不构造 core 私有实现。
- Harness 暴露 open/receive/close、Clock、Scheduler 和 Trace。
- Harness 方法返回 Mono，不在内部 `block()`。
- core 测试侧增加使用 Memory Store 的 provider 实现。

### TK-P1-3：显式场景 DSL

文件：

- `engine/BrokerScenario.java`
- `engine/ScenarioStep.java`
- `engine/ScenarioResult.java`

DSL 最小操作：`opened`、`receives`、`closed`、`advanceTime`、`expectsPacket`、`expectsClose`、`expectsTraceOrder`、`expectsStoreState`。

验收：

- 同一场景可运行在 Core Harness 和真实 Broker Client Fixture 上的公共步骤不得复制 MQTT 期望。
- 失败输出步骤编号、连接名、输入报文和完整 Trace。

### TK-P1-4：Store Contract P1 扩展

实现 ST-005~ST-010：业务 ID 幂等、订阅替换、Packet ID 生命周期、Session 从属清理、模型 round-trip 和 Retain upsert/delete。

Memory Store 必须先通过，再由 RocksDB 在 P2 复用；不能为 Memory Store 放宽共同语义。

### TK-P1-5：Broker 启动 SPI

文件：

- `broker/BrokerLauncher.java`
- `broker/RunningBroker.java`
- `broker/BrokerReadiness.java`
- `broker/BrokerAddress.java`
- `broker/BrokerDiagnostics.java`
- `broker/TestBrokerConfig.java`
- `broker/TestServices.java`

要求：

- 使用 testkit 自有 readiness enum，launcher 映射生产 `HealthStatus`。
- 默认要求 READY；降级启动 case 可显式接受 DEGRADED。
- RunningBroker 暴露实际 TCP/WS 地址、stop 和诊断快照。

Broker 模块消费者任务：

- 应用 `java-test-fixtures`。
- 在 `broker/src/testFixtures` 实现 `InProcessBrokerLauncher`。
- 使用真实 Composition Root，不在 launcher 中建立第二套装配。

### TK-P1-6：BrokerTestFixture

文件：

- `broker/BrokerTestFixture.java`
- `broker/FixtureResourceRegistry.java`

要求：

- 强制监听端口为 0，并在 READY 后读取实际端口。
- 自动注册客户端、Scheduler、Store 和临时目录，逆序关闭。
- 启动失败/timeout 先保存 diagnostics 再清理。
- 通过 TK-011~TK-012、TR-001~TR-002、BR-001~BR-008。

### TK-P1-7：合法 MQTT 5 Client Fixture

文件：

- `client/Mqtt5ClientFixture.java`
- `client/TestClientConfig.java`
- `client/ReceivedPublish.java`

任务：

- 在 version catalog 增加并冻结 HiveMQ MQTT Client 测试版本。
- 包装 async MQTT 5 client，自动重连默认关闭。
- 暴露 CONNECT/SUBSCRIBE/PUBLISH/ACK/DISCONNECT 和 ReceivedPublish Probe。
- 关闭时终止客户端线程和网络资源。

### TK-P1-8：Raw MQTT 5 Client

文件：

- `client/RawMqtt5Client.java`
- `client/MqttWireBytes.java`
- `client/RawFrameProbe.java`

要求：

- 直接发送给定字节，不复用生产 PacketMapper。
- 第一阶段支持 TCP；TLS/WS 非法帧按实际需求在 P4 扩展。
- 覆盖非法 UTF-8、重复单值属性、非法 fixed header、Remaining Length 和 CONNECT 前报文。

### TK-P1-9：测试任务约定

建立或接入统一构建约定：

| Source set/任务 | 内容 | 默认 `check` |
| --- | --- | --- |
| `test` | unit | 是 |
| `componentTest` | core + Memory Store | 是 |
| `contractTest` | Store/Plugin contract | 是 |
| `integrationTest` | TCP/WS、Broker lifecycle | CI 是 |
| `recoveryTest` | Forked process/RocksDB | P2 后 CI 是 |
| `interopTest` | HiveMQ/Paho/Mosquitto | nightly/release |

统一 JUnit tags：`unit`、`component`、`contract`、`integration`、`recovery`、`interop`、`slow`。

TK-P1 退出标准：

1. 端口 0 的多个 fixture 可并行启动。
2. fixture 返回时 Broker 已 READY。
3. CONNECT/PING/DISCONNECT 和 QoS 0/1/SUBSCRIBE 真实 TCP 闭环通过。
4. persist-before-ACK 可由 TraceAssertions 证明。

## 6. TK-P2：持久 Store 与恢复

### TK-P2-1：完整 BrokerStoreContract

实现 ST-011~ST-020：并发事务、TransactionView 生命周期、幂等 close、正常/崩溃恢复、全部 Inflight 状态、过期清理、不可变快照、schema migration 和 commit 结果未知后的幂等重试。

RocksDB 消费者任务：

- 在 `store-rocksdb/src/test` 提供 `RocksStoreFixtureProvider`。
- 为每个 case 创建独立临时目录。
- 不支持的 capability 只通过 JUnit assumption 跳过。

### TK-P2-2：Forked Broker Fixture

文件：

- `broker/ForkedBrokerFixture.java`
- `broker/ForkedBrokerConfig.java`
- `broker/BrokerProcess.java`

要求：

- 使用明确的 distribution/classpath 和临时配置启动子进程。
- 通过健康 Probe 等待 READY，不解析“started”日志作为唯一条件。
- 支持 graceful stop 和强制 crash；禁止在测试 JVM 调用 `System.exit`。

### TK-P2-3：Crash/Restart 协议与诊断

文件：

- `broker/CrashPoint.java`
- `broker/ProcessControlChannel.java`
- `broker/RecoveryDiagnostics.java`

要求：

- 可在 Store commit、ACK send 等可观察边界通知父测试进程。
- crash 后复用原数据目录，以新进程和新 connection 恢复。
- timeout/失败保留 stdout/stderr、Trace、配置、PID/exit code 和数据目录索引。

### TK-P2-4：恢复场景模板

实现 E2E-004~E2E-008，至少覆盖：

- Clean Start/Session Present 四组合。
- 入站 QoS 2 的 PUBLISH/PUBREC/PUBREL 边界。
- 出站 QoS 1/2 的 PUBLISH_SENT/PUBREL_SENT 边界。
- DUP、Packet ID、Hook 不重复和最终 Inflight 清理。

TK-P2 退出标准：Memory/RocksDB 共同契约通过；所有真实 crash case 使用独立进程；任意 QoS 1/2 中间状态重启不丢失且不重复业务交付。

## 7. TK-P3：完整 MQTT 5 与插件

### TK-P3-1：完整协议 Builders/Assertions

扩展到 AUTH、Will、Retain、Topic Alias、Subscription Identifier、No Local、RAP、Retain Handling、共享订阅和全部 MQTT 5 属性。

要求：对应 `CO-046~CO-072`、`E2E-009~E2E-015` 的输入与断言不需要手写重复 record 构造，但所有关键字段保持显式。

### TK-P3-2：PluginHarness

文件：

- `plugin/PluginHarnessProvider.java`
- `plugin/PluginHarness.java`
- `plugin/PluginRequestBuilders.java`
- `plugin/PluginTraceAssertions.java`

能力：

- 构造 Connect、Publish、Subscribe、Will、AUTH 和 Event 上下文。
- 注入 Clock/Scheduler、Hook chain、Event queue 和 FaultPlan。
- 断言拓扑/priority/id 顺序、first-applicable、deny-overrides、重校验、timeout 和生命周期。

### TK-P3-3：PluginInvokerContract

实现 PL-017~PL-020。Local 和 gRPC Invoker 分别在拥有模块提供 provider，复用同一输入、deadline、错误和队列溢出契约。

testkit 不依赖 `plugin-runtime` 或 `plugin-remote-grpc`，只依赖稳定 `plugin-api`。

### TK-P3-4：互操作运行器

文件：

- `client/InteropClient.java`
- `client/HiveMqInteropClient.java`
- `client/PahoInteropClient.java`
- `client/MosquittoCliFixture.java`

要求：

- 三种客户端保留原生行为和错误输出，不用统一 wrapper 掩盖差异。
- 支持能力检测和清晰 skip 原因，例如本机未安装 Mosquitto CLI。
- 运行 IOP-001~IOP-004，失败保留客户端/Broker 双侧日志。

TK-P3 退出标准：PL-001~PL-022 和 IOP-001~IOP-004 自动化；完整 MQTT 5 场景可在 TCP/WS 复用；插件失败不改变 Store、ACK 和 Delivery。

## 8. TK-P4：生产验证辅助

### TK-P4-1：TLS 测试材料

提供每测试唯一的 CA/server/client 证书目录、过期证书和错误 identity。私钥只写临时目录，诊断归档必须脱敏。

### TK-P4-2：阻塞与资源泄漏检测

提供：

- EventLoop 阻塞检测接入点。
- fixture 前后线程、端口、文件句柄和临时目录快照。
- 慢消费者和有界队列测试辅助。
- 可重复启动停止的资源审计。

### TK-P4-3：失败制品

统一保存：测试 ID、随机种子、Broker 配置、Trace、客户端事件、进程日志和临时数据目录索引。归档前移除 password、Authentication Data、Payload、Token、私钥和 Secret。

TK-P4 退出标准：TR-008、NF-001~NF-005 具备可重复的自动化夹具；资源/阻塞失败给出定位信息而不是仅 timeout。

## 9. 跨模块消费者任务

| 拥有模块 | 必须提供 | 消费 testkit 能力 | 阶段 |
| --- | --- | --- | --- |
| `core` | BrokerStore/Clock/Scheduler/Policy 端口 | Store Contract、Clock、Recording Ports | P0-P1 |
| `runtime` | `BrokerEngineHarnessProvider` 实现 | Engine Harness、Recording Ports | P0-P1 |
| `runtime-standalone` | Local Profile provider | Engine Harness、Broker fixtures | P0-P1 |
| `store-memory` | Memory Store provider + contract subclass | BrokerStoreContract | P0-P2 |
| `store-rocksdb` | Rocks Store provider + contract subclass | BrokerStoreContract、Recovery Fixture | P2 |
| `broker` | InProcess/Forked launcher | BrokerTestFixture | P1-P2 |
| `runtime/transport.netty` | 场景适配与实际地址读取 | Broker/client fixtures | P1-P4 |
| `plugin-runtime` | Local PluginHarness/Invoker provider | Plugin contracts | P3 |
| `plugin-remote-grpc` | gRPC PluginHarness/Invoker provider | Plugin contracts、network faults | P3 |

这些实现不得放回 testkit，否则会形成反向依赖或让契约偏向某个适配器。

## 10. 任务依赖图

```text
TK-P0-1 构建隔离
  |
  +-- TK-P0-2 Clock/Scheduler/ID
  |     `-- TK-P0-3 Probe/Trace
  |            +-- TK-P0-4 Recording Ports
  |            `-- TK-P0-5 FaultPlan/Gate
  +-- TK-P0-6 Assertions
  `-- TK-P0-7 Store SPI
         `-- TK-P0-8 Store Contract P0
                 `-- TK-P0-9 Self Tests

TK-P0 + protocol/core ports
  `-- TK-P1-1 Packet Builders
      + TK-P1-2 Engine Harness
      |   `-- TK-P1-3 Scenario DSL
      + TK-P1-5 Broker Launcher
      |   `-- TK-P1-6 Broker Fixture
      |       +-- TK-P1-7 MQTT Client
      |       `-- TK-P1-8 Raw Client
      `-- TK-P1-4 Store Contract P1

TK-P1 + RocksDB/QoS2
  `-- TK-P2-1 Store Contract P2
      + TK-P2-2 Forked Broker
      |   `-- TK-P2-3 Crash Protocol
      `-- TK-P2-4 Recovery Scenarios

TK-P2 + full MQTT/plugin-api
  `-- TK-P3-1 Full MQTT Fixtures
      + TK-P3-2 Plugin Harness
      |   `-- TK-P3-3 Invoker Contract
      `-- TK-P3-4 Interop
```

## 11. 完成定义

每个任务完成必须同时满足：

1. 公共 API 位于 `src/testFixtures/java`，实现类位于正确拥有模块。
2. 对应 Case ID 已自动化，或任务明确只提供被其他模块消费的 SPI。
3. 正常、错误、取消和 timeout 路径都有资源清理测试。
4. 没有固定端口、无界队列、随机 sleep 或生产类型反向依赖。
5. `./gradlew :testkit:test :testkit:testFixturesJar` 通过。
6. 受影响消费者模块的 contract/component/integration 任务通过。
7. 生产 runtimeClasspath 和发布 POM 不包含 testkit 或测试库。
