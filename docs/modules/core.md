# core 应用核心实现任务

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置：`core/config` 已实现（BrokerConfig、Loader、Validator）

## 1. 目标

`core` 是正式的 Gradle 模块名，包含 MQTT 5.0 Broker 的纯领域模型和稳定适配器端口。`command/state/transition/rule` 保持同步纯 Java；`port` 可以使用 Reactor `Mono`/`Flux`。core 依赖 `protocol` 与 `reactor-core`，但不依赖 Netty、数据库驱动或配置加载实现；BrokerEngine 与异步用例编排位于共享 `runtime`。

## 2. 现状分析

当前 `core` 模块内容：

| 包 | 内容 | 是否属于 core |
| --- | --- | --- |
| `config/` | BrokerConfig、Loader、Validator、TransportConfig、MetricsConfig | 仅 ProtocolLimits/BrokerPolicy 保留；加载器和 adapter config 迁出 |
| 当前 `runtime-reactor/port` | Clock、ID、Scheduler | 应迁入 core.port |
| 当前 `runtime-reactor/store` | 多个 CRUD Store、StateTransaction | 删除，以 core.port.BrokerStore 取代 |

## 3. 边界整改

- `BrokerConfigLoader`、TransportConfig、RocksDB path、认证文件和 MetricsConfig 迁至 broker/对应 adapter。
- runtime 中的 `SessionStore`、`SubscriptionStore`、`FlightWindowStore`、`WillStore` 和 `StateTransaction` 删除，由 core 的单一 `BrokerStore` 取代。
- domain Transition 继续直接接收 `Instant`、策略值和不可变状态，不调用 port，也不返回 Mono/Flux。
- 使用包边界测试禁止 `command/state/transition/rule` 导入 `reactor.*`。

## 4. 包结构设计

```text
cn.elvis.monaco.core/
├── config/                  # 仅 BrokerPolicy、ProtocolLimits
├── command/                 # 输入命令（由 transport/cluster 发出）
│   ├── ConnectCommand
│   ├── DisconnectCommand
│   ├── SubscribeCommand
│   ├── UnsubscribeCommand
│   ├── PublishCommand
│   └── PingCommand
├── state/                   # 会话与连接状态
│   ├── SessionState         # enum: CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED
│   ├── Session              # 会话聚合根
│   ├── Subscription
│   └── InFlightMessage
├── transition/              # 状态转换规则（纯函数）
│   ├── ConnectTransition
│   ├── DisconnectTransition
│   ├── SubscribeTransition
│   └── PublishTransition
├── action/                  # 转换产出的副作用描述
│   ├── SendPacketAction
│   ├── CloseConnectionAction
│   ├── DeliverMessageAction
│   ├── ScheduleWillAction
│   └── EmitEventAction
├── rule/                    # 业务校验规则
│   ├── ConnectRule           # Client ID 校验、协议级校验
│   ├── PublishRule           # QoS 上限、topic 格式、Retain 开关
│   ├── SubscribeRule         # 通配符开关、订阅标识符开关、共享订阅开关
│   └── PacketSizeRule        # 最大包大小校验
└── port/
    ├── BrokerStore           # Store adapter 实现
    ├── ConnectionSink
    ├── Authenticator / Authorizer / PolicyRuntime
    ├── BrokerClock / IdGenerator / BrokerScheduler
    └── DomainEventSink / BrokerTelemetry
```

## 5. 核心类型设计

### 5.1 Command（不可变，由外部传入）

```java
public sealed interface Command permits
        ConnectCommand, DisconnectCommand, SubscribeCommand,
        UnsubscribeCommand, PublishCommand, PingCommand {
    String clientId();
}

public record ConnectCommand(
        String clientId,
        boolean cleanStart,
        Duration sessionExpiryInterval,
        int receiveMaximum,
        int maxPacketSize,
        int topicAliasMaximum,
        String username,
        byte[] password,
        WillMessage will,
        int keepAlive
) implements Command {}
```

### 5.2 State（会话聚合根）

```java
public record Session(
        String clientId,
        SessionState state,
        Instant createdAt,
        Duration sessionExpiryInterval,
        int receiveMaximum,
        int maxPacketSize,
        int topicAliasMaximum,
        Map<String, Subscription> subscriptions,
        List<InFlightMessage> inFlight,
        WillMessage will,
        int keepAlive
) {
    public Session withState(SessionState newState) { ... }
    public Session withSubscription(Subscription sub) { ... }
    public Session withoutSubscription(String topicFilter) { ... }
}
```

### 5.3 Transition（纯函数，输入 Command + State，输出 State + Actions）

```java
public record TransitionResult(
        Session newState,
        List<Mutation> mutations,
        List<Action> postCommitActions) {}

public final class ConnectTransition {
    public static TransitionResult apply(
            ConnectCommand command,
            Session existingSession,  // nullable，新连接时为 null
            BrokerPolicy policy,
            Instant now,              // 由 runtime 层传入，domain 不持有 Clock
            String assignedClientId   // 由 runtime 层生成，domain 不持有 IdGenerator
    ) { ... }
}
```

### 5.4 Action（副作用描述，由 runtime 层执行）

```java
public sealed interface Action permits
        SendPacketAction, CloseConnectionAction, DeliverMessageAction,
        ScheduleWillAction, EmitEventAction {}

public record SendPacketAction(ConnectionRef connection, MqttPacket packet) implements Action {}
public record CloseConnectionAction(ConnectionRef connection, DisconnectReason reason) implements Action {}
```

### 5.5 Rule（校验规则，返回 Optional<RejectReason>）

```java
public final class ConnectRule {
    public static Optional<RejectReason> validate(ConnectCommand command, BrokerPolicy policy) { ... }
}
```

## 6. 依赖约束

```kotlin
// core/build.gradle.kts
dependencies {
    api(project(":protocol"))
    api(platform(libs.reactor.bom))
    api(libs.reactor.core)
    // 禁止：netty、数据库驱动和配置加载框架
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
```

## 7. 实施步骤

| # | 任务 | 退出标准 |
| --- | --- | --- |
| 1 | core 以 api 引入 reactor-core，建立稳定 adapter port | 只有 core.port 导入 Reactor |
| 2 | 定义 BrokerStore、StoreCommit、MutationBatch、CommitResult | revision/epoch/Outbox 语义完整，且不引用 cluster adapter 类型 |
| 3 | 实现 `Command` sealed interface 及 record | 编译通过 |
| 4 | 实现 Session/Connection 等不可变状态 | 编译通过 |
| 5 | 实现 `Action` sealed interface 及 action record 类型 | 编译通过 |
| 6 | 完成全部 Transition 与 Rule | 状态机分支测试通过 |
| 7 | 拆出基础设施配置，只保留 BrokerPolicy/ProtocolLimits | core 不含路径、证书、端口或数据库配置 |
| 8 | 增加包依赖测试 | 领域包无 Reactor/Netty，port 无 Netty |

## 8. 设计原则

1. **纯函数优先**：Transition 不持有可变状态，不做 I/O，输入确定则输出确定
2. **Command 不可信**：所有校验在 Transition/Rule 中完成，不依赖 transport 层已校验
3. **Action 是描述不是执行**：domain 层只描述"需要做什么"，runtime 层决定"怎么做"
4. **Session 不可变**：每次状态变更返回新 Session 实例
5. **Policy 注入而非全局**：Transition 接收 BrokerPolicy/ProtocolLimits，不读取基础设施配置
6. **包级纯度**：Reactor 是端口签名，不得进入领域状态转换
7. **端口归内层**：Store、安全、策略和观测适配器依赖 core，不依赖 runtime
