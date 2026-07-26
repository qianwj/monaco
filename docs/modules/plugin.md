# plugin-api 与 plugin-runtime 实现任务

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 目标模块：`plugin:api`、`plugin:runtime`
>
> 前置文档：[插件系统设计](../plugin-system.md)、[MQTT 5 架构](../mqtt5-architecture.md)、[模块详细设计](../mqtt5-module-design.md)

## 1. 目标与现状

`plugin:api` 提供第三方插件编译时依赖的稳定 Java API；`plugin:runtime` 负责在 Broker 内发现可信插件、隔离依赖、管理生命周期、执行 Hook Chain，并将插件结果适配为 `core.port`。两个模块统一使用 Reactor `Mono`/`Flux`，不引入 Vert.x `Future`、`CompletionStage` 或 Netty 类型。

当前两个模块只能产出空 JAR。实施前需要修正以下边界：

1. `plugin:api` 的 Reactor Core 必须使用 `api`，因为 `Mono`/`Flux` 会出现在公共签名中。
2. `plugin:api` 只依赖 `protocol` 和 Reactor Core，不依赖 `core`、`runtime`、配置解析器或日志实现。
3. `plugin:runtime` 依赖 `core` 与 `plugin:api`，但不能依赖具体认证、Store、Transport、Micrometer 或远程协议实现。
4. `plugin:remote` 不在本文实现范围内；`plugin:runtime.spi` 只预留类型化 `PluginSource` 与 `PluginInvoker`。

本文细化 `plugin-system.md` 的 Reactor 实现边界和交付任务。

## 2. 模块边界

```text
third-party plugin ----------> plugin:api
                                  |
plugin:runtime ---------------> plugin:api + core
      |                           |
      |                           `-- immutable DTO / Mono contracts
      `-- ServiceLoader / lifecycle / chain / core adapters

broker ----------------------> plugin:runtime + default core-port adapters
plugin:remote ---------------> plugin:runtime SPI + plugin:api
```

`plugin:api` 不暴露 `SessionRecord`、`BrokerStore`、`ConnectionSink`、Channel、ByteBuf 或 runtime Service。`plugin:runtime` 不解释 MQTT 状态机，也不直接写 Store；修改结果返回 runtime 后必须重新经过协议校验、配额检查和原子提交。

进程内插件只用于可信 Java 代码。独立 ClassLoader 解决依赖冲突，不是安全沙箱；不可信或跨语言插件必须由后续远程模块通过独立进程运行。

实现选型固定为：Manifest 使用 Jackson YAML 严格映射到 record 并拒绝未知字段；deadline 使用 Reactor `timeout`；bulkhead 与 circuit breaker 使用 Resilience4j Reactor adapter；API 二进制兼容由 japicmp 在 CI 中对比最近发布版本。这些库都是 `plugin:runtime` 或构建验证的 implementation/test 依赖，不能出现在 `plugin:api` 公共签名中。Manifest/config 还必须限制文件大小、嵌套深度和 YAML alias，插件数量、JAR 数量与单包大小也必须有启动期硬上限。

## 3. plugin-api 设计

### 3.1 包结构

```text
cn.elvis.monaco.plugin.api/
├── lifecycle/       MonacoPluginFactory、MonacoPlugin、PluginContext
├── descriptor/      PluginDescriptor、ApiVersion、Capability、PluginDependency
├── context/         PluginRequestContext、Principal、TlsInfo、MessageOrigin
├── hook/            认证、授权、连接、Will、Publish、Subscription、Event Hook
├── decision/        AuthenticationDecision、EnhancedAuthenticationDecision、PolicyDecision、AuthorizationDecision
├── model/           ConnectView、PublishView、WillView、SubscriptionView
├── event/           PluginEvent 及不可变事件 DTO
└── support/         PluginConfig、PluginLogger、PluginMetrics、PluginScheduler
```

DTO 使用 record、sealed interface 和不可变集合。Payload 使用防御性复制的 `PluginPayload`，不能把 Netty buffer、可写 `ByteBuffer` 或调用结束后失效的引用交给插件。

### 3.2 生命周期与描述符

```java
public interface MonacoPluginFactory {
    MonacoPlugin create();
}

public interface MonacoPlugin {
    PluginDescriptor descriptor();
    List<PluginHook> hooks();
    Mono<Void> start(PluginContext context);
    Mono<Void> stop();
}
```

Factory 构造器、`create()`、`descriptor()` 和 `hooks()` 必须无 I/O、快速且确定。资源初始化只能发生在 `start()`。`hooks()` 返回静态不可变列表；返回 `null`、重复 Hook ID 或未声明 capability 均由 runtime 拒绝。

插件包内的 `plugin.yaml` 只声明代码身份：`id`、`name`、`version`、`apiVersion`、`capabilities` 和 `dependencies`。`required`、priority、timeout、并发、队列和失败策略属于 Broker 部署配置，不能由插件包自行提高权限。

### 3.3 Hook 清单

| Hook | 输入 | 输出 | 合并规则 |
| --- | --- | --- | --- |
| `AuthenticationProvider` | CONNECT 凭证视图 | Abstain、Success、Reject | first-applicable |
| `EnhancedAuthenticationProvider` | CONNECT/AUTH 交换 | Continue、Success、Reject | 固定单一 Provider |
| `AuthorizationPolicy` | connect/publish/subscribe 动作 | Abstain、Allow、Deny | deny-overrides |
| `ConnectionInterceptor` | 认证后的连接视图 | Allow、Reject、受限属性修改 | 顺序应用、Reject 短路 |
| `PublishInboundInterceptor` | Alias 解析后的发布视图 | Allow、Reject、受限消息修改 | 顺序应用、Reject 短路 |
| `SubscriptionInterceptor` | 单个订阅项 | Allow、Reject、降低 QoS | 每项独立、保持原顺序 |
| `WillInterceptor` | CONNECT 中的 Will | Allow、Reject、受限消息修改 | 顺序应用、Reject 短路 |
| `PluginEventListener` | 提交后的事件 | `Mono<Void>` | 监听器相互隔离 |

代表性接口如下；所有 Hook 都接收同一个不可变 `PluginRequestContext`，不能自行查询 runtime 状态：

```java
public interface PluginHook {
    String hookId();
}

public interface AuthenticationProvider extends PluginHook {
    Mono<AuthenticationDecision> authenticate(
            PluginRequestContext context, AuthenticationRequest request);
}

public interface PublishInboundInterceptor extends PluginHook {
    Mono<PolicyDecision<PublishView>> intercept(
            PluginRequestContext context, PublishView publish);
}

public interface PluginEventListener extends PluginHook {
    Mono<Void> onEvent(PluginEvent event);
}
```

runtime 在加载时校验 `hookId` 在单个插件内唯一。Hook 方法只能在订阅 Mono 后执行逻辑，不允许在方法调用阶段提前执行 I/O。

第一版不提供出站 Payload 修改、任意 BrokerFacade、Store 访问或 Hook 内重新 publish/disconnect。Retain replay、离线投递、QoS 重发、Will 触发和恢复流程只读取已持久化结果，不重复运行修改 Hook。

### 3.4 Decision 与失败语义

Decision 使用每类 Hook 的封闭类型，不能让插件返回任意 MQTT Reason Code。Reject 只携带 API 定义的稳定原因枚举，runtime 再映射为 core 结果，由协议层选择合法 Reason Code。

```java
public sealed interface PolicyDecision<T> {
    record Allow<T>(T value) implements PolicyDecision<T> {}
    record Reject<T>(PluginRejectReason reason, String publicMessage)
            implements PolicyDecision<T> {}
}
```

`publicMessage` 必须经过长度和 UTF-8 校验，不能包含异常栈或敏感凭证。认证密码、Authentication Data、Token 和 Payload 的 `toString()` 必须脱敏。

## 4. plugin-runtime 设计

### 4.1 包结构

```text
cn.elvis.monaco.plugin.runtime/
├── config/          PluginRuntimeConfig、PluginDeployment、ManifestParser
├── catalog/         PluginCatalog、PluginHandle、PluginState、PluginFingerprint
├── source/          DirectoryPluginSource、ServiceLoaderSource
├── classloading/    PluginClassLoader、PluginClassPath、DuplicateApiDetector
├── lifecycle/       LifecycleCoordinator、DependencyGraph、StartupRollback
├── registry/        HookRegistry、HookSnapshot、EnhancedAuthBinding
├── invoke/          LocalPluginInvoker、PluginBulkhead、InvocationBudget
├── chain/           AuthenticationChain、AuthorizationChain、InterceptorChain
├── adapter/         core Authenticator/Authorizer/PolicyRuntime/EventSink adapters
├── event/           EventDispatcher、PluginEventMailbox、EventDropPolicy
├── telemetry/       PluginTelemetry、PluginHealth
└── spi/             PluginSource、PluginInvoker、PluginHandleFactory
```

`spi` 是远程模块唯一允许依赖的 runtime 包。Manifest parser、ClassLoader、catalog 和 chain 实现均为内部 API。

### 4.2 本地装载

每个插件目录包含 `plugin.yaml`、`plugin.jar`、可选 `lib/*.jar` 和独立配置文件。runtime 按以下顺序处理：

1. 规范化并校验目录路径，拒绝越过插件根目录的链接和重复 JAR。
2. 使用结构化 YAML parser 读取 Manifest，在执行插件代码前完成 schema/API version 校验。
3. 计算插件 JAR、依赖 JAR 和语义配置的 SHA-256 fingerprint。
4. 创建每插件 ClassLoader；JDK、`plugin-api`、`protocol`、Reactor 与日志门面 parent-first，私有依赖 child-first。
5. 显式调用 `ServiceLoader.load(MonacoPluginFactory.class, pluginClassLoader)`。
6. 要求恰好一个 Factory，且 defining ClassLoader 必须是该插件 ClassLoader。
7. 拒绝在插件 JAR/lib 中重复打包 `plugin-api`、`protocol` 或 Reactor 公共类。

ClassLoader 和 ServiceLoader 只用于进程内实例化。目录发现、Manifest、依赖排序、required 策略和生命周期不由 ServiceLoader 负责。

### 4.3 生命周期

```text
DISCOVERED -> VALIDATED -> LOADED -> STARTING -> ACTIVE
                                      |           |
                                      v           v
                                    FAILED <- STOPPING -> STOPPED
```

`LifecycleCoordinator` 先按 dependencies 拓扑排序，再按 deployment priority 和 plugin ID 稳定排序。`start()`/`stop()` 都受部署 deadline 限制。启动失败时停止已启动插件并关闭所有 ClassLoader/Scheduler；required 插件失败使 Broker 启动失败，optional 插件失败使状态变为 DEGRADED。停止顺序与启动依赖顺序相反。

第一版不支持热替换。Broker 停止时先停止接收新连接，等待在途决策 Hook 到 deadline，原子发布空 HookSnapshot，再停止插件。禁止旧 Hook 在新 ClassLoader 快照发布后继续接收请求。

### 4.4 调用隔离与 Hook Chain

每个插件拥有独立的有界执行资源。调用使用 `Mono.defer` 延迟执行，在专用 bounded scheduler 上订阅，并同时应用：

- 单插件 `maxConcurrency` 与 `maxQueueSize`。
- 单插件 timeout 与整条 Chain deadline，实际 timeout 取两者较小值。
- 取消传播、调用计数和耗时指标。
- 连续失败/超时熔断；半开探测不能放大并发。
- 决策 Hook 不自动重试。

禁止使用 Reactor 全局 `boundedElastic`，否则一个插件可能耗尽其他插件或 Broker 的阻塞预算。超时只停止 Broker 等待，不能保证终止恶意或永久阻塞的 Java 代码。

默认失败策略必须固化为：

| Hook 类型 | timeout、队列满、异常、熔断开启 |
| --- | --- |
| Authentication、Enhanced AUTH、Authorization | `FAIL_CLOSED`，不可配置为 fail-open |
| Connection、Publish、Subscription、Will | `FAIL_CLOSED`，返回受限的 implementation-specific 拒绝 |
| Event Listener | `FAIL_OPEN`，记录失败并继续 |

单插件 timeout 不能超过 Chain budget；budget 耗尽后不再调用剩余插件。安全 Hook 不允许通过部署配置覆盖为 `FAIL_OPEN`。

`HookRegistry` 在启动完成后构建不可变 `HookSnapshot` 并原子替换。请求只读取一次快照。Chain 合并算法必须由独立类实现，不能散落在 MQTT Handler 中。

### 4.5 core 端口适配

plugin-runtime 实现或装饰 `core.port` 中冻结的认证、授权、策略和事件端口：

```text
runtime use case
  -> core.port Authenticator / Authorizer / PolicyRuntime
  -> plugin-runtime adapter
  -> HookSnapshot
  -> PluginInvoker
```

默认安全实现由 broker 注入 adapter 作为 fallback；插件全部 Abstain 时调用 fallback，而不是在 plugin-runtime 中硬编码匿名或密码策略。plugin-runtime 不依赖 `auth:*`。

修改 Hook 只生成候选值。runtime 必须重新执行 Topic、Payload、Property、QoS 和配额校验，成功后才能构造 `StoreCommit`。插件异常不能直接映射网络报文，也不能绕过 persist-before-ACK。

Enhanced AUTH 首次返回 Continue 后，以 `ConnectionRef + Authentication Method` 绑定 plugin ID；后续 AUTH 只能调用同一 Provider。连接关闭、成功、拒绝和 timeout 都必须清理 binding。

### 4.6 提交后 Event

`EventDispatcher` 为每个插件维护独立有界 mailbox，并按单插件顺序消费。队列满时按 deployment 配置执行 `DROP_LATEST` 或 `DROP_OLDEST`，记录 dropped 指标；一个监听器失败不能阻塞其他插件。

普通 Plugin Event 是 best-effort：提交后进程崩溃可能丢失，消费失败不回滚 Store、ACK 或 Delivery。需要可靠审计时必须使用单独的事务 Outbox adapter。

### 4.7 集群一致性

会影响认证、授权或消息内容的插件在集群 Profile 中必须标记 required。`PluginRuntime` 输出排序稳定的 required-plugin fingerprint，包含 API major、plugin ID/version、capabilities、artifact hash 和非敏感语义配置 hash；Secret 只记录版本引用，不进入 hash 或日志。

节点 fingerprint 不一致时不能获得 assignment 或进入 READY。决策 Hook 只在 Session Owner 首次接纳消息时运行；远端路由、Follower apply、恢复和重发不得再次调用插件。

## 5. 任务拆分

### 5.1 plugin-api

| 编号 | 任务 | 产出 | 退出标准 |
| --- | --- | --- | --- |
| API-01 | 构建与依赖边界 | Reactor Core 改为 `api`；模块边界测试 | 公共 API 仅引用 JDK、protocol、Reactor |
| API-02 | 版本与描述符 | ApiVersion、PluginDescriptor、Capability、Dependency | ID/version/capability 校验覆盖 |
| API-03 | 生命周期 API | Factory、Plugin、Context、Config、受限 Logger/Metrics/Scheduler | 无 core/runtime/实现类型泄漏 |
| API-04 | 公共上下文与 Payload | RequestContext、Principal、TLS、origin、PluginPayload | 全部不可变、敏感字段脱敏 |
| API-05 | Decision 模型 | 认证、增强认证、授权和 Policy Decision | 非法组合无法表达或被统一拒绝 |
| API-06 | 基础 Hook | Authentication、Authorization、Connection、Publish、Subscription | 接口 Javadoc 明确调用点和幂等要求 |
| API-07 | 扩展 Hook 与 Event | Enhanced AUTH、Will、EventListener、事件 DTO | Event 不引用 core DomainEvent 类型 |
| API-08 | 兼容性与示例 | API contract、二进制兼容检查、最小示例插件 | 示例 JAR 可由 ServiceLoader 发现 |

API-01~API-05 是 runtime 开工前置；API-06 与 API-07 可以按 MQTT 能力分批冻结。API 发布后不能在同一 major 内修改现有方法签名、record component 或枚举语义。

### 5.2 plugin-runtime

| 编号 | 任务 | 前置 | 产出与退出标准 |
| --- | --- | --- | --- |
| RT-01 | 构建、内部 SPI 与配置模型 | API-01~API-03 | PluginSource/Invoker、严格配置校验，无远程依赖 |
| RT-02 | Manifest 与 Catalog | RT-01、API-02 | YAML schema、状态机、fingerprint；非法包不执行代码 |
| RT-03 | ClassLoader 与 ServiceLoaderSource | RT-02、API-08 | 单 Provider、parent/child 规则、重复 API 检测 |
| RT-04 | 依赖图与生命周期 | RT-02~RT-03 | 稳定拓扑排序、启动回滚、逆序停止、required readiness |
| RT-05 | LocalPluginInvoker | RT-04、API-04~API-05 | 每插件有界并发/队列、timeout、取消、熔断、无自动重试 |
| RT-06 | HookRegistry 与 Chain | RT-05、API-06 | 不可变快照、first-applicable、deny-overrides、短路和 chain budget |
| RT-07 | core adapter | RT-06、core 端口冻结 | Auth/Policy/Event 适配，fallback 可注入，修改结果可重校验 |
| RT-08 | Enhanced AUTH binding | API-07、RT-07 | Provider affinity、代次校验、所有终止路径清理 |
| RT-09 | EventDispatcher | API-07、RT-05 | 每插件有界 mailbox、丢弃策略、监听器隔离 |
| RT-10 | Telemetry 与 Health | RT-04~RT-09 | state/invocation/timeout/queue/fingerprint 指标与脱敏日志 |
| RT-11 | Broker 生命周期装配 | RT-07~RT-10 | required ACTIVE 后 READY；drain/stop 顺序确定 |
| RT-12 | Contract 与故障测试 | RT-01~RT-11 | 本节第 6 节测试全部自动化 |

## 6. 测试规划

### 6.1 plugin-api 契约

- 使用包边界测试禁止 `core`、runtime、Netty、Vert.x、Jackson 和 Micrometer 类型进入公共 API。
- 反射验证公开 DTO 不暴露数组或可变集合；Payload 构造和读取均防御性复制。
- 验证 Reject reason、修改字段和 capability 的合法组合。
- 使用已发布基线 JAR 执行二进制兼容检查。
- 编译一个只依赖 `plugin:api` 的示例插件，验证 Service Provider 文件和公开 Javadoc。

### 6.2 plugin-runtime 单元与组件测试

- Manifest 缺失、未知字段、非法版本、重复 ID、依赖缺失和依赖环。
- Provider 缺失、多个 Provider、父 ClassLoader 污染、重复打包 API 和冲突依赖。
- 稳定排序、first-applicable、deny-overrides、逐项 Subscription 和修改链。
- 单插件/Chain timeout、队列满、取消、熔断开关与半开并发。
- required/optional 启动失败、部分启动回滚、逆序停止和 ClassLoader/Scheduler 释放。
- Enhanced AUTH Provider pinning、旧 ConnectionRef、断线和 timeout 清理。
- Event 顺序、DROP_LATEST/DROP_OLDEST、慢监听器和异常隔离。
- Store 提交失败不发布 Event；Event 失败不改变 ACK 或 Store。
- 集群 fingerprint 顺序稳定，代码或语义配置变化必然改变 fingerprint。

测试禁止使用固定 `sleep`。超时和定时场景使用 Reactor virtual time 或可控 PluginClock/PluginScheduler；ClassLoader 释放通过关闭状态、线程清单和可回收弱引用组合验证。

建议任务：

```bash
./gradlew :plugin:api:test
./gradlew :plugin:runtime:test
./gradlew :plugin:runtime:contractTest
./gradlew :broker:componentTest --tests '*Plugin*'
```

## 7. 实施顺序

```text
API-01 -> API-02 -> API-03 -> API-04 -> API-05
                                      +-> API-06 -> API-07 -> API-08

RT-01 -> RT-02 -> RT-03 -> RT-04 -> RT-05 -> RT-06 -> RT-07
                                                    +-> RT-08
                                                    +-> RT-09 -> RT-10
                                                                  `-> RT-11 -> RT-12
```

第一里程碑交付 API-01~API-06 与 RT-01~RT-07，打通基础认证、授权、Publish/Subscription Hook。第二里程碑交付 API-07、RT-08~RT-12，完成 Enhanced AUTH、Will、Event、集群 fingerprint 和生产生命周期。远程插件在上述契约冻结后单独排期。

## 8. 完成定义

1. `plugin:api` 公共签名只依赖 JDK、protocol 和 Reactor，第三方插件无需 core/runtime 即可编译。
2. `plugin:runtime` 只通过 `core.port` 接入 Broker，不访问 Store、Netty Channel 或 MQTT Handler。
3. 所有决策 Hook 都有确定顺序、总 deadline、失败策略和合法结果校验。
4. Hook 修改在持久化前重新校验；恢复、重发和远端路由不重复执行修改 Hook。
5. 每个本地插件拥有独立 ClassLoader 和有界执行资源，停止后资源可释放。
6. required 插件未 ACTIVE 或集群 fingerprint 不一致时 Broker 不进入 READY。
7. Event 队列有界且失败不影响 Store、ACK、Delivery 或其他插件。
8. 不加载任何插件时，Broker MQTT 行为与直接使用默认 core-port adapter 完全一致。
