# Monaco MQTT 5.0 模块详细设计与任务拆分

> 状态：Proposed
> 更新日期：2026-07-25
> 前置文档：[架构设计](mqtt5-architecture.md)、[技术实现方案](mqtt5-technical-implementation.md)
>
> 集群增量：[集群架构设计](mqtt5-cluster-architecture.md)、[集群模块详细设计](mqtt5-cluster-module-design.md)
>
> 演进说明：本文的 `core`、`transport-reactor` 是单机迁移阶段名称；集群 C0 会拆分为 `core-domain`、`runtime-reactor` 和 `transport-reactor-netty`，不长期保留桥接模块。

## 1. 构建系统变更

### 1.1 settings.gradle.kts

新增模块声明，迁移期保留旧模块可编译：

```kotlin
include(
    // 旧模块（迁移期保留，不被新模块依赖）
    "common",
    "gateway",
    // 独立日志库（长期保留，可选依赖）
    "logging",
    // 新架构模块
    "protocol",
    "core",
    "transport-reactor",
    "store-memory",
    "store-rocksdb",
    "security-default",
    "plugin",
    "plugin:api",
    "plugin:runtime",
    "plugin:remote",
    "observability-micrometer",
    "broker",
    "testkit"
)
```

### 1.2 libs.versions.toml 更新

Java 版本升级到 25，通过 Reactor BOM 统一管理 Reactor 及 Reactor Netty 版本：

```toml
[versions]
java = "25"
reactor-bom = "2025.0.6"
netty = "4.2.16.Final"
junit = "5.10.3"
rocksdb = "10.2.1"
micrometer = "1.15.0"
jackson = "2.18.2"

[libraries]
reactor-bom = { module = "io.projectreactor:reactor-bom", version.ref = "reactor-bom" }
reactor-core = { module = "io.projectreactor:reactor-core" }
reactor-netty-core = { module = "io.projectreactor.netty:reactor-netty-core" }
reactor-netty-http = { module = "io.projectreactor.netty:reactor-netty-http" }
reactor-test = { module = "io.projectreactor:reactor-test" }
netty-codec-mqtt = { module = "io.netty:netty-codec-mqtt", version.ref = "netty" }
```

需要 Reactor 的模块使用 `platform(libs.reactor.bom)` 引入 BOM，子依赖不再指定版本：

```kotlin
dependencies {
    implementation(platform(libs.reactor.bom))
    implementation(libs.reactor.core)
    // transport-reactor 额外需要:
    // implementation(libs.reactor.netty.core)
    // implementation(libs.netty.codec.mqtt)
}
```

### 1.3 各模块构建配置

| 模块 | 插件 | 依赖 | 禁止依赖 |
|---|---|---|---|
| protocol | java-library | 仅 Java 标准库 | Vert.x、Netty、Store |
| core | java-library | protocol、reactor-core | vertx-mqtt、Netty、RocksDB、Micrometer |
| transport-reactor | java-library | core、protocol、reactor-netty、netty-codec-mqtt | 会话持久化、ACL |
| store-memory | java-library | core | Broker 业务 |
| store-rocksdb | java-library | core、rocksdbjni | 网络、协议 |
| security-default | java-library | core | Endpoint、数据库 |
| plugin:api | java-library | protocol、reactor-core | core 内部状态、Endpoint、Store |
| plugin:runtime | java-library | core、plugin:api | MQTT 状态机 |
| plugin:remote | java-library | plugin:runtime、plugin:api、gRPC | MQTT 状态机、Store |
| observability-micrometer | java-library | core、micrometer | 业务状态变更 |
| broker | application | core + plugin:runtime + 所有适配器 | MQTT 业务规则 |
| testkit | java-library (test-fixtures) | protocol、core、store-memory | 生产代码 |

所有模块 Java toolchain 设置为 25。

---

## 2. 模块详细设计

### 2.1 protocol 模块

纯 Java 模块，零外部依赖。负责表达 MQTT 5 语义，不负责 socket 编解码。

**基础包：** `cn.elvis.monaco.protocol`

#### 2.1.1 报文模型（packet）

使用 sealed interface 区分客户端报文和服务端报文：

```
ClientPacket (sealed interface)
├── ConnectPacket    — clientId, cleanStart, keepAlive, will, username, password, properties
├── PublishPacket    — topicName, qos, retain, dup, packetId, payload, properties
├── PubAckPacket     — packetId, reasonCode, properties
├── PubRecPacket     — packetId, reasonCode, properties
├── PubRelPacket     — packetId, reasonCode, properties
├── PubCompPacket    — packetId, reasonCode, properties
├── SubscribePacket  — packetId, subscriptions[], properties
├── UnsubscribePacket — packetId, topicFilters[], properties
├── PingReqPacket
├── DisconnectPacket — reasonCode, properties
└── AuthPacket       — reasonCode, authMethod, authData, properties

ServerPacket (sealed interface)
├── ConnAckPacket    — sessionPresent, reasonCode, properties
├── PublishPacket    — topicName, qos, retain, dup, packetId, payload, properties
├── PubAckPacket     — packetId, reasonCode, properties
├── PubRecPacket     — packetId, reasonCode, properties
├── PubRelPacket     — packetId, reasonCode, properties
├── PubCompPacket    — packetId, reasonCode, properties
├── SubAckPacket     — packetId, reasonCodes[], properties
├── UnsubAckPacket   — packetId, reasonCodes[], properties
├── PingRespPacket
├── DisconnectPacket — reasonCode, properties
└── AuthPacket       — reasonCode, authMethod, authData, properties
```

报文和值对象使用 Java record，不可变。不暴露 MqttEndpoint、MqttProperties、ByteBuf 或 JsonObject。

#### 2.1.2 值对象（model）

| 类 | 说明 |
|---|---|
| `ClientId` | record，校验长度 0-65535 字节，合法 UTF-8 |
| `ConnectionId` | record，UUID 标识物理连接 |
| `PacketId` | record，范围 1-65535 |
| `TopicName` | record，不可变，禁止 + 和 # |
| `TopicFilter` | record，不可变，允许规范位置通配符 |
| `ShareGroup` | record，共享订阅组名 |
| `QoS` | enum：AT_MOST_ONCE(0)、AT_LEAST_ONCE(1)、EXACTLY_ONCE(2) |
| `Payload` | record wrapping byte[]，含 formatIndicator |

#### 2.1.3 属性系统（property）

| 类 | 说明 |
|---|---|
| `MqttProperty` | sealed interface，每种属性使用明确类型 |
| `PropertyId` | enum，所有 MQTT 5 属性标识符 |
| `PropertySchema` | 描述每类报文允许的属性、重复性和范围 |
| `ConnectProperties` | record，CONNECT 报文属性集合 |
| `ConnAckProperties` | record，CONNACK 报文属性集合 |
| `PublishProperties` | record，PUBLISH 报文属性集合 |
| `WillProperties` | record，遗嘱属性集合 |

User Property 可重复，保留顺序。Subscription Identifier 在转发中可出现多个。

#### 2.1.4 Reason Code（reason）

```java
public enum ReasonCode {
    SUCCESS(0x00),
    NORMAL_DISCONNECTION(0x00),
    GRANTED_QOS_0(0x00),
    GRANTED_QOS_1(0x01),
    GRANTED_QOS_2(0x02),
    DISCONNECT_WITH_WILL(0x04),
    NO_MATCHING_SUBSCRIBERS(0x10),
    UNSPECIFIED_ERROR(0x80),
    MALFORMED_PACKET(0x81),
    PROTOCOL_ERROR(0x82),
    // ... 所有 MQTT 5 Reason Code
}
```

#### 2.1.5 校验（validation）

| 类 | 职责 |
|---|---|
| `TopicNameValidator` | Topic Name 校验：禁止 +、#、空、含 U+0000 |
| `TopicFilterValidator` | Topic Filter 校验：通配符位置规则、共享订阅格式 |
| `PropertyValidator` | 属性校验：适用报文、重复性、取值范围 |

#### 2.1.6 主题匹配（topic）

| 类 | 职责 |
|---|---|
| `TopicMatcher` | 主题匹配：+、#、$SYS 前缀、共享订阅解析 |
| `TopicLevels` | 主题分层工具，按 `/` 拆分 |

#### 2.1.7 状态机

纯函数形式：`State + Event → Transition`，Transition 包含新状态和待执行 Action。

| 类 | 职责 |
|---|---|
| `InboundQos2StateMachine` | 入站 QoS 2：PUBLISH → PUBREC → PUBREL → PUBCOMP |
| `OutboundQosStateMachine` | 出站 QoS 1/2 状态转换 |
| `EnhancedAuthStateMachine` | 增强认证多阶段状态机 |

#### 2.1.8 错误（error）

```java
public record ProtocolViolation(
    ReasonCode reasonCode,
    PacketType responseType,  // 用哪种报文响应
    boolean closeConnection,  // 是否需要关闭连接
    String message
) {}
```

---

### 2.2 core 模块

Broker 唯一业务内核。包含领域模型、用例实现和端口接口，不知道适配器如何工作。

**基础包：** `cn.elvis.monaco.core`

#### 2.2.1 入站端口（port.in）

```java
public interface BrokerEngine {
    Mono<Void> opened(ConnectionHandle connection);
    Mono<Void> received(ConnectionId id, ClientPacket packet);
    Mono<Void> closed(ConnectionId id, DisconnectCause cause);
}
```

`ConnectionHandle` 封装 `ConnectionSink` 和连接元数据（远端地址等），由 transport 创建。

#### 2.2.2 出站端口（port.out）

| 接口 | 职责 |
|---|---|
| `ConnectionSink` | send(ServerPacket)、flush()、close(ReasonCode) |
| `BrokerStore` | transact(StoreOperation)、recover()、close() |
| `TransactionView` | 事务内读写 Session、Subscription、Message、Delivery、Inflight、Retain、Will |
| `Authenticator` | authenticate(clientId, username, password) → Mono\<AuthResult\> |
| `Authorizer` | authorizeConnect/Publish/Subscribe → Mono\<AuthzResult\> |
| `BrokerTelemetry` | 计数、耗时、状态量、协议错误 |
| `DomainEventSink` | publish(DomainEvent)，事务提交后通知 |
| `BrokerClock` | now() → Instant，测试可替换 |
| `IdGenerator` | nextId() → String (ULID) |
| `BrokerScheduler` | schedule(key, delay, action)、cancel(key) |

**BrokerStore 事务接口：**

```java
public interface BrokerStore {
    <T> Mono<T> transact(StoreOperation<T> operation);
    Mono<RecoverySnapshot> recover();
    Mono<Void> close();
}

@FunctionalInterface
public interface StoreOperation<T> {
    T execute(TransactionView view);
}
```

#### 2.2.3 领域模型（model）

| 类 | 说明 | 生命周期 |
|---|---|---|
| `ConnectionState` | 认证阶段、Keep Alive、协商限制、双向 Topic Alias 表 | 瞬时，随物理连接 |
| `SessionState` | clientId、expiryAt、订阅、Will、连接代次 | 可持久化 |
| `MessageRecord` | ULID、publisherId、topic、payload、QoS、properties、expiryAt | 可持久化 |
| `DeliveryRecord` | clientId、messageId、finalQoS、subscriptionIds、排队状态 | 可持久化 |
| `InflightRecord` | clientId、direction、packetId、messageId、qosState、DUP | 可持久化 |
| `RetainedRecord` | topicName、messageRecord | 可持久化 |
| `ConnectLimits` | 双向 Receive Maximum、Maximum Packet Size、Topic Alias Maximum | 瞬时 |
| `DisconnectCause` | Normal、ProtocolError、KeepAliveTimeout、NetworkError、ServerShutdown、SessionTakenOver | 值 |

ConnectionState 不持久化，Topic Alias 在网络连接结束时清理。SessionState 不持有 Endpoint。

#### 2.2.4 领域服务（service）

| 服务 | 职责 |
|---|---|
| `ConnectionService` | CONNECT、AUTH、Keep Alive、连接接管和协商限制 |
| `SessionService` | Clean Start、Session Present、Expiry、断线与恢复 |
| `PublishService` | 入站 PUBLISH 校验、QoS 状态转换、Retain 和路由请求 |
| `SubscriptionService` | SUBSCRIBE/UNSUBSCRIBE、授权、索引更新和 Retain replay |
| `RoutingService` | 普通/重叠/共享订阅匹配，生成每客户端 DeliveryPlan |
| `DeliveryService` | 离线队列、Receive Maximum、出站 Packet ID、重发和 ACK |
| `WillService` | Will 保存、取消、调度和发布 |
| `MaintenanceService` | 会话、消息、Retain 和 Will 过期清理 |

不再使用通用 Manager 抽象。服务名称对应具体用例集合。

#### 2.2.5 并发组件（engine）

| 类 | 职责 |
|---|---|
| `BrokerEngineImpl` | 实现 BrokerEngine，报文分发 |
| `ConnectionMailbox` | 同一物理连接报文按接收顺序处理（Mono 串联） |
| `SessionMailbox` | 以 clientId 为键，保证状态变更串行 |
| `RoutingCoordinator` | 串行化订阅索引变更和匹配快照生成 |

Mailbox 只串联 Mono，不占用线程持续等待。禁止跨 Mailbox 同步等待。

#### 2.2.6 领域事件（event）

```
DomainEvent (sealed interface)
├── SessionConnected    — clientId, connectionId, cleanStart
├── SessionDisconnected — clientId, cause, connectionGeneration
├── MessageAccepted     — messageId, publisherId, topic, qos
├── MessageDelivered    — messageId, subscriberId
├── SubscriptionChanged — clientId, added[], removed[]
├── WillPublished       — clientId, topic
```

所有事件在 BrokerStore 事务提交后交给 DomainEventSink。事件发送失败不回滚 MQTT 状态。

#### 2.2.7 配置（config）

```java
public record BrokerConfig(
    int tcpPort,
    int wsPort,
    boolean tcpEnabled,
    boolean wsEnabled,
    boolean tlsEnabled,
    String tlsCertPath,
    String tlsKeyPath,
    int maxConnections,
    int maxSessionExpiryInterval,
    int defaultReceiveMaximum,
    int maxReceiveMaximum,
    int topicAliasMaximum,
    int maxPacketSize,
    int maxQueueSize,
    int serverKeepAlive,
    QoS maximumQoS,
    boolean retainAvailable,
    boolean wildcardSubscriptionAvailable,
    boolean subscriptionIdentifierAvailable,
    boolean sharedSubscriptionAvailable,
    String authMode,
    String authFilePath,
    String rocksdbPath
) {}
```

broker 加载和校验后注入，core 不读取配置文件。

#### 2.2.8 错误映射

| 类 | 职责 |
|---|---|
| `ProtocolErrorMapper` | 内部错误 → MQTT 5 Reason Code + 动作 |

映射规则：
- CONNECT 阶段：CONNACK 拒绝
- SUBSCRIBE/UNSUBSCRIBE：对应 ACK Reason Code（可按条目失败）
- PUBLISH QoS 1/2：PUBACK/PUBREC 错误码
- 连接级错误：发送 DISCONNECT 后关闭
- 编解码/网络错误：直接关闭，触发 Will

---

### 2.3 transport-reactor 模块

**基础包：** `cn.elvis.monaco.adapter.transport.reactor`

| 类 | 职责 |
|---|---|
| `ReactorTransportFactory` | 创建 TCP/WS 传输实例 |
| `ReactorTransport` | 使用 Reactor Netty 监听 TCP/TLS/WS |
| `PacketMapper` | Netty MQTT codec 事件 → protocol 报文双向转换 |
| `ReactorConnectionSink` | 实现 ConnectionSink，ServerPacket → Netty Channel 写入 |
| `ConnectionRegistry` | ConnectionId → Channel 瞬时注册表 |
| `ReactorBrokerScheduler` | 实现 BrokerScheduler，Reactor Scheduler 封装 |
| `TransportConfig` | record：端口、TLS、实例数 |

Handler 注册完毕后才接受 CONNECT。Transport.start 返回 Mono，端口绑定前 broker 不进入 READY。

---

### 2.4 store-memory 模块

**基础包：** `cn.elvis.monaco.adapter.store.memory`

| 类 | 职责 |
|---|---|
| `MemoryBrokerStore` | 实现 BrokerStore，锁 + 不可变快照 |
| `MemoryTransactionView` | 实现 TransactionView，操作内存数据结构 |

内部数据结构：
- sessions: `ConcurrentHashMap<String, SessionState>`
- subscriptions: `ConcurrentHashMap<String, List<Subscription>>`
- messages: `ConcurrentHashMap<String, MessageRecord>`
- deliveries: `ConcurrentHashMap<String, Queue<DeliveryRecord>>`（按 clientId）
- inflight: `ConcurrentHashMap<InflightKey, InflightRecord>`
- retained: `ConcurrentHashMap<String, RetainedRecord>`（按 topicName）
- wills: `ConcurrentHashMap<String, WillRecord>`（按 clientId）

---

### 2.5 store-rocksdb 模块（P2 阶段）

**基础包：** `cn.elvis.monaco.adapter.store.rocksdb`

| 类 | 职责 |
|---|---|
| `RocksBrokerStore` | 实现 BrokerStore，WriteBatch 原子事务 |
| `RocksStoreConfig` | 配置：数据目录、WAL、缓存大小 |
| `ColumnFamilies` | Column Family 定义与管理 |
| `KeyCodec` | 稳定二进制 key 编码 |
| `ValueCodec` | Value 编码，含 schemaVersion |

Column Family：sessions、subscriptions、messages、deliveries、inflight、retained、wills、metadata。

RocksDB 调用运行在 Schedulers.boundedElastic()，队列满时返回 Server Busy。

---

### 2.6 security-default 模块

**基础包：** `cn.elvis.monaco.adapter.security`

| 类 | 职责 |
|---|---|
| `DefaultSecurityFactory` | 根据配置创建 Authenticator 和 Authorizer |
| `AnonymousAuthenticator` | 始终允许，开发环境 |
| `PasswordFileAuthenticator` | 哈希密码文件认证 |
| `FileAclAuthorizer` | 文件 ACL，支持 connect/publish/subscribe |
| `AllowAllAuthorizer` | 始终允许，开发环境 |

---

### 2.7 plugin 模块

plugin 为聚合模块，包含三个子模块：`plugin:api`、`plugin:runtime`、`plugin:remote`。

#### 2.7.1 plugin:api 子模块

**基础包：** `cn.elvis.monaco.plugin.api`

面向第三方的稳定插件接口。不允许插件直接访问 SessionState、BrokerStore、Endpoint 或 core Service。

定义内容：
- 生命周期接口（init、start、stop）
- Hook 接口（认证、授权、消息拦截）
- Decision DTO（Accept、Reject、Modify）
- Event DTO（连接、断线、发布、订阅事件通知）
- 插件 Manifest 和 API 版本声明

plugin:api 依赖 protocol（MQTT 值对象）和 reactor-core（Mono/Flux），不依赖 core 内部。

---

#### 2.7.2 plugin:runtime 子模块

**基础包：** `cn.elvis.monaco.plugin.runtime`

实现 core 的安全、策略和事件端口，将第三方 Hook 结果转换为 core 端口结果。

| 类 | 职责 |
|---|---|
| `PluginRuntime` | 插件生命周期管理，启动/停止 |
| `PluginLoader` | 插件发现、Manifest 校验、API 版本检查 |
| `PluginClassLoader` | 隔离 ClassLoader，防止插件间冲突 |
| `HookChain` | 确定顺序的 Hook 调用链，支持短路 |
| `HookExecutor` | 有界 WorkerExecutor 执行 Hook，超时和熔断 |
| `EventDispatcher` | 事件缓冲、上限和丢弃策略 |

职责详述：
- 发现插件、校验 Manifest 和 API 版本，隔离 ClassLoader
- 为认证、授权和拦截调用建立确定顺序、超时和失败策略
- 使用有界并发和熔断隔离故障插件
- 对事件订阅实施缓冲上限和丢弃策略
- 将插件异常或非法结果映射为明确的 Broker 决策

Hook 执行时机：
- 决策 Hook 位于 CONNECT、PUBLISH、SUBSCRIBE、Will 接纳路径中，在持久化之前执行
- 修改结果必须重新经过协议校验和配额检查
- Domain Event 在事务提交后异步发送，失败不改变 ACK、Store 或 Delivery 状态

core 不依赖 plugin:api。plugin:runtime 同时依赖 core 和 plugin:api。

---

#### 2.7.3 plugin:remote 子模块

**基础包：** `cn.elvis.monaco.plugin.remote.grpc`

支持不可信或跨语言插件在独立进程中运行，通过 gRPC 通信。

| 类 | 职责 |
|---|---|
| `GrpcPluginSource` | 远程插件连接管理 |
| `GrpcHookProxy` | 将 gRPC 调用适配为 plugin:api Hook 接口 |
| `GrpcEventStream` | 事件流推送到远程进程 |
| `RemoteHealthCheck` | 远程插件健康检查 |

依赖 plugin:runtime + plugin:api + gRPC。默认使用 gRPC 强类型契约；只有持续双向事件流证明需要 Request N 和 Resume 时再增加 RSocket 适配器。

---

### 2.8 observability-micrometer 模块

**基础包：** `cn.elvis.monaco.adapter.observability`

| 类 | 职责 |
|---|---|
| `MicrometerTelemetry` | 实现 BrokerTelemetry |
| `HealthEndpoint` | 健康检查端点 |

核心指标：
- `monaco_connections_active`、`monaco_sessions_total`
- `monaco_publish_in_total`、`monaco_publish_out_total`（按 QoS 和结果分类）
- `monaco_inflight`、`monaco_offline_queue_size`、`monaco_messages_dropped_total`
- `monaco_auth_failures_total`、`monaco_protocol_errors_total`
- `monaco_store_latency_seconds`、`monaco_event_loop_delay_seconds`

---

### 2.11 broker 模块

**基础包：** `cn.elvis.monaco.broker`

| 类 | 职责 |
|---|---|
| `MonacoApplication` | main()，唯一应用入口 |
| `BrokerConfigLoader` | 环境变量/配置文件 → 不可变 BrokerConfig |
| `BrokerLifecycle` | 启动/停止生命周期管理 |
| `HealthStatus` | enum：STARTING、READY、DEGRADED、STOPPING |

**启动流程：**

```
1. BrokerConfigLoader 加载配置，集中校验
2. 创建 BrokerStore（memory 或 rocksdb）→ recover()
3. 重建主题索引和定时器
4. 创建 Authenticator、Authorizer
5. 创建 BrokerEngineImpl（注入所有端口）
6. 启动 plugin:runtime
7. 创建 ReactorTransport → bind TCP/TLS/WS
8. 健康状态 → READY
```

任一步失败关闭已启动资源并退出进程。只有监听端口成功后才记录 Broker started。

**配置覆盖顺序：** 启动参数 > JVM System Properties > Environment > Config File > Default。

---

### 2.12 testkit 模块

**基础包：** `cn.elvis.monaco.testkit`

| 类 | 职责 |
|---|---|
| `BrokerStoreContract` | Store 契约测试抽象基类，store-memory 和 store-rocksdb 共用 |
| `BrokerTestFixture` | 启动内存版 Broker 的工具，自动分配端口 |
| `TestBrokerConfig` | 测试用配置（端口 0、匿名认证） |
| `PacketAssertions` | 报文断言工具 |

测试不依赖固定端口；Listener 配置端口 0 后读取实际端口。测试必须等待 READY 后再连接。

---

## 3. 任务拆分

### 3.1 P0：建立可运行基线

**退出标准：** 全新 checkout 一条命令构建、测试并启动，TCP MQTT 5 客户端可完成 CONNECT/PING/DISCONNECT。

| 编号 | 任务 | 产出 | 依赖 | 验收标准 |
|---|---|---|---|---|
| P0-1 | 创建构建骨架 | settings.gradle.kts、libs.versions.toml（Java 25）、所有新模块 build.gradle.kts | 无 | `./gradlew build` 全部通过（空模块） |
| P0-2 | protocol — 值对象与报文模型 | packet/、model/、reason/、error/ | P0-1 | QoS、ReasonCode、值对象单元测试通过 |
| P0-3 | protocol — 校验与匹配 | validation/、topic/ | P0-1 | TopicName/TopicFilter/Property 校验单元测试通过 |
| P0-4 | core — 端口接口 | port/in/、port/out/ 全部接口 | P0-2 | 编译通过 |
| P0-5 | core — 领域模型 | model/ 全部 record | P0-2, P0-4 | 编译通过 |
| P0-6 | core — BrokerConfig | config/BrokerConfig.java | P0-4 | 编译通过 |
| P0-7 | core — ConnectionService | service/ConnectionService、SessionService 骨架 | P0-4, P0-5 | mock store + mock sink 单元测试通过 CONNECT 正常/异常场景 |
| P0-8 | core — BrokerEngineImpl | engine/BrokerEngineImpl、ConnectionMailbox、SessionMailbox | P0-4, P0-7 | 报文正确路由到 ConnectionService |
| P0-9 | store-memory | MemoryBrokerStore、MemoryTransactionView | P0-4 | 基本事务读写测试通过 |
| P0-10 | transport-reactor — CONNECT/PING/DISCONNECT | ReactorTransport、PacketMapper、ReactorConnectionSink、ConnectionRegistry | P0-4, P0-2 | TCP 端口可监听 |
| P0-11 | security-default 骨架 | AnonymousAuthenticator、AllowAllAuthorizer | P0-4 | 匿名认证通过 |
| P0-12 | broker — Composition Root | MonacoApplication、BrokerConfigLoader、BrokerLifecycle | P0-8, P0-9, P0-10, P0-11 | `./gradlew :broker:run` 启动成功 |
| P0-13 | testkit 骨架 | BrokerStoreContract、BrokerTestFixture、TestBrokerConfig | P0-9 | store-memory 通过契约测试 |
| P0-14 | 端到端验证 | CONNECT/PING/DISCONNECT 集成测试 | P0-12 | MQTT 5 客户端闭环 |

### 3.2 P1：QoS 0/1 与订阅闭环

**退出标准：** QoS 0/1、订阅替换、断线重连和错误 Reason Code 端到端测试通过。

| 编号 | 任务 | 产出 | 依赖 | 验收标准 |
|---|---|---|---|---|
| P1-1 | protocol — 属性校验完善 | PropertyValidator 支持 PUBLISH/SUBSCRIBE 属性、PacketResponseFactory | P0-2 | 属性合法/非法用例测试通过 |
| P1-2 | core — PublishService | 入站 QoS 0/1 校验、状态转换、persist、路由请求 | P0-7, P0-8 | QoS 0 路由、QoS 1 PUBACK 单元测试通过 |
| P1-3 | core — SubscriptionService | SUBSCRIBE/UNSUBSCRIBE、授权、索引更新、Retain replay 骨架 | P0-7, P0-8 | SUBACK 按序返回、部分失败测试通过 |
| P1-4 | core — RoutingService | 订阅匹配、DeliveryPlan 生成、RoutingCoordinator | P1-3 | 通配符匹配、重叠订阅单份投递测试通过 |
| P1-5 | core — DeliveryService | 离线队列、Receive Maximum、出站 Packet ID、事件驱动 drain | P1-2, P1-4 | 窗口背压、ACK 释放测试通过 |
| P1-6 | transport-reactor — PUB/SUB 映射 | PacketMapper 新增 PUBLISH/SUBSCRIBE 等报文映射 | P0-10, P1-2 | 报文正确转换 |
| P1-7 | security-default — 基础 ACL | FileAclAuthorizer | P0-11 | publish/subscribe 权限测试通过 |
| P1-8 | store-memory — 消息与投递 | TransactionView 完善 Message/Delivery/Inflight 操作 | P0-9, P1-2 | 消息写入、队列弹出测试通过 |
| P1-9 | 端到端测试 | QoS 0/1 发布订阅、通配符、断线重连、错误码 | P1-1 ~ P1-8 | 全部场景通过 |

### 3.3 P2：持久会话与 QoS 2

**退出标准：** 在每个 QoS 2 中间状态重启 Broker，恢复后仍满足 exactly-once。

| 编号 | 任务 | 产出 | 依赖 |
|---|---|---|---|
| P2-1 | protocol — QoS 2 状态机 | InboundQos2StateMachine、OutboundQosStateMachine | P0-2 |
| P2-2 | core — Inflight 管理 | PUBREC/PUBREL/PUBCOMP 全流程 | P2-1, P1-2 |
| P2-3 | core — SessionService 完善 | Clean Start=false、Session Present、Session Expiry | P0-7 |
| P2-4 | core — 连接接管 | Session Taken Over + connection generation | P2-3 |
| P2-5 | store-rocksdb | Column Family、WriteBatch、key 编码、恢复 | P0-4 |
| P2-6 | testkit — 完整契约 | BrokerStoreContract 全部用例 | P2-5, P0-13 |
| P2-7 | 崩溃恢复测试 | 每个 QoS 2 中间状态重启验证 | P2-2, P2-5 |

### 3.4 P3：MQTT 5 完整能力

**退出标准：** 必测场景全部自动化，Paho、HiveMQ、Mosquitto 互操作通过。

| 编号 | 任务 | 产出 | 依赖 |
|---|---|---|---|
| P3-1 | 保留消息 | RetainService，Retain Handling 选项 | P1-2 |
| P3-2 | 遗嘱 | WillService，Will Delay + Session Expiry 竞争 | P2-3 |
| P3-3 | Topic Alias | ConnectionState 双向别名表，越界/清理 | P1-2 |
| P3-4 | Subscription Identifier | 多 ID 聚合投递 | P1-4 |
| P3-5 | Enhanced AUTH | 增强认证状态机 | P0-7 |
| P3-6 | 共享订阅 | $share/{group}/{filter}，round-robin | P1-4 |
| P3-7 | plugin:api + plugin:runtime | 插件框架完整实现，Hook Chain、ClassLoader 隔离 | P0-4 |
| P3-8 | plugin:remote | gRPC 远程插件支持 | P3-7 |
| P3-9 | observability-micrometer | BrokerTelemetry 完整实现 | P0-4 |

### 3.5 P4：生产化

**退出标准：** 发布检查表、容量报告、故障恢复演练和运维手册齐备。

| 编号 | 任务 | 产出 | 依赖 |
|---|---|---|---|
| P4-1 | TLS/mTLS | 配置与测试 | P3 全部 |
| P4-2 | 配额与限流 | 连接/订阅/队列配额 | P3 全部 |
| P4-3 | 压力测试 | 容量模型与长稳测试 | P4-1, P4-2 |
| P4-4 | 删除旧模块 | 移除 gateway、common（logging 作为独立日志库保留） | P4-3 |
| P4-5 | 运维文档 | 发布检查表、运维手册 | P4-4 |

---

## 4. 任务依赖图

```
P0-1 构建骨架
 ├── P0-2 protocol 报文模型
 │    ├── P0-3 protocol 校验匹配
 │    └──┐
 │       P0-4 core 端口接口
 │        ├── P0-5 core 领域模型
 │        ├── P0-6 core 配置
 │        ├── P0-7 core ConnectionService ← P0-5
 │        │    └── P0-8 core BrokerEngineImpl
 │        ├── P0-9 store-memory
 │        ├── P0-10 transport-reactor ← P0-2
 │        └── P0-11 security-default
 │
 P0-8 + P0-9 + P0-10 + P0-11
 └── P0-12 broker Composition Root
      ├── P0-13 testkit
      └── P0-14 端到端 CONNECT/PING/DISCONNECT
           │
           P1-1 ~ P1-8
           └── P1-9 端到端 QoS 0/1
                │
                P2-1 ~ P2-7
                │
                P3-1 ~ P3-9
                │
                P4-1 ~ P4-5
```

---

## 5. 架构验收标准

1. Gradle 依赖图符合本文方向，不存在循环依赖。
2. protocol 不依赖 Reactor；core 仅依赖 reactor-core，不出现 Netty、RocksDB 或 Micrometer。
3. 使用 store-memory 可运行全部协议组件测试，替换 store-rocksdb 无需修改 core。
4. TCP 与 WebSocket 使用同一套 BrokerEngine 和测试场景。
5. Broker 启动、停止和监听失败通过 Mono 准确传播。
6. 任意 QoS 1/2 中间状态重启后，Broker 能从 Store 恢复。
7. 删除 EventBus 后不影响核心消息投递；事件系统只服务扩展和观测。
8. 旧 gateway/common 模块在迁移期仍可编译，不被新模块反向依赖。logging 作为独立日志库长期保留。
