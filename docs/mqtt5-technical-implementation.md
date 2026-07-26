# Monaco MQTT 5.0 技术实现方案

> 状态：Draft
>
> 更新日期：2026-07-26
>
> 实现主线：全新模块化架构
>
> 协议基准：OASIS MQTT Version 5.0

## 1. 目标与范围

本项目目标是实现一个可部署、可验证的单节点 MQTT 5.0 Broker，支持 TCP、TLS 和 WebSocket 接入，并完整处理连接、会话、发布订阅、QoS、保留消息、遗嘱、认证授权及 MQTT 5 属性。

第一阶段不实现集群、桥接和跨节点共享订阅。这些能力必须通过端口预留扩展点，但不得增加单节点协议闭环的复杂度。客户端状态的所有权、持久化记录和恢复规则见 [客户端状态设计](mqtt5-client-state-design.md)，集群通信与部署方案见 [MQTT 5.0 集群设计](mqtt5-cluster-design.md)。现有 gateway 仅作为行为与测试参考；新实现按 [MQTT 5.0 架构设计](mqtt5-architecture.md) 旁路构建，完成能力迁移后删除旧实现。

## 2. 当前实现评估

### 2.1 可复用基础

- Netty MQTT codec 和 Reactor Netty 可提供 MQTT 报文编解码、TCP/WS 监听和异步运行时。
- gateway 已按 Transport、Session、Manager、Store 分层。
- 已有 MQTT 5 ACK、属性工具、主题树、内存 Store、指标和普通/增强认证雏形。
- 已覆盖 Clean Start、Topic、Topic Tree 等少量 JUnit 5 测试。

### 2.2 必须优先修复的基线问题

| 问题 | 影响 | 目标修复 |
| --- | --- | --- |
| Gradle wrapper JAR 缺失，gateway mainClass 指向不存在的类 | 无法形成可重复构建和启动基线 | 提交 wrapper JAR 的忽略例外，将入口统一为 cn.elvis.monaco.Application |
| Module.init 的依赖顺序与 SessionModule 读取顺序相反，且 EndpointHandler 未注册到 instances | Transport 无法取得有效 Handler | 使用显式构造器装配或类型化模块依赖，启动失败必须向上传播 |
| 会话对象持有 MqttEndpoint，断线时直接从 Store 删除 | Clean Start=false、离线消息和重连恢复无法实现 | 分离 Physical/Logical ConnectionState 与可持久化 SessionRecord |
| 时间字段混用毫秒和秒，多处过期判断方向相反 | 会话、消息和遗嘱过期错误 | 协议输入统一按秒解析，内部统一使用 Instant/Duration |
| QoS 2 在收到 PUBREL 前已路由；出站复用发布者 Packet ID | 破坏 exactly-once，多个订阅者间冲突 | 建立入站/出站独立 Inflight 状态机和逐连接 Packet ID 分配器 |
| 消息线程忙轮询，且 push 同时入队和直接发送 | CPU 空转、重复发送、ACK 前消息丢失 | 改为事件驱动 drain，ACK 后删除，窗口满时仅排队 |
| Topic Name 与 Topic Filter 校验混用，UNSUBSCRIBE 判断反向 | 非法主题被接受，合法退订被拒绝 | 建立两个独立 Validator，并为每条规范规则加测试 |
| 入站属性被复制到 ACK，属性重复性和报文适用范围未校验 | 产生非法 MQTT 5 报文 | 统一 PropertyValidator 与 PacketResponseFactory，只生成允许的响应属性 |
| Retain、Will、Enhanced AUTH 和共享订阅只有骨架 | 关键 MQTT 5 能力不闭环 | 按本文状态机逐阶段实现 |

## 3. 目标架构

采用单 JVM 的模块化单体和端口与适配器架构：

    MQTT Client
         |
    runtime
      transport.netty + use cases
       |                    |
       v                    v
    core -> protocol    runtime-standalone
      core ports              local profile
         |
       +----------+---------+----------+
       |          |           |          |
    store-*   security    plugins   observability

protocol 保存纯 MQTT 模型、校验和状态机；core 保存纯领域 command/state/transition/action 和稳定 Reactor 应用端口；runtime 保存 Broker 用例、提交管线、mailbox，并内置项目唯一支持的 Reactor Netty 客户端接入。runtime-standalone 提供 LocalDispatcher 和本地 Profile；store:memory、store:rocksdb、auth 模块、plugin-runtime 和 observability-micrometer 实现 core 端口。broker 是唯一依赖装配和生命周期入口。

### 3.1 并发模型

- Connection Mailbox 保证同一物理连接报文有序。
- Session Mailbox 保证同一 clientId 的状态变更串行，不同 clientId 可并行。
- Routing Coordinator 串行化订阅索引变更和路由快照，投递阶段按目标会话并行。
- Event Loop 上禁止文件、RocksDB、认证扩展等阻塞调用，core.port、runtime 及异步适配器统一返回 Reactor Mono；core 领域包保持同步纯 Java。
- 取消当前虚拟线程 busy-poll；新消息、ACK 和窗口释放通过事件触发 drain。
- Store 的复合操作必须原子化。内存实现使用事务锁和不可变快照，RocksDB 实现使用 WriteBatch。

## 4. 核心领域模型

### 4.1 Physical/Logical ConnectionState

两者仅在网络连接期间存在。PhysicalConnectionState 位于 Ingress，包含 endpoint、Keep Alive timer 和网络写队列；LogicalConnectionState 位于 Session Owner，包含 clientId、认证阶段、协商限制和双向 Topic Alias。单机可以把两者装配在同一节点，但类型和生命周期仍保持分离。

### 4.2 SessionRecord

以 clientId 为主键，只包含 revision、sessionExpiryAt、活动连接绑定和当前连接代次等有界元数据；Subscription、Will、离线 Delivery 与入站/出站 Inflight 使用独立记录并参与同一 Shard 事务。Clean Start=true 时原子清除旧会话；Clean Start=false 时恢复未过期会话并设置 Session Present。

### 4.3 MessageRecord

使用 Broker 生成的 ULID 作为 messageId，不使用发布者 Packet ID 作为全局标识。字段至少包括 publisherId、topic、payload、QoS、retain、properties、createdAt 和 expiryAt。转发时按目标订阅生成 DeliveryRecord。

### 4.4 InflightRecord

主键为 clientId + direction + packetId。入站状态为 RECEIVED_QOS2；出站状态为 PUBLISH_SENT_QOS1、PUBLISH_SENT_QOS2、PUBREL_SENT。记录 messageId、DUP、首次发送时间和重试次数。

### 4.5 BrokerStore

runtime 定义统一 BrokerStore 事务端口，事务视图覆盖 Session、Subscription、Message、Delivery、Inflight、Retain 和 Will。store-memory 与 store-rocksdb 通过同一套 Store 契约测试；业务层不得依赖具体存储。Topic Alias 属于 LogicalConnectionState，不进入持久化 Store。

## 5. 协议处理设计

### 5.1 CONNECT、CONNACK 与会话

1. 校验 MQTT 版本、Client ID、CONNECT Flags、属性适用范围、重复属性和取值范围。
2. 完成基础认证；存在 Authentication Method 时进入增强认证状态机。
3. 处理同 clientId 连接接管：向旧连接发送 Session Taken Over 后关闭旧连接。
4. 根据 Clean Start 和 Session Expiry Interval 清理或恢复 SessionRecord 及相关业务记录。
5. 协商 Receive Maximum、Maximum Packet Size、Topic Alias Maximum、Maximum QoS、Keep Alive 及可用能力。
6. 成功持久化会话后发送 CONNACK，再启动离线队列 drain。

Session Expiry Interval 允许为 0，单位为秒。Keep Alive 使用最后收到完整控制报文的时间，超出 1.5 倍后按异常断开处理。DISCONNECT 可更新 Session Expiry Interval。

### 5.2 属性处理

为每种控制报文维护允许属性、是否可重复、数值范围和响应传播规则。User Property 可重复；Subscription Identifier 在转发 PUBLISH 中可出现多个；Receive Maximum 和 Topic Alias 不得为 0。响应属性必须重新构造，禁止复制整个请求属性集合。

连接协商属性具有方向性，必须分别保存：

| 属性来源 | 表达的限制 | 执行位置 |
| --- | --- | --- |
| CONNECT Receive Maximum | 客户端可同时处理的 QoS 1/2 数量 | Broker 出站窗口 |
| CONNACK Receive Maximum | Broker 可同时处理的 QoS 1/2 数量 | Broker 入站校验 |
| CONNECT Maximum Packet Size | 客户端可接收的最大报文 | Broker 出站编码前 |
| CONNACK Maximum Packet Size | Broker 可接收的最大报文 | Broker 入站校验 |
| CONNECT Topic Alias Maximum | 客户端接受的最大别名 | Broker 出站别名表 |
| CONNACK Topic Alias Maximum | Broker 接受的最大别名 | Broker 入站别名表 |

Server Keep Alive 只能由服务端在 CONNACK 中下发，不能从 CONNECT 读取。服务端支持 QoS 2 时省略 Maximum QoS；仅支持 QoS 0/1 时才返回该属性。

当 Request Problem Information=0 时，不返回 Reason String 和诊断 User Property。Publication Expiry Interval 在每次转发和重发时写入剩余秒数。

### 5.3 发布与 QoS 状态机

入站 PUBLISH 先完成 Topic Name、Topic Alias、QoS、Packet ID、Maximum Packet Size、Payload Format 和发布 ACL 校验，再进入状态机。

| 方向/QoS | 处理流程 |
| --- | --- |
| 入站 QoS 0 | 校验后路由，不发送 ACK |
| 入站 QoS 1 | 消息和路由结果进入 Store 后发送 PUBACK；重复 PUBLISH 不得重复产生不可接受的副作用 |
| 入站 QoS 2 | 首次 PUBLISH 持久化为 RECEIVED_QOS2 并返回 PUBREC；收到 PUBREL 后仅路由一次，返回 PUBCOMP 并清理状态 |
| 出站 QoS 1 | 为目标连接分配 Packet ID，持久化 PUBLISH_SENT_QOS1 后发送；PUBACK 后释放 ID |
| 出站 QoS 2 | 持久化并发送 PUBLISH；PUBREC 后持久化 PUBREL_SENT 并发送 PUBREL；PUBCOMP 后释放 ID |

重连恢复时，PUBLISH_SENT 状态以 DUP=1 重发 PUBLISH，PUBREL_SENT 状态重发 PUBREL。每个客户端独立分配 1..65535 的出站 Packet ID。Receive Maximum 只统计未完成的 QoS 1/2 发送；窗口满时消息保留在队列中。

### 5.4 主题与订阅

- Topic Name 禁止 + 和 #；Topic Filter 允许规范位置的通配符。
- 主题和过滤器必须是合法 UTF-8，长度为 1..65535 字节且不得含 U+0000。
- + 匹配单层，# 只能独占末层；以通配符开头的过滤器不得匹配以 $ 开头的主题。
- 同一客户端多个过滤器命中时只投递一份消息，QoS 取发布 QoS 与最高授权订阅 QoS 的较小值，并附加所有命中的 Subscription Identifier。
- 应用 No Local、Retain As Published 和 Retain Handling。
- 共享订阅按 $share/{group}/{filter} 建立组索引，每组每条消息只选择一个当前可接收成员；首版使用 round-robin，离线成员可按配置跳过或排队。

SUBACK/UNSUBACK 必须为请求中的每个过滤器按原顺序返回 Reason Code。订阅替换应原子更新 SubscriptionRecord，提交后再更新派生主题索引。

### 5.5 保留消息

retain=1 且 payload 非空时按 Topic Name 覆盖 RetainedRecord；payload 为空时删除。新订阅根据 Retain Handling 决定是否发送，并应用订阅 QoS、RAP 和消息剩余有效期。过期记录在读取时过滤，并由后台任务清理。

### 5.6 遗嘱

CONNECT 成功前解析并保存 Will。正常 DISCONNECT 清除 Will；Disconnect with Will Message 立即发布；网络异常、Keep Alive 超时或协议错误按 Will Delay 调度。实际发布时间为 Will Delay 与 Session Expiry 的较早者。同 clientId 在调度期内恢复会话时取消遗嘱。遗嘱发布复用普通 PublishService，以保证 Retain、Expiry、ACL 和 QoS 行为一致。

### 5.7 AUTH 与授权

AuthenticationService 负责用户名密码和增强认证状态机；AuthorizationService 分别校验 connect、publish(topic) 和 subscribe(filter)。AUTH 仅允许在合法阶段出现，Authentication Method 在一次认证交换中必须一致。扩展调用设置超时、熔断和明确的失败 Reason Code，日志不得记录密码、认证数据或消息载荷。

### 5.8 错误处理

ProtocolErrorMapper 将内部错误映射为 MQTT 5 Reason Code 和动作：

- CONNECT 阶段使用 CONNACK 拒绝。
- 可按条目失败的 SUBSCRIBE/UNSUBSCRIBE 使用对应 ACK Reason Code。
- QoS 1/2 PUBLISH 在规范允许时返回 PUBACK/PUBREC 错误。
- Malformed Packet、Protocol Error、Receive Maximum Exceeded 等连接级错误发送 DISCONNECT 后关闭。
- 无法安全发送 DISCONNECT 的编解码或网络错误直接关闭，并触发 Will 规则。

业务代码不得抛出未映射异常到 Event Loop，也不得使用自定义非协议数值作为线上 Reason Code。

## 6. 持久化与恢复

首个可用版本使用 store-memory；生产单节点版本使用 store-rocksdb。需要持久化 Session、Subscription、Retained、Offline Queue、Inflight 和 Will。Topic Alias 与活动 Connection 不持久化。

一次协议状态转换的消息、Inflight 和队列变更必须在同一个 WriteBatch 中提交。启动时扫描未过期会话、遗嘱和保留消息，重建主题索引；连接恢复后再重发 Inflight。存储值需要 schemaVersion，升级时提供前向迁移，禁止依赖 Java 原生序列化。

## 7. 配置、安全与可观测性

配置至少包含监听地址、TCP/TLS/WS 开关、证书路径、最大连接数、会话有效期上限、Receive Maximum、Maximum Packet Size、Topic Alias Maximum、队列大小、单消息大小、认证/授权扩展和 RocksDB 路径。启动时集中校验，非法配置直接失败。

默认限制匿名访问仅用于开发环境。生产环境要求 TLS、客户端限速、连接/订阅/队列配额和发布/订阅 ACL。日志使用 clientId、connectionId、packetType、packetId、reasonCode 和 messageId 形成可关联上下文。

### 7.1 插件执行边界

插件拆分为 plugin-api、plugin-runtime 和 plugin-remote-grpc。runtime 定义 Authenticator、Authorizer、PolicyInterceptor 和 DomainEventSink 等端口；plugin-runtime 将进程内或远程 Hook Chain 适配到这些端口，broker 负责装配。全部异步接口返回 Reactor Mono。

- Authentication、Authorization、Connect、Publish、Subscribe 和 Will 属于持久化前的 Decision Hook，必须有超时、确定顺序和 fail-closed 结果。
- Publish 修改后必须重新校验 Topic、Payload、Property、QoS 和配额，再执行授权与 Store 事务。
- QoS 重发、离线投递、Retain replay 和恢复过程只使用 MessageRecord，不重新执行修改 Hook。
- Domain Event 只在事务提交后进入每插件有界队列，监听失败不回滚协议状态；可靠审计另用事务 Outbox。
- 第三方本地插件默认在有界 WorkerExecutor 上执行；不可信、跨语言或需要资源硬隔离的插件通过 gRPC 独立部署。

插件 Manifest、生命周期、Hook 合并、ClassLoader、版本和远程协议详见 [MQTT 插件系统设计](plugin-system.md)。

核心指标：

- monaco_connections_active、monaco_sessions_total
- monaco_publish_in_total、monaco_publish_out_total，按 QoS 和结果分类
- monaco_inflight、monaco_offline_queue_size、monaco_messages_dropped_total
- monaco_auth_failures_total、monaco_protocol_errors_total
- monaco_store_latency_seconds、monaco_event_loop_delay_seconds

## 8. 测试策略

### 8.1 自动化层次

- **单元测试**：Topic/Filter、PropertyValidator、PacketIdAllocator、所有 QoS 状态转换、Expiry 和 Reason Code 映射。
- **组件测试**：直接驱动 core + store-memory，验证并发、窗口和原子行为。
- **端到端测试**：启动 broker，使用 MQTT 5 客户端覆盖 CONNECT、AUTH、QoS 0/1/2、重连、Retain、Will、Alias、共享订阅和 WebSocket。
- **恢复测试**：在 QoS 每个中间状态停止 Broker，重启后验证不丢失且不重复交付。
- **互操作测试**：至少使用 Eclipse Paho/HiveMQ Client 与 mosquitto_pub/sub -V mqttv5 验证。

核心协议包目标行覆盖率不低于 85%，所有状态机分支和错误 Reason Code 必须有测试。测试类命名为 *Test 或 *Tests；新增测试使用 JUnit Assertions，禁止依赖 JVM assert。

### 8.2 必测场景

- Clean Start 与 Session Present 的四种组合，以及会话过期前后重连。
- QoS 1/2 重复报文、乱序 ACK、Packet ID in use 和连接中断恢复。
- Receive Maximum=1 的背压、Maximum Packet Size 和队列溢出。
- 重叠订阅、+/#、$SYS、No Local、RAP、Retain Handling、多 Subscription Identifier。
- Topic Alias 建立、复用、越界、0 和断线清理。
- Will Delay 与 Session Expiry 竞争、正常断开、接管和重连取消。
- 非法属性、重复单值属性、无效 UTF-8 和每类连接级错误。

## 9. 分阶段实施

### P0：建立可运行基线

- 修复并提交可复现的 Gradle wrapper。
- 创建 protocol、core、runtime、runtime-standalone、store-memory 和 broker 模块骨架；将 runtime-reactor 重命名为 runtime，并吸收现有 transport-reactor。
- 冻结旧 gateway 协议开发，新模块不得依赖旧模块。
- 完成应用生命周期、动态测试端口和 Broker READY 等待机制。

退出标准：全新 checkout 可用一条命令构建、测试并启动，TCP MQTT 5 客户端可完成 CONNECT/PING/DISCONNECT。

### P1：QoS 0/1 与订阅闭环

- 在 `runtime/transport.netty` 实现 PhysicalConnectionState，在 runtime/core 边界实现 LogicalConnectionState、SessionRecord、属性校验和错误映射；后两者不得持有 Channel 或 ByteBuf。
- 完成普通/通配符订阅、出站 Packet ID、QoS 0/1、背压和基础 ACL。
- 建立 plugin-api、plugin-runtime 骨架，以及认证、授权、Publish、Subscribe Hook 和提交后 Event。

退出标准：QoS 0/1、订阅替换、断线重连和错误 Reason Code 端到端测试通过。

### P2：持久会话与 QoS 2

- 完成 Inflight 状态机、离线队列、store-rocksdb 和崩溃恢复。
- 完成会话接管、Session Expiry 和 QoS 2 双向流程。
- 验证恢复、Broker 出站 DUP 重发和离线投递不会重复运行消息修改 Hook。

退出标准：在每个 QoS 2 中间状态重启 Broker，恢复后仍满足 exactly-once。

### P3：MQTT 5 完整能力

- 完成 Retain、Will Delay、Topic Alias、Subscription Identifier、请求/响应信息和 Enhanced AUTH。
- 完成共享订阅和所有订阅选项。
- 完成 Enhanced AUTH Provider 绑定、Will Hook、插件测试工具和 API 兼容性测试。

退出标准：本文必测场景全部自动化，Paho、HiveMQ 和 Mosquitto 互操作通过。

### P4：生产化

- TLS/mTLS、配额、限流、管理接口、数据迁移、指标告警和压力测试。
- 完成 plugin-remote-grpc、远程健康检查、deadline、mTLS 和故障隔离。
- 明确容量模型并验证长稳、慢消费者和存储故障。
- 达到能力矩阵后删除旧 gateway 和 common 实现。logging 作为独立日志库保留。

退出标准：发布检查表、容量报告、故障恢复演练和运维手册齐备。

## 10. 完成定义

一个功能只有同时满足以下条件才算完成：

1. 行为对应 MQTT 5.0 规范条款，合法与非法路径均有明确 Reason Code。
2. 状态变更可恢复，断线和 Broker 重启不会破坏 QoS 保证。
3. 单元、组件和端到端测试通过，且无 Event Loop 阻塞。
4. 配置、指标、日志和资源清理已实现，不记录敏感信息。
5. 相关文档与协议能力矩阵同步更新。

规范参考：https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html
