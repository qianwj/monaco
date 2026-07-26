# Monaco MQTT 插件系统设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 异步模型：Reactor Mono/Flux
>
> 适用范围：单机与集群 Broker，预留远程插件
>
> 模块落地：[plugin-api 与 plugin-runtime 实现任务](modules/plugin.md)

## 1. 目标与边界

插件系统用于扩展认证、授权、连接策略、发布订阅策略、审计和运维能力，而不修改 Broker core。设计目标：

1. 插件 API 稳定，不暴露 MqttEndpoint、SessionState、BrokerStore 或 core 实现类。
2. 插件失败、超时和卸载不能破坏 MQTT QoS 状态机。
3. 影响协议结果的 Hook 与事务提交后的 Event 使用不同执行语义。
4. 插件调用全程使用 Reactor Mono/Flux，不阻塞 Reactor Netty EventLoop。
5. 可信插件可进程内运行；不可信或跨语言插件使用独立进程。
6. 决策 Hook 必须可重复调用且不产生不可逆副作用；外部通知只能消费提交后的 Event。

插件不能替换 MQTT 编解码、QoS 状态机、Packet ID、Session Expiry、Store 事务和路由索引。这些能力只能由 protocol/core 实现。

## 2. 模块拆分

| 模块 | 职责 | 依赖 |
| --- | --- | --- |
| plugin-api | 插件生命周期、Hook 接口、不可变 DTO、Decision 和 Event | protocol、reactor-core |
| plugin-runtime | JAR 发现、Manifest、ClassLoader、依赖排序、Hook Chain、超时、隔离和指标 | core、plugin-api |
| plugin-remote-grpc | gRPC 握手、远程 Hook 调用、事件流、健康检查和重连 | plugin-api、plugin-runtime、gRPC/Protobuf |
| testkit | PluginHarness、模拟上下文、顺序/超时/兼容性契约测试 | plugin-api、plugin-runtime |

core 不依赖 plugin-api。core 只定义 Authenticator、Authorizer、PolicyInterceptor 和 DomainEventSink 等出站端口，由 plugin-runtime 实现和注入。

依赖方向：

    plugin-api ----------> protocol + reactor-core
    plugin-runtime ------> core + plugin-api
    plugin-remote-grpc --> plugin-runtime + plugin-api
    broker -------------> plugin-runtime + optional remote adapter

### 2.1 plugin-runtime 内部结构

    PluginRuntime
      |- PluginCatalog              descriptor、版本和运行状态
      |- PluginSource
      |    |- ServiceLoaderSource   进程内 Java 插件
      |    `- GrpcPluginSource      UDS/TCP 远程插件
      |- LifecycleCoordinator       校验、依赖排序、启动和停止
      |- HookRegistry               不可变 Hook Chain 快照
      |- PluginInvoker
      |    |- LocalPluginInvoker
      |    `- GrpcPluginInvoker
      |- EventDispatcher            每插件有界事件队列
      `- PluginTelemetry             延迟、错误、超时和熔断

plugin-runtime 定义两个内部 SPI，远程模块只实现 SPI，不把 gRPC 依赖带入基础运行时：

    interface PluginSource {
        Flux<PluginHandle> load();
    }

    interface PluginInvoker {
        <R> Mono<R> invoke(HookInvocation<R> invocation);
    }

ServiceLoaderSource 是 plugin-runtime 的内置实现；GrpcPluginSource 位于 plugin-remote-grpc，由 broker 通过 PluginRuntimeBuilder 注入。两种来源都归一化为包含 descriptor、hooks 和 invoker 的 PluginHandle。HookRegistry 只处理 PluginHandle，因而本地和远程插件共享排序、Chain budget、结果校验、失败策略、指标和 core 端口适配逻辑。

## 3. 插件包格式

每个插件使用独立目录：

    plugins/
      acl-file/
        plugin.yaml
        plugin.jar
        lib/
          dependency.jar
        config.yaml

plugin.yaml 至少包含：

    id: acl-file
    name: File ACL
    version: 1.0.0
    apiVersion: 1
    dependencies: []
    capabilities:
      - authentication
      - authorization

`required`、priority、timeout、并发、队列和失败策略由 Broker 部署配置声明，例如：

    plugins:
      acl-file:
        enabled: true
        required: true
        priority: 100
        timeout:
          decisionMs: 100
          eventMs: 500
        execution:
          maxConcurrency: 16
          maxQueueSize: 1024

id 在一个 Broker 实例内唯一且不可在运行时变化。apiVersion 不兼容、依赖缺失、依赖成环或 Manifest 非法时禁止加载。插件包不能自行把自己声明为 required 或放宽安全 Hook 的失败策略。

plugin.jar 必须包含 Java Service Provider 配置：

    META-INF/services/cn.elvis.monaco.plugin.api.lifecycle.MonacoPluginFactory

该文件只声明一个 MonacoPluginFactory 实现。plugin-runtime 为每个插件目录创建独立 ClassLoader，再调用 ServiceLoader.load(MonacoPluginFactory.class, pluginClassLoader)。未发现 Provider、发现多个 Provider、Provider 来自父 ClassLoader 或 Provider 与 Manifest capabilities 不一致时拒绝加载。

ServiceLoader 只负责本地插件实例化，不负责扫描目录、读取配置、依赖排序或生命周期。Manifest 是 id、版本、API 和能力的唯一来源，Broker 配置是 required、顺序和资源预算的唯一来源；不再提供 mainClass 反射入口，避免两套加载规则产生歧义。

插件配置位于其命名空间下。密码、Token 和私钥只允许通过环境变量或 Secret 文件引用，不直接写入 plugin.yaml。

第三方进程内插件统一使用每插件有界 Scheduler，不提供可配置的 event-loop 模式。队列或并发配额耗尽时，按对应 Hook 的失败策略立即返回，不能无限排队。

## 4. API 模型

### 4.1 生命周期

进程内插件通过 ServiceLoader 暴露 Factory，由 Factory 创建生命周期实例：

    interface MonacoPluginFactory {
        MonacoPlugin create();
    }

    interface MonacoPlugin {
        Mono<Void> start(PluginContext context);
        List<PluginHook> hooks();
        Mono<Void> stop();
    }

Factory 必须具有无参构造器，构造器和 create 不得执行文件、网络或其他阻塞操作；资源初始化统一放在 start 中。hooks 必须能在 start 前调用并返回静态、不可变列表。Manifest capabilities 必须与 hooks 返回的扩展点完全一致，多报或少报均视为加载错误。

PluginContext 只提供：

- 插件自身的只读配置。
- 带插件 id 的 Logger 和 Metrics。
- 只读 PluginClock 和受限 PluginScheduler。
- Broker 版本、Plugin API 版本和节点 id。

PluginContext 不提供 BrokerStore、Endpoint、RoutingService 或任意 core Service。第一版不提供可重入的 BrokerFacade；插件不能在 Hook 中直接再次调用 publish 或 disconnect。

plugin-runtime 通过适配器把 Hook Chain 接到 core 的出站端口：认证 Hook 适配 Authenticator，授权 Hook 适配 Authorizer，连接、发布、订阅和 Will Hook 适配对应 PolicyInterceptor，事件监听器适配 DomainEventSink。core 只认识这些端口，不认识 MonacoPlugin、Manifest 或 ClassLoader。

### 4.2 调用上下文

每次 Hook 收到不可变 PluginRequestContext：

- invocationId、connectionId、clientId。
- Principal 和认证属性。
- Listener 名称、远端地址和 TLS 信息。
- Message origin：CLIENT、WILL、SYSTEM。
- Trace id 和插件命名空间属性。

插件属性只能写入自己的 namespace，不能覆盖其他插件或 Broker 保留字段。

### 4.3 Decision

所有决策使用受限结果类型，不允许插件抛出任意 MQTT Reason Code：

- Abstain：该插件不处理。
- Allow：允许并携带可选的受限变更。
- Reject：拒绝并携带当前 Hook 允许的标准原因。
- Continue：仅用于 Enhanced Authentication，携带下一轮 Authentication Data。

plugin-runtime 校验结果并由 core 的 ProtocolErrorMapper 映射到合法 MQTT 5 Reason Code。

Decision Hook 应视为确定性的策略函数。MQTT QoS 0/1 允许重复到达，远程调用也可能在超时边界出现结果未知；invocationId 标识一次 Chain 调用，plugin-runtime 不自动重试决策 RPC。插件不得在 Hook 中直接发送不可撤销的外部请求；确需调用自有系统时必须自行保证幂等。审计、Webhook 和数据同步应消费提交后的 Event。

## 5. 扩展点

### 5.1 决策 Hook

决策 Hook 位于协议主链路中，必须等待结果：

| Hook | 调用时机 | 允许结果或修改 |
| --- | --- | --- |
| AuthenticationProvider | CONNECT 基础认证 | NotApplicable、Success(Principal)、Reject |
| EnhancedAuthenticationProvider | CONNECT/AUTH 交换 | Continue、Success、Reject |
| ConnectionInterceptor | 认证成功、创建 Session 前 | Allow、Reject、增加连接属性 |
| WillInterceptor | CONNECT 中保存 Will 前 | Allow、Reject、修改 Topic/Payload/User Property |
| PublishInboundInterceptor | Topic Alias 解析后、持久化前 | Allow、Reject、修改 Topic/Payload/User Property |
| SubscriptionInterceptor | 过滤器校验后、持久化前 | 每个过滤器 Allow/Reject、降低 Granted QoS |
| AuthorizationPolicy | 最终 Topic/Filter 确定后 | Abstain、Allow、Deny |
| ManagementCommandProvider | 管理面命令 | 命名空间内的命令结果 |

限制：

- Publish Hook 不能修改 clientId、Packet ID、DUP，不能提高 QoS。
- Subscription Hook 不能扩大或重写 Topic Filter，只能拒绝或降低 QoS。
- Plugin 修改 Topic、Payload 或 Property 后必须再次通过 protocol 校验和配额检查。
- Will 在 CONNECT 时运行 WillInterceptor；实际触发时不重复运行决策 Hook。

第一版不提供出站 Payload 修改 Hook。离线投递、Retain replay 和 QoS 重发必须复用已经持久化的 MessageRecord；按订阅者再次修改内容会破坏重发一致性和消息去重。需要按订阅者控制时，只允许返回投递 Allow/Deny，不允许修改报文。

### 5.2 通知 Event

通知发生在 Store 事务提交后，不影响原请求结果：

- BrokerStarted、BrokerStopping。
- SessionConnected、SessionDisconnected、SessionExpired。
- MessageAccepted、MessageDelivered、MessageDropped。
- SubscriptionChanged。
- RetainedChanged。
- WillScheduled、WillCancelled、WillPublished。
- PluginStateChanged。

每个事件包含 eventId 和 occurredAt。监听器必须幂等；Broker 不承诺 Event exactly-once。

Domain Event 是有界、best-effort 的扩展通知：Broker 在提交后崩溃可能导致事件未发送。需要可恢复审计或可靠外部投递时，应增加独立的事务 Outbox 端口和存储记录，不能把普通 Event 队列误当作可靠消息系统。

## 6. MQTT 调用顺序

### 6.1 CONNECT

    protocol validation
      -> AuthenticationProvider / EnhancedAuthenticationProvider
      -> ConnectionInterceptor
      -> WillInterceptor
      -> AuthorizationPolicy(connect)
      -> SessionService transaction
      -> CONNACK
      -> SessionConnected event

认证 Provider 使用 first-applicable：按顺序遇到 Success 或 Reject 即停止；全部 Abstain 时使用 auth:simple 的默认策略。

Enhanced Authentication 首轮返回 Continue 后，connectionId、Authentication Method 和 provider pluginId 必须绑定到认证交换状态；后续 AUTH 只回调同一个 Provider，不能重新执行整条认证 Chain。连接关闭、认证成功或失败时立即销毁该状态。

### 6.2 PUBLISH

    packet/property/quota validation
      -> resolve inbound Topic Alias
      -> inspect existing inbound QoS state
      -> PublishInboundInterceptor chain
      -> revalidate modified message
      -> AuthorizationPolicy(final topic)
      -> QoS state + BrokerStore transaction
      -> route and enqueue Delivery
      -> PUBACK/PUBREC when required
      -> MessageAccepted event

Broker 对已生成 MessageRecord 的 Retain replay、离线投递、出站 DUP 重发和 QoS 恢复不会重新运行 PublishInboundInterceptor。插件只能在消息首次进入 Broker、尚未生成持久 MessageRecord 时修改内容。

已存在的 QoS 2 Packet ID 直接复用持久化状态并返回相应 ACK，不再次调用 Hook。QoS 1 本身是 at-least-once，Broker 不承诺跨连接消除所有重复入站发布，因此 Hook 仍必须幂等；一旦生成 MessageRecord，后续路由和重发只使用已保存的修改结果。

### 6.3 SUBSCRIBE

    validate each Topic Filter
      -> SubscriptionInterceptor per entry
      -> AuthorizationPolicy(final entry)
      -> Subscription transaction
      -> Routing Index update
      -> SUBACK
      -> retained replay
      -> SubscriptionChanged event

部分过滤器可以失败，插件结果必须保留原请求顺序。

### 6.4 DISCONNECT 和 Will

断线处理不等待通知插件。SessionService 和 WillService 先提交清理、保留或 Will 调度状态，再异步发布 SessionDisconnected、WillScheduled 等 Event。Will 到期后直接进入 core PublishService，使用 CONNECT 时已经确认的消息内容。

## 7. Hook Chain 语义

### 7.1 顺序

启动时按以下规则生成不可变 Hook Chain：

1. 先对插件 dependencies 做拓扑排序。
2. 同一依赖层按 priority 从小到大。
3. priority 相同按 plugin id 字典序。

运行时请求只读取当前 Chain 快照，不在每条消息上重新排序。

### 7.2 合并规则

- Authentication：first-applicable。
- Authorization：deny-overrides；任一 Deny 立即终止，至少一个 Allow 才允许，否则进入默认策略。
- Connection/Publish/Will Interceptor：按顺序应用变更，任一 Reject 立即终止。
- Subscription：每个过滤器独立执行和合并。
- Event Listener：相互独立，一个监听器失败不阻止其他监听器。

### 7.3 超时和失败策略

| 类型 | 默认失败策略 | 超时结果 |
| --- | --- | --- |
| Authentication/Authorization | FAIL_CLOSED | 拒绝请求 |
| Connection/Publish/Subscription/Will | FAIL_CLOSED | Implementation Specific Error |
| Management Command | FAIL_CLOSED | 命令失败 |
| Domain Event | FAIL_OPEN | 记录失败并继续 |

每次调用同时受单插件 timeout 和整条 Chain budget 限制。security 类 Hook 不允许配置为 FAIL_OPEN。Event 使用每插件有界队列；队列满时按配置丢弃最新或最旧事件，并记录指标。

超时只能停止 Broker 等待，不能强制终止已经阻塞的 Java 代码。进程内插件连续超时后由熔断器摘除并标记 DEGRADED；需要 CPU、内存或系统调用硬隔离的插件必须使用远程进程。

## 8. 并发与状态

- Hook 在发起请求的 Connection/Session Mailbox 中异步串联，但不得阻塞线程。
- 插件实例默认必须线程安全，因为不同 clientId 会并行调用。
- plugin-runtime 不允许一个 Hook 同步等待 Broker 的另一个 Mailbox。
- 第三方进程内 Hook 通过每插件有界 Scheduler 调用；runtime adapter 在应用结果前切回原 Shard lane，插件不能直接占用 Reactor Netty EventLoop。
- Payload 默认只读；插件声明 publish-payload capability 后才接收 Payload。
- 插件不能在对象字段中保存 Endpoint、SessionState 或请求级 Buffer 引用。
- 插件自己的持久数据由插件管理，不能使用 BrokerStore 的 Column Family。

如果插件需要触发后续 Broker 动作，应在 Decision 中返回受限 PluginAction，由 core 在当前 Hook 完成后排队执行。第一版不提供任意 Service Locator。

## 9. 生命周期与装载

状态机：

    DISCOVERED -> VALIDATED -> LOADED -> STARTING -> ACTIVE
                                             |          |
                                             v          v
                                           FAILED <- STOPPING -> STOPPED

启动流程：

1. broker 加载并校验插件配置。
2. ServiceLoaderSource 扫描本地目录并验证 Manifest；GrpcPluginSource 读取远程端点配置。
3. 本地来源创建独立 PluginClassLoader 并加载唯一 Factory；远程来源完成 Handshake。
4. PluginCatalog 归一化 PluginHandle，校验 API 版本、capabilities 和依赖图。
5. LifecycleCoordinator 按依赖顺序启动本地实例并确认远程健康状态。
6. HookRegistry 编译并原子发布 Hook Chain 快照。
7. required 插件全部 ACTIVE 后，Broker 才能进入 READY。

停止时先停止 MQTT Listener，等待进行中的 Hook 到达超时或完成，原子清空 Chain，再按依赖逆序停止本地实例并关闭远程 Channel。远程进程由外部 supervisor 管理，Broker 不负责创建或杀死进程。

第一版不支持 JAR 热替换。配置更新需要重启 Broker；避免 ClassLoader 泄漏、旧 Hook 仍在执行和状态版本不一致。后续热更新必须使用 quiesce、drain、atomic swap 三阶段协议。

## 10. 隔离与安全

### 10.1 进程内插件

每个插件使用独立 ClassLoader。JDK、plugin-api、protocol 和 reactor-core 使用 parent-first；插件私有依赖使用 child-first，避免插件之间依赖冲突。ServiceLoader 必须显式使用该插件的 ClassLoader，不能扫描 Broker 全局 classpath；运行时同时校验 Provider 的 defining ClassLoader，防止父级 Provider 混入。

Java 25 没有可依赖的 SecurityManager 沙箱。进程内插件拥有与 Broker 相同的 OS 权限，只能安装可信代码。ClassLoader 解决依赖隔离，不解决恶意代码、System.exit、无限内存或本地文件访问。

### 10.2 远程插件

不可信或跨语言插件通过 plugin-remote-grpc 运行在独立进程或容器：

- Handshake：协商 API version、plugin id、capabilities 和健康状态。
- InvokeHook：执行带 deadline 的决策调用，不自动重试。
- EventStream：传输提交后事件，使用有界队列和显式流控。
- Health：探测存活、就绪和版本变化。
- 远程进程断开按对应 Hook 的失败策略处理。

同一主机默认使用 gRPC over Unix Domain Socket，例如 unix:///run/monaco/plugins/acl.sock。Socket 放在 Broker 与插件专用的运行目录，通过目录和文件权限限制访问，不使用公共临时目录。生产环境由 systemd、容器或其他 supervisor 启动插件进程并管理 Socket；required 插件在启动 deadline 内未完成 Handshake 时，Broker 不进入 READY。

跨主机使用 mTLS TCP。本地开发环境缺少 Netty epoll/kqueue native transport 时，也可以回退到 loopback mTLS TCP。gRPC adapter 使用 `Mono.create` 封装 callback，公共 API 不暴露 stub 或 CompletionStage。

gRPC 是默认远程协议，因为契约、跨语言生成和诊断工具成熟。只有事件吞吐证明需要消息级 Request N 和 Resume 时，再实现 RSocket transport；两者复用 plugin-api 语义。

### 10.3 ServiceLoader 与 UDS 的选择

ServiceLoader 和 Unix Domain Socket 不处于同一层：前者负责 JVM 内发现和实例化，后者负责独立进程 IPC。Runtime 同时支持两者：

| 维度 | ServiceLoader 本地插件 | gRPC over UDS 远程插件 |
| --- | --- | --- |
| 调用延迟 | 最低，适合高频 Hook | 有序列化和进程切换成本 |
| 故障与资源隔离 | 弱，ClassLoader 不是沙箱 | 强，可使用独立用户、容器和资源限制 |
| 语言 | Java/JVM | 跨语言 |
| 部署复杂度 | 低 | 较高，需要进程和 Socket 管理 |
| 推荐用途 | 官方、可信、低延迟插件 | 第三方、不可信、企业集成插件 |

第一阶段先交付 ServiceLoaderSource；PluginSource 和 PluginInvoker SPI 同期稳定。远程能力随后通过 GrpcPluginSource 增量接入，不改变 Hook API 和 core 端口。PUBLISH 等高频 Hook 优先使用可信本地插件；安全隔离优先时使用 UDS 远程插件。

## 11. 可观测性

每个插件至少暴露：

- monaco_plugin_state，标签 plugin_id/version。
- monaco_plugin_invocations_total，按 hook/result 分类。
- monaco_plugin_duration_seconds。
- monaco_plugin_timeouts_total、monaco_plugin_errors_total。
- monaco_plugin_event_queue_size、monaco_plugin_events_dropped_total。

日志固定包含 pluginId、hook、invocationId、clientId 和 duration。Authentication Data、密码和 Payload 默认禁止记录。

## 12. 版本与兼容性

- plugin-api 使用 SemVer；apiVersion 表示不兼容的主版本。
- Broker 只加载声明兼容当前 apiVersion 的插件。
- DTO 只允许增加可选字段；删除、重命名或改变语义必须升级主版本。
- 插件不得依赖 core、transport 或 Store artifact。
- plugin-runtime 启动时检查插件 JAR 是否打包了 plugin-api/protocol，发现重复类直接拒绝。

## 13. 测试策略

testkit 提供 PluginHarness：

- 构造 Connect、Publish、Subscribe 和 Event 上下文。
- 使用虚拟 PluginClock/PluginScheduler 验证 timeout。
- 验证依赖排序、优先级、first-applicable 和 deny-overrides。
- 验证修改后重新校验和 Reason Code 映射。
- 验证 Event 队列溢出不影响 MQTT 主链路。
- 验证 required/optional 插件启动失败和逆序停止。
- 验证 ServiceLoader Provider 缺失、重复、父级污染和 Factory 初始化失败。
- 验证 ClassLoader 依赖冲突与 API 版本拒绝。
- 使用同一契约测试验证 LocalPluginInvoker 和 GrpcPluginInvoker 的排序与失败语义一致。
- 对 UDS 远程插件执行 Handshake 失败、断连、超时、半开、重连和队列溢出测试。

## 14. 分阶段交付

### P1：API 与进程内运行时

- plugin-api、Manifest、MonacoPluginFactory、ServiceLoaderSource、生命周期和 ClassLoader。
- Authentication、Authorization、Publish、Subscription Hook。
- Domain Event、超时、失败策略和指标。

### P2：完整 MQTT Hook

- Enhanced AUTH、Will、管理命令。
- 配置 schema、插件依赖和 PluginHarness。
- 与持久会话、Retain、QoS 2 恢复场景联测。

### P3：远程插件

- plugin-remote-grpc、GrpcPluginSource、UDS、握手、deadline、事件流和 mTLS TCP。
- 故障隔离、健康检查和部署文档。

## 15. 验收标准

1. 删除全部插件后，MQTT 协议行为和 QoS 保证不变。
2. 决策 Hook 的超时、异常和非法返回值都有确定 Reason Code。
3. Domain Event 消费失败不会改变 Store、ACK 或 Delivery 状态。
4. 插件不能访问 Endpoint、SessionState、BrokerStore 或 core Service。
5. required 插件未启动时 Broker 不进入 READY。
6. 进程内插件冲突依赖不会污染 Broker 或其他插件。
7. 远程插件断开不会阻塞 Event Loop 或无限增长队列。
8. 本地与远程 PluginInvoker 对同一 Hook 输入产生一致的合并和失败语义。
