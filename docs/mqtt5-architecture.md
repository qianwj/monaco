# Monaco MQTT 5.0 架构设计

> 状态：Proposed
>
> 更新日期：2026-07-25
>
> 架构形态：模块化单体、端口与适配器、单节点优先

## 1. 架构目标

Monaco 需要先成为协议正确、可恢复、可测试的 MQTT 5.0 Broker，再考虑集群和扩展生态。全新设计遵循以下原则：

1. MQTT 协议状态机不能依赖 Vert.x、Netty、RocksDB 或配置框架。
2. 网络连接与持久会话分离，Packet ID 只在单个连接方向内有意义。
3. 影响 QoS 的状态变更必须先原子持久化，再发送 ACK 或网络报文。
4. 同一 clientId 的命令串行执行，不同会话并行；核心路径禁止阻塞 Event Loop。
5. 核心正确性使用类型化调用，不依赖字符串 Channel 和无类型 EventBus 消息。
6. Gradle 模块代表代码所有权和依赖边界，不代表独立进程或微服务。

第一版只交付单节点。集群、桥接和远程插件必须通过端口接入，但不能污染单节点状态机。

## 2. 总体架构

    MQTT Client
         |
         v
    transport-vertx
      decode / encode
         |
         v
    +-----------------------+
    |         core          |
    | connection + session  |
    | publish + delivery    |
    | subscription + retain |
    | will + qos state      |
    +-----------------------+
       |       |       |
       v       v       v
     store   security telemetry
     ports    ports      port
       |       |         |
       v       v         v
    memory/  default   micrometer
    rocksdb

broker 是唯一 Composition Root，负责选择适配器、注入依赖和控制生命周期。所有模块最终运行在一个 JVM 中。

## 3. Gradle 模块拆分

建议使用以下根目录模块，发布 artifact 时统一添加 monaco- 前缀。

| 模块 | 核心职责 | 允许依赖 | 禁止包含 |
| --- | --- | --- | --- |
| protocol | MQTT 5 值对象、报文模型、属性规则、Reason Code、主题匹配、纯状态机 | Java 标准库 | Vert.x、Netty、Store、线程、网络 |
| core | Broker 用例、领域状态、入站端口、出站端口、并发协调 | protocol、vertx-core | vertx-mqtt、Netty、具体数据库、配置文件 |
| transport-vertx | TCP/TLS/WS 监听，Vert.x/Netty 报文与 protocol 模型互转 | core、protocol、Vert.x MQTT | 会话持久化、路由业务、ACL 规则 |
| store-memory | core 存储端口的内存实现 | core | Broker 业务判断 |
| store-rocksdb | core 存储端口的 RocksDB 实现、编码、迁移和恢复 | core、RocksDB | 网络和协议编排 |
| security-default | 匿名、用户名密码、文件 ACL 等默认安全实现 | core | Endpoint、数据库细节 |
| plugin-api | 面向第三方的稳定生命周期、Hook、Decision 和 Event DTO | protocol、vertx-core | core 内部状态、Endpoint、Store |
| plugin-runtime | 插件发现、ClassLoader、排序、生命周期、超时和 Hook Chain | core、plugin-api | MQTT 状态机 |
| plugin-remote-grpc | 独立进程插件的 gRPC 调用、事件流和健康检查 | plugin-runtime、plugin-api、gRPC | MQTT 状态机、Store |
| observability-micrometer | 指标端口实现、日志上下文、健康检查 | core、Micrometer | 业务状态变更 |
| broker | 配置加载、依赖装配、启动/停止、发行包和容器镜像 | core 与所选运行时适配器 | MQTT 业务规则 |
| testkit | Store 契约测试、协议场景 DSL、Broker 测试容器和客户端夹具 | protocol、core、store-memory | 生产运行时代码 |

### 3.1 目录结构

    broker/
    protocol/
    core/
    transport-vertx/
    store-memory/
    store-rocksdb/
    security-default/
    plugin-api/
    plugin-runtime/
    plugin-remote-grpc/
    observability-micrometer/
    testkit/
    docs/
    deploy/

现有 gateway、common 不作为新架构的长期模块。迁移期间可保留，但不得被新 core 反向依赖；原 app、extension-core 和 extension-protocol 已从 Gradle 模块清单及工作区移除。

logging 是独立的轻量级日志库，基于 java.util.logging 实现，作为 Log4j2 的可替换选项。它不属于旧实现，不随 gateway 一起废弃；任何模块均可选择依赖 logging 替代 Log4j2。

目标 settings.gradle.kts 只声明明确模块：

    include(
        "logging",
        "protocol",
        "core",
        "transport-vertx",
        "store-memory",
        "store-rocksdb",
        "security-default",
        "plugin-api",
        "plugin-runtime",
        "plugin-remote-grpc",
        "observability-micrometer",
        "broker",
        "testkit"
    )

唯一应用入口为 cn.elvis.monaco.broker.MonacoApplication，位于 broker。迁移期间旧模块继续使用原名称，新旧入口不会冲突。

### 3.2 依赖方向

以下箭头表示“左侧模块依赖右侧模块”：

    core ---------------------> protocol
    plugin-api ---------------> protocol + vertx-core
    transport-vertx ----------> core
    store-memory -------------> core
    store-rocksdb ------------> core
    security-default ---------> core
    observability-micrometer -> core
    plugin-runtime -----------> core + plugin-api
    plugin-remote-grpc -------> plugin-runtime + plugin-api
    broker -------------------> core + plugin-runtime + selected adapters
    testkit ------------------> protocol + core + store-memory

broker 位于依赖图最外层。任何适配器之间不得直接依赖，例如 transport-vertx 不能调用 store-rocksdb，security-default 不能访问 Vert.x Endpoint。testkit 只能作为 test fixture 或 testImplementation 依赖。

### 3.3 包与公共 API

| 模块 | 基础包 | 对外 API |
| --- | --- | --- |
| protocol | cn.elvis.monaco.protocol | packet、property、topic、qos、reason |
| core | cn.elvis.monaco.core | port.in、port.out、model |
| transport-vertx | cn.elvis.monaco.adapter.transport.vertx | VertxTransportFactory、TransportConfig |
| store-memory | cn.elvis.monaco.adapter.store.memory | MemoryBrokerStore |
| store-rocksdb | cn.elvis.monaco.adapter.store.rocksdb | RocksBrokerStore、RocksStoreConfig |
| security-default | cn.elvis.monaco.adapter.security | DefaultSecurityFactory |
| plugin-api | cn.elvis.monaco.plugin.api | 稳定插件接口和 DTO |
| plugin-runtime | cn.elvis.monaco.plugin.runtime | PluginRuntime、PluginLoader |
| plugin-remote-grpc | cn.elvis.monaco.plugin.remote.grpc | GrpcPluginSource |
| observability-micrometer | cn.elvis.monaco.adapter.observability | MicrometerTelemetry |
| broker | cn.elvis.monaco.broker | MonacoApplication |

core 的 service、mailbox 和 coordinator 实现不作为公共 API。适配器只公开工厂、配置和端口实现，不公开内部 DTO。

不能因为一个类被两个模块使用就放入 common。MQTT 语义归 protocol，Broker 状态和端口归 core，基础设施工具留在对应适配器；没有明确所有者的跨模块 utils 不允许新增。

### 3.4 构建约束

build-logic 作为 Gradle included build 存放统一的 Java 21、JUnit、编译器和测试套件约定，它不是生产模块。依赖版本集中在 gradle/libs.versions.toml。

- 库模块使用 java-library，只有稳定公共类型使用 api，其余依赖使用 implementation。
- broker 使用 application 插件，mainClass 固定为 cn.elvis.monaco.broker.MonacoApplication。
- testkit 通过 java-test-fixtures 暴露测试能力，不进入生产 runtimeClasspath。
- CI 增加 dependencyBoundaries 任务：protocol 不允许 Vert.x；core 只允许 vertx-core，不允许 vertx-mqtt、Netty、RocksDB 或 Micrometer。
- 单元测试、组件测试和端到端测试使用独立 source set，默认 check 至少运行单元和组件测试。
- gradle-wrapper.jar 必须提交，确保全新 checkout 可直接执行构建。

## 4. protocol 模块

protocol 是纯 Java 模块，负责表达 MQTT 5 语义，不负责 socket 编解码。

### 4.1 公共模型

- ClientPacket：Connect、Publish、PubAck、PubRec、PubRel、PubComp、Subscribe、Unsubscribe、PingReq、Disconnect、Auth。
- ServerPacket：ConnAck、Publish、PubAck、PubRec、PubRel、PubComp、SubAck、UnsubAck、PingResp、Disconnect、Auth。
- 值对象：ClientId、ConnectionId、PacketId、TopicName、TopicFilter、ShareGroup、QoS、Payload。
- 属性：每种属性使用明确类型；User Property 和多个 Subscription Identifier 保留顺序与重复值。
- 错误：ProtocolViolation 携带标准 Reason Code、响应报文类型和是否关闭连接。

报文和值对象应不可变。不得把 MqttEndpoint、MqttProperties、ByteBuf 或 JsonObject 暴露到模块外。

### 4.2 纯规则组件

- TopicNameValidator 与 TopicFilterValidator。
- TopicMatcher，包括 +、#、$SYS 和共享订阅解析规则。
- PropertySchemaRegistry，描述每类报文允许的属性、重复性和范围。
- InboundQos2StateMachine、OutboundQosStateMachine、EnhancedAuthStateMachine。
- PacketResponseFactory，仅生成规范允许的响应属性。

状态机采用纯函数形式：State + Event -> Transition。Transition 包含新状态和待执行 Action，持久化和网络发送由 core 编排。

## 5. core 模块

core 是 Broker 的唯一业务内核。它包含领域模型、用例实现和端口接口，但不知道适配器如何工作。

### 5.1 入站端口

transport-vertx 只允许通过 BrokerEngine 进入核心：

    interface BrokerEngine {
        Future<Void> opened(ConnectionHandle connection);
        Future<Void> received(ConnectionId id, ClientPacket packet);
        Future<Void> closed(ConnectionId id, DisconnectCause cause);
    }

opened 只表示物理连接建立。CONNECT 成功前不创建持久会话。received 返回的 Future 完成表示该报文产生的状态变更和必要发送动作已经处理完成。

### 5.2 出站端口

core 定义、适配器实现：

- ConnectionSink：发送 ServerPacket、刷新、按 Reason Code 关闭连接。
- BrokerStore：事务、启动恢复、过期数据清理。
- Authenticator：基础和增强认证。
- Authorizer：connect、publish、subscribe 权限判断。
- BrokerTelemetry：计数、耗时、状态量和协议错误。
- DomainEventSink：向扩展或审计系统发送已提交事件。
- BrokerClock 与 IdGenerator：时间和 ULID，测试时可替换。
- BrokerScheduler：按稳定 key 调度或取消 Keep Alive、Session Expiry、Will 和维护任务。

DomainEventSink 只发布事务提交后的通知。发布失败不得回滚已经完成的 MQTT 状态，也不得参与消息投递正确性。

BrokerScheduler 的 Vert.x 实现由 transport-vertx 提供并由 broker 注入；core 只保存 timer key，不持有 Vert.x timer id。

### 5.3 异步模型

core 和全部运行时端口统一使用 io.vertx.core.Future，不再引入 CompletionStage：

- 公共端口返回 Future，不把 Promise 暴露给调用方。
- 使用 compose、map、recover、eventually 和 Future.all 组合流程。
- transport-vertx 调用 core 时保留当前 Vert.x Context，不做 Future/CompletionStage 往返转换。
- RocksDB、文件认证和远程扩展等阻塞操作通过 executeBlocking 或有界 WorkerExecutor 执行，再返回 Future。
- 失败通过 Future 传播到 ProtocolErrorMapper 或生命周期管理器，禁止 join、get、await 和线程阻塞。

protocol 中的值对象、校验器和状态机仍是同步纯函数，因此 protocol 不依赖 vertx-core。

### 5.4 领域服务

| 服务 | 责任 |
| --- | --- |
| ConnectionService | CONNECT、AUTH、Keep Alive、连接接管和协商限制 |
| SessionService | Clean Start、Session Present、Expiry、断线与恢复 |
| PublishService | 入站 PUBLISH 校验、QoS 状态、Retain 和路由请求 |
| SubscriptionService | SUBSCRIBE/UNSUBSCRIBE、授权、索引更新和 Retain replay |
| RoutingService | 普通/重叠/共享订阅匹配，生成每客户端一个 DeliveryPlan |
| DeliveryService | 离线队列、Receive Maximum、出站 Packet ID、重发和 ACK |
| WillService | Will 保存、取消、调度和发布 |
| MaintenanceService | 会话、消息、Retain 和 Will 过期清理 |

不再使用通用 Manager 抽象。服务名称必须对应一个具体用例集合。

### 5.5 领域状态

- ConnectionState：瞬时，保存认证阶段、Keep Alive、协商限制和双向 Topic Alias。
- SessionState：持久，保存 clientId、expiryAt、订阅、Will 和连接代次。
- MessageRecord：Broker ULID、发布者、主题、Payload、QoS、属性和 expiryAt。
- DeliveryRecord：目标 clientId、messageId、最终 QoS、订阅标识和排队状态。
- InflightRecord：clientId、direction、packetId、messageId、QoS 状态和 DUP。
- RetainedRecord：每个 Topic Name 最多一条。

ConnectionState 不能持久化，Topic Alias 在每次网络连接结束时清理。SessionState 不能持有 Endpoint。

## 6. 并发与一致性

### 6.1 两级 Mailbox

- Connection Mailbox 保证一个物理连接上的报文按接收顺序处理。
- Session Mailbox 以 clientId 为键，保证连接接管、会话恢复、订阅和 Inflight 操作串行。
- Routing Coordinator 串行化订阅索引变更和匹配快照生成；投递到不同 Session Mailbox 后可并行。

Mailbox 只串联 Vert.x Future，不占用一个线程持续等待。一个 Mailbox 中不得同步等待另一个 Mailbox，跨会话工作拆成后续命令，避免死锁。

### 6.2 状态提交规则

遵循 persist before acknowledge：

1. 校验报文并计算状态转换。
2. 在 BrokerStore 事务中写入 Message、Inflight、Delivery 或 Session 变更。
3. 提交成功后更新可重建的内存索引。
4. 发送 PUBACK/PUBREC/PUBREL/PUBCOMP 或下一个 PUBLISH。
5. 发送失败按断线恢复规则保留状态。

QoS 0 允许不落盘；QoS 1/2、持久会话、Retain 和 Will 必须遵守该顺序。

### 6.3 Store 事务边界

    interface BrokerStore {
        <T> Future<T> transact(StoreOperation<T> operation);
        Future<RecoverySnapshot> recover();
        Future<Void> close();
    }

StoreOperation 在一个事务视图中读写 Session、Subscription、Message、Delivery、Inflight、Retain 和 Will。内存实现使用锁和不可变快照；RocksDB 使用 WriteBatch。事务回调不能泄漏到完成后的异步代码。

## 7. 关键运行时流程

### 7.1 启动

    broker load config
      -> open BrokerStore
      -> core recover snapshot
      -> rebuild topic index and timers
      -> start plugin runtime
      -> bind TCP/TLS/WS
      -> health status READY

任一步失败都必须关闭已启动资源并使进程退出。只有监听端口成功后才能记录 Broker started。

### 7.2 CONNECT

    Vert.x CONNECT
      -> transport maps ConnectPacket
      -> ConnectionService validates properties
      -> Authenticator
      -> Session Mailbox
      -> BrokerStore transaction
      -> ConnectionState installed
      -> ConnectionSink sends CONNACK
      -> DeliveryService resumes queued/inflight messages

同 clientId 已连接时，旧连接收到 Session Taken Over；使用 connection generation 防止旧连接的迟到 close 事件删除新连接状态。

### 7.3 入站 PUBLISH

    PUBLISH
      -> protocol/property/ACL validation
      -> inbound QoS state transition
      -> persist message/inflight
      -> Routing Coordinator creates DeliveryPlans
      -> target Session Mailboxes enqueue
      -> publisher ACK according to QoS state
      -> DeliveryService drains each target window

发布者 Packet ID 不进入 DeliveryRecord。每个目标连接由 DeliveryService 单独分配 Packet ID。

### 7.4 SUBSCRIBE

    SUBSCRIBE
      -> validate each filter
      -> authorize each filter
      -> transaction replaces accepted subscriptions
      -> atomically publish new topic-index snapshot
      -> build ordered SUBACK reasons
      -> replay retained messages according to Retain Handling

一次请求中部分过滤器可以失败；Reason Code 数量和顺序必须与请求一致。

### 7.5 断线与恢复

transport 将 Normal、Protocol Error、Keep Alive Timeout、Network Error 和 Server Shutdown 映射为 DisconnectCause。SessionService 据此决定清除或保留会话，WillService 决定取消、立即发布或延迟发布。重连后 DeliveryService 根据持久化 Inflight 重发 DUP PUBLISH 或 PUBREL。

## 8. 传输适配器

transport-vertx 使用成熟的 Vert.x MQTT/Netty 编解码器，不自行解析 MQTT 二进制帧。它只负责：

- 监听 TCP、TLS 和 WebSocket。
- 将 MqttEndpoint 事件映射为 protocol 报文。
- 实现 ConnectionSink，将 ServerPacket 编码为 Vert.x 调用。
- 管理 ConnectionId 到 Endpoint 的瞬时注册表。
- 将 codec、socket 和 TLS 错误转换为 DisconnectCause。

所有 Handler 在注册完毕后才接受 CONNECT。Transport.start 返回 Future，端口绑定成功前 broker 不得进入 READY。

## 9. 存储设计

store-memory 是所有 Store 行为的参考实现和单元测试实现。store-rocksdb 负责生产单节点持久化：

- Column Family：sessions、subscriptions、messages、deliveries、inflight、retained、wills、metadata。
- Key 使用稳定二进制编码，禁止 Java 原生序列化。
- Value 包含 schemaVersion；存储 DTO 不直接复用 core record。
- 一次 MQTT 状态转换使用一个 WriteBatch。
- RocksDB 调用运行在有界 worker executor，队列满时返回 Quota Exceeded 或 Server Busy。
- 启动恢复读取必要索引，不把所有 Payload 常驻内存。

每个 Store 实现必须通过 testkit 中同一套 BrokerStoreContract。

## 10. 安全与插件

core 只依赖 Authenticator、Authorizer 和 DomainEventSink。security-default 提供开发期匿名模式以及生产可用的哈希密码和 ACL 文件模式。

插件调用保持端口与适配器方向：

    core use case
         |
         v
    core outbound port
         |
         v
    plugin-runtime / Hook Chain
         |                    |
         v                    v
    trusted local JAR    plugin-remote-grpc
                              |
                              v
                       isolated plugin process

core 不依赖 plugin-api。plugin-runtime 同时依赖 core 和 plugin-api，将第三方 Hook 结果转换为 core 的认证、授权、策略和事件端口结果；broker 负责选择默认安全适配器、进程内插件和远程插件并完成装配。

plugin-api 只暴露稳定 DTO，不允许插件直接访问 SessionState、BrokerStore、Endpoint 或 core Service。plugin-runtime 实现 core 的安全、策略和事件端口，负责：

- 发现插件、校验 Manifest 和 API 版本，并隔离 ClassLoader。
- 为认证、授权和拦截调用建立确定顺序、超时和失败策略。
- 使用有界并发和熔断隔离故障插件。
- 对事件订阅实施缓冲上限和丢弃策略。
- 将插件异常或非法结果映射为明确的 Broker 决策。

决策 Hook 位于 CONNECT、PUBLISH、SUBSCRIBE 或 Will 的接纳路径中，并在持久化之前执行；修改结果必须重新经过协议校验和配额检查。已存在的 QoS 2 状态、Retain replay、离线投递及 Broker 出站重发只读取已经持久化的消息，不重复执行修改 Hook。QoS 1 入站仍是 at-least-once，插件必须具备幂等性。Domain Event 在事务提交后异步发送，失败不能改变 ACK、Store 或 Delivery 状态。

第三方进程内 Hook 默认运行在每插件有界 WorkerExecutor；超时、队列上限和熔断只隔离故障传播，无法构成安全沙箱。Enhanced AUTH 一旦选定 Provider，后续 AUTH 报文必须固定回调同一个插件。可靠审计需要独立事务 Outbox，普通插件 Event 只提供 best-effort 通知。

进程内插件只允许加载可信代码；不可信或跨语言插件通过 plugin-remote-grpc 独立运行。远程协议默认使用 gRPC；只有持续双向事件流证明需要 Request N 和 Resume 时再增加 RSocket 适配器。完整 Hook、生命周期、隔离和版本设计见 [MQTT 插件系统设计](plugin-system.md)。

## 11. 可观测性

BrokerTelemetry 位于 core 端口，observability-micrometer 实现。日志关联字段固定为 connectionId、clientId、packetType、packetId、messageId 和 reasonCode。禁止记录密码、Authentication Data 和 Payload。

健康状态：

- STARTING：Store 或恢复尚未完成。
- READY：至少一个配置的 MQTT Listener 已成功绑定。
- DEGRADED：非关键扩展或指标后端失败。
- STOPPING：停止接收新连接，正在关闭。

## 12. 测试边界

| 模块 | 必须覆盖 |
| --- | --- |
| protocol | 属性矩阵、主题规则、Reason Code、状态机全部转换 |
| core | 用例行为、事务顺序、Mailbox 并发、断线恢复 |
| store-* | 同一套契约、原子性、重启恢复、schema 迁移 |
| transport-vertx | 报文映射、启动失败传播、TCP/TLS/WS |
| broker | 配置、装配、生命周期和健康状态 |
| end-to-end | Paho/HiveMQ/Mosquitto 的 MQTT 5 互操作 |

测试不允许依赖固定 1883 端口；Listener 配置端口 0 后读取实际端口。测试必须等待 broker READY，而不是调用 start 后立即连接。

## 13. 现有架构图复用决策

现有分层图见 [layer.png](design/export/layer.png)，源文件为 [layer.excalidraw](design/layer.excalidraw)。该图可以作为旧实现的组件清单和迁移输入，但不能直接作为目标架构图：除 Settings 的委托箭头外，图中没有表达依赖方向、运行时调用链、状态所有权和事务边界。

### 13.1 组件迁移映射

| 图中组件 | 决策 | 新架构归属 |
| --- | --- | --- |
| TCP Transport | 保留能力，重写适配器 | transport-vertx |
| WebSocket Transport | 保留能力，和 TCP 复用同一 BrokerEngine | transport-vertx |
| Default/Environment/System Properties/File Settings | 保留配置来源，删除多层 Delegate 对象链 | broker 中的 BrokerConfigLoader |
| Client Session Manager | 拆分连接态和持久会话 | ConnectionService、SessionService |
| Publisher Manager | 拆分入站发布与目标投递 | PublishService、DeliveryService |
| Subscriber Manager | 拆分订阅管理与主题匹配 | SubscriptionService、RoutingService |
| Retain Message Manager | 保留领域能力，去除通用 Manager 抽象 | RetainService |
| Will Message Manager | 保留领域能力，统一走发布管线 | WillService |
| Packet Identifier Manager | 保留算法职责，不保留全局 Manager | DeliveryService 内的逐连接 PacketIdAllocator |
| Client Store | 重命名并修正模型边界 | BrokerStore 的 Session 事务视图 |
| Subscription Store | 保留持久化职责 | BrokerStore 的 Subscription 事务视图 |
| Retain Message Store | 保留持久化职责 | BrokerStore 的 Retained 事务视图 |
| Will Message Store | 保留持久化职责 | BrokerStore 的 Will 事务视图 |
| Message Store | 拆分消息本体、目标投递和 QoS 状态 | Message、Delivery、Inflight 事务视图 |
| Topic Alias Store | 删除 | Topic Alias 放在瞬时 ConnectionState |
| Topics Store | 删除独立持久化 | Routing Index 由 Subscription 恢复时重建 |

配置覆盖顺序统一为：启动参数或显式配置 > JVM System Properties > Environment > Config File > Default。加载完成后生成一个不可变 BrokerConfig，并在启动监听前完成集中校验。

### 13.2 Event 区域的处理

图中的事件名称可以作为领域事件词汇，但 EventBus 不再参与核心正确性：

| 原事件 | 新领域事件 | 约束 |
| --- | --- | --- |
| Client Close Event | SessionDisconnected | 包含 DisconnectCause 和 connection generation |
| Subscribe/Unsubscribe Event | SubscriptionChanged | Store 提交和 Routing Index 更新后发布 |
| Publish Event | MessageAccepted、MessageDelivered | 不代替路由、队列或 QoS ACK |
| Will Publish Event | WillPublished | Will 通过普通 PublishService 接受后发布 |

所有 Domain Event 都在 BrokerStore 事务提交后交给 DomainEventSink。事件发送失败不能回滚 MQTT 状态，也不能造成消息重复投递或 ACK 丢失。

### 13.3 不直接复用的结构

- Manager 不是一个模块边界；它被明确的领域服务替代。
- Event 不是与 Manager 并列的核心层；它是 plugin-runtime 和 observability 的外发端口。
- Store 不是一组互不协调的 Map 接口；跨 Session、Message、Delivery 和 Inflight 的变更通过 BrokerStore 原子事务提交。
- Settings 不进入 core；broker 完成加载和校验后只注入明确的配置值。
- Transport 不直接访问 Store，也不负责认证、会话、路由或 ACK 决策。

原图没有覆盖 protocol、属性合法性、Reason Code、Connection/Session 分离、QoS Inflight、Delivery、认证授权、Mailbox、persist-before-ack、可观测性和 broker Composition Root。这些内容必须出现在新的目标架构图中。

### 13.4 图源一致性

当前 Excalidraw 源文件和 PNG 导出内容不完全一致：源文件包含 Packet Identifier Manager 和 User Defined Settings，而导出图中的标签不同。后续以 layer.excalidraw 为编辑源，每次修改必须同步重新导出 PNG；CI 或评审应把“源图与导出图同时更新”作为文档完成条件。

## 14. 迁移策略

新架构采用旁路重建，不在现有 gateway Manager/Module 体系上继续扩展：

| 当前代码 | 迁移目标 |
| --- | --- |
| gateway topics、ACK、properties | 经规范测试后迁入 protocol |
| gateway session、manager | 按用例重写到 core，不直接复制 |
| gateway transport | 重写为 transport-vertx 适配器 |
| gateway memory store | 按 BrokerStoreContract 重写到 store-memory |
| common authentication | 收敛为 core 端口和 security-default |
| logging | 迁移期保留，作为 Log4j2 的轻量替代 | 可选依赖 logging 替代 Log4j2 |
| gateway 入口 | 替换为唯一 broker Composition Root |

实施步骤：

1. 保持旧模块可编译，创建 protocol、core 和 store-memory。
2. 用纯单元测试完成 CONNECT、主题、属性与 QoS 状态机。
3. 创建 transport-vertx 和 broker，打通内存版端到端测试。
4. 完成持久会话、QoS 2、Retain、Will 后接入 RocksDB。
5. 达到能力矩阵后删除旧 gateway/common 实现。logging 作为独立日志库保留。

迁移期间不允许新模块依赖旧模块；旧代码只能作为算法参考和回归测试来源。

## 15. 架构验收标准

1. Gradle 依赖图符合本文方向，不存在循环依赖。
2. protocol 不依赖 Vert.x；core 仅依赖 vertx-core，不出现 vertx-mqtt、Netty、RocksDB 或 Micrometer。
3. 使用 store-memory 可运行全部协议组件测试，替换 store-rocksdb 无需修改 core。
4. TCP 与 WebSocket 使用同一套 BrokerEngine 和测试场景。
5. Broker 启动、停止和监听失败通过 Vert.x Future 准确传播。
6. 任意 QoS 1/2 中间状态重启后，Broker 能从 Store 恢复。
7. 删除 EventBus 后不影响核心消息投递；事件系统只服务扩展和观测。

协议行为与阶段计划见 [MQTT 5.0 技术实现方案](mqtt5-technical-implementation.md)。
