# runtime-reactor 实现任务

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置：`protocol` ✅、`core` 领域层进行中
>
> 当前实现评审：[架构与实现评审](runtime/architecture-review.md)；结论为需要重新设计，本文中的完成标记不能作为集群阶段验收结果。

## 1. 目标

`runtime-reactor` 是单机和集群共用的 Broker 运行模块。它使用 Reactor `Mono`/`Flux` 编排连接、会话、路由和投递，并内置项目唯一支持的 Reactor Netty MQTT TCP/TLS/WebSocket 接入。standalone 和 cluster 复用同一运行时，只替换 Dispatcher 和 Store adapter。

## 2. 职责边界

| 属于 runtime-reactor | 不属于 runtime-reactor |
|---|---|
| BrokerEngine 入口（opened/received/closed） | 纯领域模型（command/state/transition → core） |
| Connection Processor 生命周期 | Broker peer 通信（→ cluster-transport-rsocket） |
| Shard lane 串行化调度 | 集群分片路由（→ cluster-runtime） |
| Use case 编排（connect、publish、subscribe...） | 持久化实现（→ store-*） |
| 运行时端口定义（BrokerClock、IdGenerator、BrokerScheduler） | 认证实现（→ security-default） |
| CommandDispatcher 抽象（Local/Remote） | |
| timeout、backpressure、错误处理 | |
| Reactor Netty TCP/TLS/WS、MQTT codec、Channel registry | |

## 3. 依赖

```
runtime-reactor → core (api)
runtime-reactor → protocol (传递自 core)
runtime-reactor → reactor-core (api)
runtime-reactor → reactor-netty-core (implementation)
runtime-reactor → netty-codec-mqtt (implementation)
```

不依赖：Vert.x、RSocket、数据库驱动。Netty 类型只能在 `transport.netty` 包使用，不能出现在公共 API。

## 4. 包结构

```text
cn.elvis.monaco.runtime/
├── engine/                  # BrokerEngine 入口
│   ├── BrokerEngine         # interface: opened/received/closed
│   └── DefaultBrokerEngine  # 实现：分发到 processor
├── connection/              # 连接处理器
│   ├── ConnectionRef        # connectionId、generation、ingressNode
│   ├── LogicalConnectionState
│   ├── ConnectionProcessor  # 单连接状态机
│   └── LogicalConnectionRegistry
├── session/                 # 会话处理
│   ├── SessionProcessor     # clientId 维度串行处理
│   ├── ShardLane            # 固定数量 shard 调度
│   └── ShardMailbox         # 有界、单消费者、显式拒绝策略
├── handler/                 # 报文处理器
│   ├── ConnectHandler
│   ├── DisconnectHandler
│   ├── PublishHandler
│   ├── SubscribeHandler
│   ├── UnsubscribeHandler
│   ├── PacketRouter
│   └── ActionExecutor
├── dispatch/                # 命令派发
│   ├── CommandDispatcher    # interface（Local/Cluster 实现）
│   └── LocalDispatcher      # standalone 本地派发
├── port/                    # 运行时基础设施端口
│   ├── BrokerClock          # 时钟抽象
│   ├── IdGenerator          # ULID 生成
│   ├── RuntimeScheduler     # 定时任务
│   ├── ConnectionSink      # 按 ConnectionRef 发包/关闭
│   ├── PolicyRuntime       # 认证、授权、插件策略
│   └── StateStore          # Shard-local 原子事务
└── transport/netty/         # 唯一 MQTT 客户端接入实现
    ├── ReactorMqttServer
    ├── NettyPacketMapper
    ├── NettyConnectionSink
    ├── PhysicalConnectionState
    └── PhysicalConnectionRegistry
```

## 5. 核心接口

```java
public interface BrokerEngine {
    Mono<Void> opened(ConnectionRef connection, ConnectionSink sink);
    Mono<Void> received(ConnectionRef connection, ClientPacket packet);
    Mono<Void> closed(ConnectionRef connection, DisconnectCause cause);
}

public interface CommandDispatcher {
    Mono<CommandResult> dispatch(PartitionTarget target, SessionCommand command);
}

public interface StateStore {
    Mono<CommitResult> transact(ShardId shardId, long expectedEpoch,
                                MutationBatch mutations);
}
```

`SessionCommand`、`CommandResult`、`PartitionTarget`、`ConnectionRef` 和 `MutationBatch` 必须是可测试、可映射到 cluster wire 的类型，不能接受 lambda、`Object` 或 Netty 类型。一次 command 只有一个原子提交点；提交成功后才执行发送、关闭、timer 和领域事件。

## 6. 并发模型

- **Connection Processor** — 同一物理连接通过 `concatMap(..., 1)` 保持报文有序
- **Shard Lane** — 固定数量 lane，按 shardId 选 lane；有界 mailbox 在上一个异步 command 终止后才取下一个
- 禁止 `parallel()`/`parallelFlux()` 并发修改 Session

## 7. 实现任务

### 7.1 端口与基础设施（从 core 迁出）

| 类 | 状态 |
|---|---|
| `port/BrokerClock` | ✅ 可保留，需纳入新端口包 |
| `port/IdGenerator` | ✅ 可保留，需纳入新端口包 |
| `port/RuntimeScheduler` | ⬜ 由现有 BrokerScheduler 重构 |
| `port/ConnectionSink` | ⬜ 增加完整 ConnectionRef 定位 |
| `port/PolicyRuntime` | ⬜ 待冻结 |

### 7.2 Store 事务端口

| 类 | 状态 |
|---|---|
| `port/StateStore` | ⬜ 待实现 Shard-local `transact` |
| `MutationBatch`/`CommitResult` | ⬜ 待实现 |
| 现有分散 `*Store` 端口 | ❌ 废弃，不能提供原子提交 |

### 7.3 Engine 与连接

| 类 | 状态 |
|---|---|
| `engine/BrokerEngine` | ⬜ 按 ConnectionRef 重定义 |
| `engine/DefaultBrokerEngine` | ⬜ 按 persist-before-send 重写 |
| `connection/ConnectionRef` | ⬜ 待实现 |
| `connection/LogicalConnectionState` | ⬜ 待实现 |
| `connection/ConnectionProcessor` | ⬜ 待实现 |
| `connection/LogicalConnectionRegistry` | ⬜ 待实现 |

### 7.4 Session 与调度

| 类 | 状态 |
|---|---|
| `session/SessionProcessor` | ⬜ 待实现 |
| `session/ShardLane` | ⬜ 待实现 |
| `session/ShardMailbox` | ⬜ 替换当前 subscribeOn 串行假设 |

### 7.5 报文处理器（handler/）

| 类 | 状态 |
|---|---|
| `handler/ConnectHandler` | ⬜ 修复 takeover 定位与事务顺序 |
| `handler/DisconnectHandler` | ⬜ 纳入原子提交与 generation 校验 |
| `handler/PublishHandler` | ⬜ 接入 LogicalConnectionState |
| `handler/SubscribeHandler` | ⬜ 接入 LogicalConnectionState |
| `handler/UnsubscribeHandler` | ⬜ 接入 LogicalConnectionState |
| `handler/PacketRouter` | ⚠️ 可保留路由骨架，需适配新 command |
| `handler/ActionExecutor` | ⬜ 重写为 commit 后 Action 执行器 |

### 7.6 命令派发

| 类 | 状态 |
|---|---|
| `dispatch/CommandDispatcher` | ⬜ 改为 typed command/result |
| `dispatch/LocalDispatcher` | ⬜ 接入有界 ShardMailbox |

### 7.7 Reactor Netty 接入

| 类 | 状态 |
|---|---|
| `transport/netty/ReactorMqttServer` | ⬜ 待从 transport-reactor 合并 |
| `transport/netty/NettyPacketMapper` | ⬜ 待实现 |
| `transport/netty/NettyConnectionSink` | ⬜ 待实现 |
| `transport/netty/PhysicalConnectionRegistry` | ⬜ 待实现 |

## 8. 测试清单

| 测试 | 覆盖 |
|---|---|
| `DefaultBrokerEngineTest` | opened/received/closed 分发 |
| `ConnectionProcessorTest` | 连接状态机、报文路由 |
| `ShardLaneTest` | 串行保证、hash 分配 |
| `ConnectUseCaseTest` | 认证、Session 创建/接管 |
| `PublishUseCaseTest` | QoS 0/1/2 投递 |
| `LocalDispatcherTest` | 本地命令分发 |
| `ReactorMqttServerTest` | TCP/TLS/WS 生命周期和真实动态端口 |
| `NettyBoundaryTest` | 非 transport 包不能引用 io.netty 类型 |

## 9. 验收标准

1. `./gradlew :runtime-reactor:build` 编译通过
2. `core` 模块 `build.gradle.kts` 移除 `reactor-core` 依赖（domain 纯 Java）
3. Reactor Netty 和 Netty MQTT 使用 `implementation`；Netty 类型只存在于 `transport.netty`
4. 所有 Store 端口返回 `Mono`/`Flux`
5. BrokerEngine 可被内置 ReactorMqttServer 调用，且无 socket 测试仍可直接驱动 Engine
6. settings 和 broker runtimeClasspath 中不再包含独立 `transport-reactor`
7. Store 提交失败不发送 MQTT ACK/Delivery，takeover 只关闭旧 ConnectionRef
8. Local/Cluster Dispatcher 可通过同一 typed-command contract，接口中不存在 lambda 或 Object command
