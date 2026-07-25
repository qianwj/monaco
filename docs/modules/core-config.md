# core 模块 — 配置迁移与完善

> 任务编号：P0-6
> 状态：待实现
> 前置：P0-1 构建骨架（core 模块目录和 build.gradle.kts 已存在）
> 来源模块：gateway/settings

## 1. 目标

将 gateway 模块的 Settings 体系迁移到 core 模块，形成不可变的 `BrokerConfig` record。broker 模块负责加载和校验，core 只接收已校验的配置值。

## 2. 现有实现分析

### 2.1 gateway 中的配置类

| 文件 | 类型 | 职责 |
|---|---|---|
| `Settings.java` | interface | 定义所有配置访问方法 |
| `DefaultSettings.java` | class | 硬编码默认值 |
| `EnvironmentSettings.java` | class | 从环境变量读取，回退到 DefaultSettings |
| `TransportSettings.java` | record | TCP/WS 传输配置 |
| `MetricsSettings.java` | record | 指标配置 |

### 2.2 现有参数与默认值

**会话与协议：**

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| maximumSessionCount | int | 100 | 最大会话数 |
| serverAssignedClientIdentifier | boolean | false | 服务端分配 Client ID |
| maximumClientIdentifierLength | int | 23 | Client ID 最大长度 |
| defaultSessionExpiryInterval | int | 1,800,000 ms | 默认会话过期 |
| maxSessionExpiryInterval | int | 14,400,000 ms | 最大会话过期 |
| defaultReceiveMaximum | int | 10 | 默认接收窗口 |
| maxReceiveMaximum | int | 65535 | 最大接收窗口 |
| topicAliasMaximum | int | 500 | Topic Alias 上限 |
| maximumQualityOfService | int | 0 | 最大 QoS |
| wildcardSubscriptionAvailable | boolean | false | 通配符订阅 |
| subscriptionIdentifierAvailable | boolean | false | 订阅标识符 |
| sharedSubscriptionAvailable | boolean | false | 共享订阅 |
| retainAvailable | boolean | false | 保留消息 |
| serverKeepaliveIntervalMaximum | int | 30 秒 | Keep Alive 上限 |

**认证：**

| 参数 | 类型 | 默认值 |
|---|---|---|
| authenticationMode | enum | ALLOW_ANONYMOUS |
| fileAuthenticationPath | String | "authentication.json" |

**传输（TCP/WS 各一组）：**

| 参数 | 类型 | TCP 默认 | WS 默认 |
|---|---|---|---|
| enable | boolean | true | false |
| port | int | 1883 | 8883 |
| useTLS | boolean | false | false |
| instances | int | 1 | 1 |

**指标：**

| 参数 | 类型 | 默认值 |
|---|---|---|
| enable | boolean | false |
| exportJvmMetrics | boolean | false |
| endpoint | String | "/metrics" |
| port | int | 9095 |

### 2.3 现有问题

1. **时间单位需统一**：gateway 中 sessionExpiryInterval 使用毫秒，MQTT 协议报文使用秒。配置输入按秒（与协议一致），内部统一转为毫秒存储，方便定时器调度和 Keep Alive 1.5 倍超时计算（MQTT 5.0 §3.1.2.10）。
2. **环境变量前缀不一致**：部分有 `MONACO_` 前缀，部分没有。
3. **拼写错误**：`SHARD_SUBSCRIPTION_AVAILABLE` 应为 `SHARED_`。
4. **默认值不合理**：maximumQoS=0 和大部分能力为 false 过于保守。
5. **缺少参数**：maxPacketSize、maxQueueSize、TLS 证书路径、RocksDB 路径。
6. **TransportSettings 耦合 Vert.x**：`options()` 方法返回 `MqttServerOptions`。

## 3. 目标设计

### 3.1 core 中的 BrokerConfig

位于 `cn.elvis.monaco.core.config`，不可变 record。所有时间间隔使用 `java.time.Duration`，类型安全且自带单位转换，配置输入按秒（与 MQTT 协议一致），通过 `Duration.ofSeconds()` 构建：

```java
public record BrokerConfig(
    // 传输
    TransportConfig tcp,
    TransportConfig webSocket,
    // 会话
    int maxConnections,
    boolean serverAssignedClientIdentifier,
    int maxClientIdentifierLength,
    Duration defaultSessionExpiryInterval,
    Duration maxSessionExpiryInterval,
    int defaultReceiveMaximum,
    int maxReceiveMaximum,
    // 协议能力
    int topicAliasMaximum,
    int maxPacketSize,                  // 新增，字节
    int maxQueueSize,                   // 新增，每客户端离线队列
    Duration serverKeepAlive,           // Duration.ZERO 表示不强制覆盖
    QoS maximumQoS,                    // AT_MOST_ONCE, AT_LEAST_ONCE, EXACTLY_ONCE
    boolean retainAvailable,
    boolean wildcardSubscriptionAvailable,
    boolean subscriptionIdentifierAvailable,
    boolean sharedSubscriptionAvailable,
    // 安全
    String authMode,
    String authFilePath,
    // 存储
    String rocksdbPath,                 // 新增
    // 指标
    MetricsConfig metrics
) {
    /** Keep Alive 超时 = 1.5 倍 serverKeepAlive（MQTT 5.0 §3.1.2.10） */
    public Duration keepAliveTimeout() {
        return serverKeepAlive.plus(serverKeepAlive.dividedBy(2));
    }
}
```

### 3.2 子配置 record

```java
public record TransportConfig(
    boolean enabled,
    int port,
    boolean tlsEnabled,
    String tlsCertPath,      // 新增
    String tlsKeyPath,       // 新增
    int instances
) {}

public record MetricsConfig(
    boolean enabled,
    int port,
    String endpoint,
    boolean exportJvmMetrics
) {}
```

### 3.3 默认值工厂

```java
public final class BrokerConfigDefaults {
    public static BrokerConfig defaults() { ... }
    public static TransportConfig defaultTcp() { ... }
    public static TransportConfig defaultWebSocket() { ... }
    public static MetricsConfig defaultMetrics() { ... }
}
```

### 3.4 合理默认值调整

| 参数 | 旧默认 | 新默认 | 原因 |
|---|---|---|---|
| defaultSessionExpiryInterval | 1,800,000 ms | Duration.ofMinutes(30) | Duration 类型安全 |
| maxSessionExpiryInterval | 14,400,000 ms | Duration.ofHours(24) | 更合理的上限 |
| maximumQoS | QoS.AT_MOST_ONCE | QoS.EXACTLY_ONCE | MQTT Broker 应支持全部 QoS |
| wildcardSubscriptionAvailable | false | true | 标准能力 |
| subscriptionIdentifierAvailable | false | true | 标准能力 |
| sharedSubscriptionAvailable | false | true | 标准能力 |
| retainAvailable | false | true | 标准能力 |
| maxPacketSize | 无 | 268435456 (256MB) | MQTT 规范最大值 |
| maxQueueSize | 无 | 1000 | 合理离线队列 |
| serverKeepAlive | 30 | Duration.ZERO（不覆盖） | ZERO 表示不强制；keepAliveTimeout() 自动计算 1.5 倍 |
| WebSocket port | 8883 | 8083 | 8883 通常是 MQTTS |
| maxConnections | 100 | 100000 | 生产合理值 |

### 3.5 校验规则

BrokerConfig 应提供 `validate()` 方法或在 broker 模块的 loader 中校验：

- `maxPacketSize` 范围 [1, 268435456]
- `maximumQoS` 不能为 null（使用 `QoS` 枚举，类型安全）
- `defaultReceiveMaximum` 范围 [1, 65535]
- `maxReceiveMaximum` >= `defaultReceiveMaximum`
- `topicAliasMaximum` 范围 [0, 65535]
- `maxSessionExpiryInterval` >= `defaultSessionExpiryInterval`（Duration 比较）
- `serverKeepAlive` 非负且 toSeconds() 范围 [0, 65535]（Duration.ZERO 表示不覆盖）
- TCP 和 WS 至少启用一个
- TLS 启用时 cert/key 路径不能为空

## 4. 文件清单

### 4.1 新建（core 模块）

| 文件 | 说明 |
|---|---|
| `core/src/main/java/cn/elvis/monaco/core/config/BrokerConfig.java` | 不可变配置 record |
| `core/src/main/java/cn/elvis/monaco/core/config/TransportConfig.java` | 传输配置 record |
| `core/src/main/java/cn/elvis/monaco/core/config/MetricsConfig.java` | 指标配置 record |
| `core/src/main/java/cn/elvis/monaco/core/config/BrokerConfigDefaults.java` | 默认值工厂 |
| `core/src/main/java/cn/elvis/monaco/core/config/BrokerConfigValidator.java` | 校验逻辑 |
| `core/src/test/java/cn/elvis/monaco/core/config/BrokerConfigValidatorTest.java` | 校验测试 |
| `core/src/test/java/cn/elvis/monaco/core/config/BrokerConfigDefaultsTest.java` | 默认值测试 |

### 4.2 依赖

core 模块无外部依赖，仅使用 Java 标准库（`java.time.Duration` 等）。QoS 使用 int（0/1/2），不依赖 protocol 模块的枚举定义。

### 4.3 不迁移的部分

- `EnvironmentSettings` 的环境变量加载逻辑属于 broker 模块的 `BrokerConfigLoader`，不进入 core。
- `TransportSettings.options()` 和 `MetricsSettings.options()` 中的 Vert.x 对象构建属于 transport 适配器，不进入 core。

## 5. 验证

```bash
./gradlew :core:test
./gradlew :core:compileJava
```

- BrokerConfigDefaults.defaults() 返回的配置通过 validate()
- 各种非法配置组合被 validate() 拒绝并给出明确错误信息
- BrokerConfig record 不可变，所有字段通过构造器设置
