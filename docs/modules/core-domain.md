# core-domain 实现任务

> 状态：Proposed
>
> 更新日期：2026-07-25
>
> 前置：`core/config` 已实现（BrokerConfig、Loader、Validator）

## 1. 目标

将当前 `core` 模块定位为 `core-domain`，包含 MQTT 5.0 Broker 的纯领域模型：command、state、transition、action 和业务规则。**不依赖 Reactor、Netty 或任何 I/O 框架**，只依赖 `protocol` 模块和 Java 标准库。

## 2. 现状分析

当前 `core` 模块内容：

| 包 | 内容 | 是否属于 core-domain |
| --- | --- | --- |
| `config/` | BrokerConfig、Loader、Validator、TransportConfig、MetricsConfig | 是 |
| `port/out/BrokerClock` | 纯 Java `@FunctionalInterface` | 否，迁至 runtime-reactor |
| `port/out/IdGenerator` | 纯 Java `@FunctionalInterface` | 否，迁至 runtime-reactor |
| `port/out/BrokerScheduler` | 使用 `Mono<Void>`，依赖 Reactor | 否，迁至 runtime-reactor |

## 3. 需迁出

- `port/out/` 整个包 → `runtime-reactor` 模块（BrokerClock、IdGenerator、BrokerScheduler 都是运行时基础设施抽象）
- domain 层的 Transition 直接接收值参数（`Instant`、`String`），不定义 port 接口

## 4. 包结构设计

```text
cn.elvis.monaco.core/
├── config/                  # 已实现
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
│   ├── PersistSessionAction
│   ├── CloseConnectionAction
│   ├── DeliverMessageAction
│   └── ScheduleWillAction
└── rule/                    # 业务校验规则
    ├── ConnectRule           # Client ID 校验、协议级校验
    ├── PublishRule           # QoS 上限、topic 格式、Retain 开关
    ├── SubscribeRule         # 通配符开关、订阅标识符开关、共享订阅开关
    └── PacketSizeRule        # 最大包大小校验
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
public record TransitionResult(Session newState, List<Action> actions) {}

public final class ConnectTransition {
    public static TransitionResult apply(
            ConnectCommand command,
            Session existingSession,  // nullable，新连接时为 null
            BrokerConfig config,
            Instant now,              // 由 runtime 层传入，domain 不持有 Clock
            String assignedClientId   // 由 runtime 层生成，domain 不持有 IdGenerator
    ) { ... }
}
```

### 5.4 Action（副作用描述，由 runtime 层执行）

```java
public sealed interface Action permits
        SendPacketAction, PersistSessionAction, CloseConnectionAction,
        DeliverMessageAction, ScheduleWillAction {}

public record SendPacketAction(String clientId, MqttPacket packet) implements Action {}
public record CloseConnectionAction(String clientId, DisconnectReason reason) implements Action {}
```

### 5.5 Rule（校验规则，返回 Optional<RejectReason>）

```java
public final class ConnectRule {
    public static Optional<RejectReason> validate(ConnectCommand command, BrokerConfig config) { ... }
}
```

## 6. 依赖约束

```kotlin
// core/build.gradle.kts
dependencies {
    api(project(":protocol"))
    // 禁止：不能依赖 reactor-core、netty、任何 I/O 库
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
```

## 7. 实施步骤

| # | 任务 | 退出标准 |
| --- | --- | --- |
| 1 | 移除 `port/out/` 整个包（迁至 runtime-reactor） | core 的 `build.gradle.kts` 不含 `reactor-core` |
| 2 | 实现 `Command` sealed interface 及 6 个 record | 编译通过 |
| 3 | 实现 `SessionState` enum 和 `Session` 聚合根 | 编译通过 |
| 4 | 实现 `Action` sealed interface 及 action record 类型 | 编译通过 |
| 5 | 实现 `ConnectTransition`（最复杂） | 覆盖 cleanStart=true/false、sessionTakeover、willDelay、assignedClientId |
| 6 | 实现 `DisconnectTransition` | 覆盖 normal/abnormal disconnect、will 发布条件 |
| 7 | 实现 `SubscribeTransition` / `UnsubscribeTransition` | 通配符、共享订阅、订阅标识符规则 |
| 8 | 实现 `PublishTransition` | QoS 0/1/2 流程、retain 处理、topic alias |
| 9 | 实现 `Rule` 校验类 | 所有 BrokerConfig 约束都有对应测试 |
| 10 | 确认依赖图：core 不含 Reactor | `./gradlew :core:dependencies` 无 reactor-core |

## 8. 设计原则

1. **纯函数优先**：Transition 不持有可变状态，不做 I/O，输入确定则输出确定
2. **Command 不可信**：所有校验在 Transition/Rule 中完成，不依赖 transport 层已校验
3. **Action 是描述不是执行**：domain 层只描述"需要做什么"，runtime 层决定"怎么做"
4. **Session 不可变**：每次状态变更返回新 Session 实例
5. **Config 注入而非全局**：Transition 接收 BrokerConfig 参数，不静态引用
