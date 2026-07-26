# Monaco MQTT 5.0 集群设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 运行时方案：Reactor Core + Reactor Netty
>
> 集群通信：RSocket 数据与控制平面
>
> 存储方案：共享 PostgreSQL 或 Apache Ratis + RocksDB
>
> 部署范围：Docker、Docker Compose、Docker Swarm、Kubernetes

本文记录集群技术决策、协议语义与选型依据。组件关系见 [集群架构设计](mqtt5-cluster-architecture.md)，客户端连接、会话、QoS 和恢复状态见 [客户端状态设计](mqtt5-client-state-design.md)，RSocket route、Protobuf 报文与错误码见 [集群通信协议](mqtt5-cluster-protocol.md)，Gradle 模块、SPI 和任务拆分见 [集群模块详细设计](mqtt5-cluster-module-design.md)。

## 1. 目标与原则

Monaco 必须使用同一套 `protocol + core + runtime` 同时支持单机和集群。部署平台只负责进程编排、网络和 Secret，不参与 MQTT 正确性判断。集群设计遵循以下原则：

1. 运行模式由配置显式选择，不根据容器环境自动推断。
2. Session、QoS、Will 和 Delivery 的正确性仍由状态机、Store 事务及 fencing 保证。
3. DNS、心跳和网络连接只能表示可达性，不能单独证明节点拥有状态写权限。
4. 节点通信使用类型化端口和 Reactor Mono，不把 EventBus address 或具体 RPC 类型暴露给 core/runtime。
5. 所有跨节点消息均允许重复，接收端依靠稳定 id 和 Store 唯一约束实现幂等。
6. 节点失效不能导致旧 Owner 恢复后继续写入，也不能产生无界内存队列。

第一版以 3 至 10 个 Broker 节点为容量目标。更大规模需要在压测后引入 Topic Interest Summary、路由分层或专用路由节点。

本文扩展 [MQTT 5.0 架构设计](mqtt5-architecture.md) 的单节点边界；MQTT 状态机、Store 事务和插件 Hook 语义仍分别以总架构、技术实现方案和 [MQTT 插件系统设计](plugin-system.md) 为准。

## 2. 运行模式

| 模式 | 状态存储 | 集群端口 | 适用场景 |
| --- | --- | --- | --- |
| standalone-memory | store-memory | 不启动 | 测试和开发 |
| standalone-rocksdb | store-rocksdb | 不启动 | 单机生产 |
| cluster-shared-store | store-postgres | RSocket TCP | 共享数据库集群 |
| cluster-replicated | Apache Ratis + RocksDB | RSocket + Raft TCP | 不依赖外部数据库的复制集群 |

建议配置：

    broker.mode=cluster
    cluster.id=production-a
    cluster.node-id=broker-0
    cluster.incarnation=<generated-uuid>
    cluster.bind-host=0.0.0.0
    cluster.bind-port=18883
    cluster.advertise-host=broker-0.monaco-headless
    cluster.transport=rsocket
    cluster.partition-count=1024
    cluster.data-shard-count=128
    store.type=postgres

cluster.id、partition-count 和 data-shard-count 创建后不能在线修改。bind address 用于监听，advertise address 用于其他节点连接；禁止发布 0.0.0.0、localhost 或仅宿主机可见的地址。

## 3. 总体架构

    MQTT Client
         |
         v
    Ingress Broker
         |
         | local call / peer transport
         v
    Session Partition Owner
         |
         +----------> selected Store profile
         |             PostgreSQL or Ratis + RocksDB
         |
         `----------> Route Partition Owners
                       regular + shared delivery

集群分成四个平面：

- 控制平面：节点注册、租约、分区分配、epoch、drain 和版本协商。
- 会话平面：将任意入口节点上的连接命令发送到 clientId 对应的 Session Owner。
- 路由平面：把已经接受并持久化的 Publication 分发给目标分区 Owner。
- 持久化平面：保存 Session、Inflight、Message、Delivery、Will、Retain、租约和 Outbox。

PostgreSQL 是 shared-store profile 的状态和协调基线。Broker 高可用不自动意味着数据库高可用；生产部署必须另行提供 PostgreSQL HA、备份和恢复方案。replicated-store profile 使用第 10 节的 Ratis + RocksDB。多个 Broker 不能共享 RocksDB 目录，也不能把 RocksDB 放到 NFS 或共享 Volume 上并发打开。

## 4. 模块拆分

本节只列出集群阶段的模块增量；完整基础模块拆分、依赖方向、公共端口和实施任务以 [集群模块详细设计](mqtt5-cluster-module-design.md) 为准。

| 模块 | 职责 | 主要依赖 |
| --- | --- | --- |
| cluster-protocol | peer/Raft Protobuf schema、版本化 envelope 和 codec | protocol、Protobuf |
| runtime-cluster | 成员视图、分区表、Session Dispatcher、Publication Fanout、drain | runtime、core、cluster-protocol |
| cluster-transport-rsocket | peer connection、Session channels、Route channels、Lease、Resume、mTLS | runtime-cluster、cluster-protocol、RSocket |
| cluster-coordination-postgres | 节点租约、分区 assignment、epoch 和 coordinator 选举 | runtime-cluster、PostgreSQL client |
| store-postgres | core BrokerStore 事务、Outbox 原子写入、幂等约束和恢复 | core、PostgreSQL client |
| cluster-outbox-postgres | Outbox claim、ack、retry 和 drain | runtime-cluster、PostgreSQL client |
| cluster-consensus-ratis | Metadata/Data Raft Group、复制日志和 snapshot | runtime-cluster、cluster-protocol、Ratis |
| store-rocksdb-sharded | ShardStateStore、WriteBatch、checkpoint 和恢复 | runtime-cluster、core、RocksDB |
| cluster-testkit | 集群传输、协调、存储和故障契约测试 | runtime-cluster、cluster-protocol、testkit |

这些是进入集群阶段后新增的目标模块，按实施阶段逐步加入 `settings.gradle.kts`。不允许 `core` 或 `runtime` 为尚未实现的传输建立反向依赖。

依赖方向：

    cluster-protocol ----------------> protocol
    runtime-cluster -----------------> runtime + core
    cluster-transport-rsocket -------> runtime-cluster + cluster-protocol
    cluster-coordination-postgres ---> runtime-cluster
    store-postgres ------------------> core
    cluster-outbox-postgres --------> runtime-cluster
    cluster-consensus-ratis ---------> runtime-cluster + cluster-protocol
    store-rocksdb-sharded -----------> runtime-cluster + core
    broker --------------------------> runtime + runtime-cluster + selected adapters

runtime-cluster 定义传输端口，唯一生产实现为 RSocket：

    interface PeerTransport {
        Mono<SessionChannel> openSessionChannel(SessionTarget target);
        Mono<RouteAck> route(RouteBatch batch);
        Mono<Void> sendControl(ControlCommand command);
    }

core 和 runtime 不依赖任何 Profile 模块。broker 在 standalone 模式装配 runtime-standalone，在 cluster 模式装配 runtime-cluster；两个 Profile 互不依赖。

## 5. 节点身份、成员与 Fencing

每个运行实例使用以下身份：

- clusterId：隔离不同集群，证书和所有请求都必须携带或绑定该值。
- nodeId：节点逻辑名称，可来自 StatefulSet ordinal、Swarm Task 或显式配置。
- incarnation：每次进程启动生成的新 UUID，防止旧进程和新进程被视为同一实例。
- endpoint：其他节点可访问的 advertise host 和 port。
- capabilities：协议版本、功能位、插件指纹和最大报文限制。

shared-store profile 的节点定期使用数据库时间更新 lease。租约过期只使节点成为失效候选；Data Shard 必须在协调器写入新 assignment 和递增 epoch 后才能由新 Owner 接管。replicated-store profile 由 Metadata Raft Group 和 Leader term 提供对应语义。

`PartitionTable` 保存 `partitionType + partitionId -> shardId` 映射和 generation；Data Shard assignment 至少包含 shardId、ownerNodeId、ownerIncarnation、epoch、assignmentGeneration 和 state。shared-store 的 Session/Inflight/Delivery 变更在数据库事务中验证 mapping generation 与 epoch；replicated-store 的写入由当前 Raft Leader/term 和多数派提交约束。旧 Owner 在两种 profile 中都不能继续提交。

DNS、Docker Service、Swarm DNSRR 和 Kubernetes Headless Service 只用于解析 endpoint。shared-store 以数据库 lease/assignment 为权威视图，replicated-store 以 Metadata Raft Group 为权威视图；两者都不再引入第二套 ClusterManager 成员状态。

## 6. Session 分区与连接转发

使用固定数量的逻辑 Partition，再通过 `PartitionTable` 定位物理 Data Shard：

    sessionPartition = stableHash("session", clientId) % partitionCount
    dataShard = partitionTable[SESSION, sessionPartition]

客户端可以连接任意 Ingress Broker，不要求负载均衡器理解 MQTT CONNECT：

1. `runtime/transport.netty` 解码 CONNECT 并取得 clientId。
2. clientId 为空时，Ingress 生成可在 Attach 重试中复用的 Assigned Client ID，再计算 Session Partition。
3. ClusterSessionDispatcher 查询本地 PartitionTable 与 assignment 快照。
4. Owner 是本节点时进入 runtime ShardProcessor；否则选择到目标节点的 Session lane。
5. Owner 执行完整协议校验、插件 Hook、Session takeover 和 Store 事务。
6. Owner 产生的 ServerPacket 经 Session lane 返回 Ingress，再由 `runtime/transport.netty` 编码发送。

实际 Netty Channel 始终留在 Ingress。Owner 只持有 `LogicalConnectionState` 和 ConnectionSink；ConnectionSink 可以是本地 transport，也可以是远程 SessionChannel，不能把 Channel、ByteBuf 或其他 Netty 对象序列化到集群协议。

节点间不为每个 MQTT 客户端创建物理连接。每对节点复用 RSocket connection，并以 request-channel 承载固定数量的逻辑 lane；connectionId 哈希选择 lane。消息包含 connectionId、connectionGeneration 和 sequence，保证同一连接有序并避免单 lane 队头阻塞。

Session Owner 或 lane 长时间失联时，Ingress 关闭对应 MQTT 连接，客户端通过标准重连恢复。第一版不承诺在两个 Broker 进程之间无损迁移现有 TCP 连接。

## 7. Publication 路由

第一版按 Session 分区维护订阅索引，而不是构建一个需要强一致复制的全局 Topic Trie。每个 Owner 只匹配自己负责分区中的普通订阅。

发布流程：

1. Session Owner 执行 Publish Hook、修改后校验和最终授权，解析 Topic Alias 并移除连接级属性。
2. MessageRecord、规范化 routed properties、入站 QoS 状态和 ClusterOutbox 在一个事务中提交；旧 Subscription Identifier 不跨 Shard 传播。
3. MQTT QoS 1/2 ACK 只在事务成功后发送；ACK 表示 Broker 已承担后续路由责任。
4. Outbox Dispatcher 将逻辑目标分区按当前节点批量发送。
5. 接收 Owner 匹配本地订阅并幂等写入 DeliveryRecord，然后返回 RouteAck。
6. Outbox 根据 RouteAck 标记完成；断线、超时或 assignment 变化时向当前 Owner 重试。

逻辑目标是 `PartitionKey(type, partitionId)`，不是固定 shardId 或 nodeId。网络层可以按节点批量，但 mapping/assignment 变化后未完成任务必须通过最新 PartitionTable 解析到新 Owner。messageId、routeTaskId 和 deliveryId 在重试中保持不变，接收端使用唯一约束消除重复副作用。

初始实现可能把 Publication 发送给所有持有分区的节点，网络放大约为 O(nodeCount)。后续可交换无假阴性的 Topic Interest Summary；它只能减少发送，不能成为正确性来源。

### 7.1 共享订阅

普通节点不能各自为同一共享组选择消费者，否则一条消息会被多个节点投递。共享组使用独立逻辑分区：

    sharedPartition = stableHash("shared", group, filter) % partitionCount

Shared Group Owner 保存组成员索引并执行 round-robin 或后续策略。消费者选择和 DeliveryRecord 使用 `(messageId, group, filter)` 唯一键在一个事务中提交，确保一次 Publication 在一个共享组中只选择一个成员。Owner 失效后，新 Owner 从 Store 恢复成员和选择状态。

### 7.2 Retain、Will 与插件

- RetainedRecord 位于共享 Store，SUBSCRIBE 由 Session Owner 读取并生成 Delivery。
- Will 到期后进入普通 PublishService 和 ClusterOutbox，不走特殊广播通道。
- Publish 修改 Hook 只在入口 Owner、首次持久化前执行。
- 跨节点路由、恢复、Retain replay 和出站重发不再次执行修改 Hook。
- required 插件及 API 版本指纹不兼容的节点不能加入同一 assignment generation。

## 8. Peer 协议

本节说明交互语义；字段编号、RSocket metadata、Session/Route frame、版本兼容和限制以 [集群通信协议](mqtt5-cluster-protocol.md) 为准。

每个请求或流帧至少携带 clusterId、source node/incarnation、target node、protocolVersion、requestId、timeoutMillis 和 trace context；分片请求额外携带 partitionType、partitionId、dataShardId、mappingGeneration 和 epoch。Payload 使用 Protobuf，不使用 Java 原生序列化。

Peer 交互模型：

| 服务 | RSocket 交互模型 | 职责 |
| --- | --- | --- |
| Handshake | request-response | 身份、版本、capabilities 和限制协商 |
| ClusterControl | request-response | drain、assignment hint 和诊断命令 |
| SessionLane | request-channel | 多连接复用的 ClientPacket、ServerPacket 和关闭信号 |
| RoutePublication | request-channel | Publication batch、RouteAck 和 sequence |
| Health | request-response/request-stream | 存活与就绪探测 |

RSocket 使用 Request N 实施已打开 stream/channel 内的逐帧背压，Lease 只实施新 request/channel 的 peer 准入。每个 peer、lane 和 partition 都有并发与队列上限，队列满时停止追加 demand，任务留在 Outbox，不能在 I/O EventLoop 或堆内无限等待。公共端口只暴露 Mono/Flux，不暴露 CompletionStage。

传输完成不是业务持久化证明。RouteAck 只能在接收端事务提交后返回，Session 命令的网络响应也必须遵守 persist-before-send。

Broker 节点始终使用 TCP，因为 Docker Swarm 和 Kubernetes 可能跨宿主机。Unix Domain Socket 只用于同主机插件等 IPC，不作为集群基线。

## 9. 通信与运行时选型

四个候选不完全处于同一层：EventBus 是消息总线，gRPC 是 RPC/Streaming 协议，RSocket 是 Reactive Streams 协议，Apache Pekko 是包含 Cluster 和 Sharding 的分布式 Actor 运行时。

| 维度 | Vert.x EventBus | gRPC | RSocket | Apache Pekko |
| --- | --- | --- | --- | --- |
| 请求响应 | 支持 | 原生 | 原生 | Ask Pattern |
| 双向流 | 需自行封装 | 原生 bidi stream | 原生 request-channel | Actor 消息或 Pekko Streams |
| 背压 | 需要自建 credit | HTTP/2 + 手动流控 | 原生 Request N、Lease | 需要 Streams 或有界 Mailbox |
| Resume | 无 | 应用层恢复 | 协议支持 | 依赖 Actor/Persistence 恢复 |
| 成员管理 | 依赖 ClusterManager | 不提供 | 不提供 | Pekko Cluster 提供 |
| Session Sharding | 不提供 | 不提供 | 不提供 | Cluster Sharding 提供 |
| 强类型契约 | 自定义 MessageCodec | Protobuf | 需选择 Protobuf 等格式 | Actor Protocol + Serializer |
| 跨语言 | 较弱 | 成熟 | 支持但生态较小 | 集群内部主要是 JVM |
| Reactor 集成 | 需要桥接 | callback 转 Mono | 原生 Mono/Flux | 常需 CompletionStage/Streams 适配 |
| 架构侵入性 | 中 | 低 | 低至中 | 很高 |
| 自带业务持久性 | 无 | 无 | Resume 不能替代 Store | 可组合 Pekko Persistence |
| 运维复杂度 | 中 | 低至中 | 中 | 高 |

### 9.1 Vert.x Clustered EventBus

Clustered EventBus 仍依赖网络和 ClusterManager。逻辑 address 无法证明 consumer 拥有最新 partition epoch；节点重启、consumer 尚未注册或 publish 期间断线时也不提供持久重放。它还会与 PostgreSQL lease 或 Ratis Metadata Group 形成第二套成员视图。

选择全 Reactor 架构后不再引入 EventBus。节点内可重建通知使用 Reactor Sink，关键链路使用类型化调用、RSocket 和持久 Outbox。

### 9.2 gRPC

gRPC 提供 Protobuf、deadline、状态码、mTLS、双向流和成熟诊断工具，但 gRPC Java 核心 API 使用 callback、Future stub 和 StreamObserver，不直接提供 Mono/Flux 或 Reactive Streams Request N。

在 Reactor 集群中使用 gRPC 需要额外 callback-to-Mono 桥接，并自行把 HTTP/2 flow control 映射为应用背压。这会形成第二套异步适配和流控语义，因此集群数据平面与控制平面都不使用 gRPC，也不创建 `cluster-transport-grpc`。`plugin-remote-grpc` 是独立的插件 IPC 决策，不属于 Broker 节点通信。

### 9.3 RSocket

RSocket Java 基于 Reactor；request-channel 与 Request N 适合长期 Session/Route lanes 和逐帧背压，Lease 提供新请求准入，Resume 支持短暂断线恢复，因此作为 Reactor 集群唯一的节点数据与控制传输。

RSocket 不解决发现、fencing、Shard placement、持久化或脑裂。Resume 只优化短暂链路中断，不能代替 MQTT Inflight、Ratis Log 和 ClusterOutbox。Payload 继续使用带版本的 Protobuf，不把 Java 对象直接放到帧中。

### 9.4 Apache Pekko

Pekko Cluster Sharding 可以自然地把 clientId 映射为 SessionEntity，并提供成员管理、分片迁移、Singleton、Split Brain Resolver 和 Persistence 组合能力。它适合共享 PostgreSQL/Cassandra Persistence 下的完整 Actor 架构。

在 RocksDB + Ratis 路线中，Pekko Shard placement 会与 Raft Leader placement 形成两套所有权，因此不采用 Pekko + Ratis 的混合设计。重新评估 Pekko 必须用 Actor Entity 替换 Reactor ShardProcessor，并单独编写 ADR 和端到端 PoC。

### 9.5 选型结论

- Reactor Core：统一 Broker 异步编排模型。
- Reactor Netty：统一 MQTT TCP/TLS/WebSocket 接入。
- RSocket：唯一的 Broker 节点数据与控制传输。
- gRPC：集群不采用；只允许独立的插件远程适配器按插件文档使用。
- EventBus：从生产运行时移除。
- Apache Pekko：完整 Actor 架构备选，不与 Ratis 混用。

### 9.6 全 Reactor 架构

    MQTT Client
         |
    runtime
      transport.netty (TCP / TLS / WebSocket)
         |
      connection + shard processors
         |
         +---- RSocket ----> peer shard leaders
         +---- Ratis ------> shard replicas
         `---- RocksDB ----> applied shard state

core 的 command/state/transition/rule 保持同步纯函数，只有 core.port 使用 Reactor，整个 core 不依赖 Netty。runtime 使用 Mono/Flux 编排 use case、生命周期、timeout 和 backpressure。standalone 和 cluster 复用同一 runtime，分别由 runtime-standalone 与 runtime-cluster 提供 Dispatcher 和所有权策略。

运行时端口统一为 Reactor 类型：

    interface BrokerEngine {
        Mono<Void> opened(ConnectionHandle connection);
        Mono<Void> received(ConnectionId id, ClientPacket packet);
        Mono<Void> closed(ConnectionId id, DisconnectCause cause);
    }

Vert.x 不再作为运行时依赖，vertx-core、vertx-mqtt、Verticle 和 EventBus 全部移除。不能让 standalone 暴露 Future、cluster 暴露 Mono，也不能在核心路径持续双向桥接两套异步类型。

### 9.7 Reactor Netty MQTT 接入

Reactor Netty 通过 TcpServer/HttpServer 和 Netty ChannelPipeline 实现 MQTT Listener：

- TcpServer 提供 MQTT TCP 和 TLS。
- HttpServer WebSocket route 提供 MQTT over WebSocket，并校验 mqtt subprotocol。
- Netty MQTT encoder/decoder 只负责 wire frame；protocol 继续校验 MQTT 5 属性、Reason Code、Topic、UTF-8 和状态阶段。
- `runtime` 的 `transport.netty` 包把 Netty `MqttMessage` 转为不可变 `ClientPacket`，把 `ServerPacket` 转回 Netty message。
- Netty ByteBuf 只能存在于 transport；进入 core 前转换为有明确所有权的 Payload，禁止跨异步边界保留未 retain 的 ByteBuf。
- Reactor Netty 与 Netty MQTT codec 必须是 `implementation` 依赖；公共 API 和其他 runtime 包不得出现 `io.netty.*`。

每个连接的入站报文保持顺序：

    inboundPackets
      .concatMap(packet -> brokerEngine.received(connectionId, packet), 1)
      .then()

不能使用 flatMap 并发处理同一连接。连接接管后，来自不同物理连接但属于同一 clientId 的命令仍必须进入同一个 ShardProcessor。

背压由四层共同实现：Reactor Netty 根据下游 demand 控制读取，MQTT 层执行 Receive Maximum，ShardProcessor 使用有界 mailbox，RSocket 使用 Request N 控制 stream demand，并用 Lease 控制新请求准入。任何一层达到上限都必须停止读取、返回 Server Busy/Quota 结果或关闭连接，不能无限缓存。

### 9.8 Multi-Reactor 执行模型

Project Reactor 是响应式编排库；Vert.x 式 multi-reactor 网络模型由 Reactor Netty 底层的 Netty EventLoopGroup 实现。Reactor Netty 默认使用多个 I/O worker，一个连接在生命周期内固定到一个 EventLoop，不同连接分布到不同 EventLoop：

    acceptor/select loop
            |
      +-----+-----+-----+
      |           |     |
    IO loop 0   IO loop 1 ... IO loop N
    conn A/C    conn B/D
      |           |
      +-----+-----+
            |
      serialized Shard lanes
            |
      Ratis + RocksDB executors

可以使用同一组 LoopResources 驱动 TCP 与 WebSocket Listener：

    LoopResources ioLoops = LoopResources.create("mqtt-io", ioWorkers, true);
    TcpServer.create().runOn(ioLoops);
    HttpServer.create().runOn(ioLoops);

I/O worker 初始值接近可用 CPU 数，再根据连接建立速率、TLS、吞吐和 P99 压测调整。TCP 与 WebSocket 默认共享 I/O loops；相互影响明显时再隔离。Linux SO_REUSEPORT 和多个 acceptor 仅作为高连接建立速率优化，不作为正确性依赖。

与 Vert.x 的对应关系如下：

| 能力 | Vert.x | Reactor 方案 |
| --- | --- | --- |
| 接收连接 | NetServer acceptor | Reactor Netty/Netty selector |
| 网络并行 | 多个 Event Loop | LoopResources 中的多个 I/O worker |
| 连接亲和性 | 连接固定在一个 Event Loop/Context | Netty Channel 固定在一个 EventLoop |
| 业务串行化 | Verticle/Context 或显式 mailbox | Shard lane + 有界 mailbox + concatMap |
| 阻塞任务隔离 | WorkerExecutor/executeBlocking | 专用 Scheduler 或适配器 executor |

因此两者在网络层都属于 multi-reactor，并都能利用多核处理大量连接。差异在编程约束：Vert.x Handler 通常由 Context 保持线程亲和性；Reactor 操作符可能在 publishOn、subscribeOn 或第三方异步回调处切换线程。实现必须显式定义调度边界，不能把 Reactor Context 当作线程或互斥机制。

网络 multi-reactor 不能代替 Session 串行化。runtime 使用固定数量的单线程 Shard lanes，按 shardId 哈希选 lane；同一 lane 可以承载多个 Shard，但同一 Shard 的 command 永远串行。禁止为每个 clientId 创建线程，也禁止使用 parallel()/parallelFlux() 并发修改 SessionRecord 或相关业务记录。

每个 lane 使用有界 MPSC mailbox 接收来自任意 I/O EventLoop 的命令，并由一个 Scheduler.Worker 顺序 drain。异步 Store、RSocket 或 Ratis 调用不占用 lane 线程等待；完成信号重新调度到原 lane 后才应用状态转换和处理下一条命令。mailbox 满时执行第 9.7 节的背压或拒绝策略，不能切换到无界 onBackpressureBuffer。

| 执行资源 | 允许工作 |
| --- | --- |
| Reactor Netty I/O EventLoop | accept、TLS、decode、encode、socket read/write |
| Shard lane | command 排序、纯状态转换和异步流程编排 |
| Ratis executor | Raft protocol、commit callback 和 snapshot coordination |
| RocksDB apply executor | WriteBatch、checkpoint 和 compaction 相关阻塞工作 |
| Plugin scheduler | 第三方插件和外部阻塞调用 |

publishOn/subscribeOn 只能出现在明确的适配器边界。Ratis/RocksDB 操作完成后必须切回对应 Shard lane，再产生后续 Action，避免回调在任意线程并发修改状态。

### 9.9 模块与插件影响

全 Reactor 基础模块为 protocol、core、runtime、runtime-standalone 和 broker；客户端 Reactor Netty 接入属于 runtime 内部实现。集群模式以 runtime-cluster 替换 runtime-standalone，并增加 cluster-protocol、cluster-transport-rsocket 和 cluster-testkit。shared-store Profile 增加 store-postgres、cluster-outbox-postgres、cluster-coordination-postgres，replicated-store Profile 增加 cluster-consensus-ratis、store-rocksdb-sharded。完整依赖方向见 [集群模块详细设计](mqtt5-cluster-module-design.md)。

该选择还要求：

- plugin-api 生命周期和 Hook 从 Future 改为 Mono，移除 vertx-core 依赖。
- PluginContext 提供 Reactor Scheduler，不再提供 Vertx。
- plugin-runtime 使用 Reactor timeout、Context、有界 Scheduler 和 Event Sink。
- observability 使用 Reactor Context 传播 trace/client/session 信息，不能依赖 ThreadLocal 跨异步边界。
- 异步测试使用 StepVerifier，网络互操作继续使用真实 MQTT 5 客户端。

这是全局异步模型迁移，必须在开始实现 core、runtime 和 plugin-api 前完成文档及模块同步；客户端传输随 runtime 一并实现。

## 10. Shard Data + RocksDB 备选路线

如果集群要求不依赖 PostgreSQL，可让每个 Broker 持有若干数据 Shard，每个 Shard 使用本地 RocksDB，并通过 Raft 复制。该方案不是把 store-postgres 替换为 store-rocksdb：RocksDB 不提供节点间复制、Leader 选举、多数派提交、fencing 或 Shard 迁移，必须增加完整共识层。

不自行实现 Raft。推荐使用 Apache Ratis 管理复制日志、term、Leader、成员变更和 snapshot install：

    Metadata Raft Group
       membership + placement + shard epoch
                    |
        +-----------+-----------+
        |           |           |
    Broker A    Broker B    Broker C
    Shard 0 L   Shard 0 F   Shard 0 F
    Shard 1 F   Shard 1 L   Shard 1 F
    RocksDB     RocksDB     RocksDB

默认 replication factor=3。一个 Shard 的写请求只进入 Leader；Raft Log 获得多数派确认后才提交，再使用 RocksDB WriteBatch 应用到状态机。只有已提交命令完成 RocksDB apply 后才能完成对应 Mono 或发送 MQTT ACK。失去一个副本仍可写，失去多数派时对应 Shard 必须停止写入，不能降级成单副本继续 ACK。

### 10.1 Raft 与 RocksDB 职责

- Raft Log 是命令顺序、Leader term 和多数派提交的来源。
- RocksDB 是已提交状态的本地物化视图，不参与 Leader 选举。
- 每次 WriteBatch 同时保存业务变更和 lastAppliedIndex，恢复时从下一条已提交日志重放。
- Snapshot 使用 RocksDB checkpoint 生成，安装完成后再重放 snapshot index 之后的日志。
- Raft 稳定日志和 RocksDB 分别使用独立目录、磁盘配额和校验；禁止复制正在运行的数据库目录。
- 大 Payload 会放大 Raft Log、snapshot 和副本网络成本，必须设置 Maximum Packet Size、日志 segment、压缩和容量告警。

Ratis 只负责同一 Shard 副本之间的共识复制。Ingress 到 Shard Leader、跨 Shard Outbox 和路由仍通过 RSocket `PeerTransport`；不能把 Raft transport 当作所有 Broker 业务通信协议。

### 10.2 Shard 类型与数量

| Shard | 分区 Key | 状态 |
| --- | --- | --- |
| Session Shard | clientId | Session、Subscription、Inflight、Delivery、Will、入口 Message/Outbox |
| Retain Shard | topic | RetainedRecord 和版本 |
| Shared Group Shard | group + filter | 组成员、选择游标和唯一选择记录 |
| Metadata Group | 固定单组 | 节点成员、Shard placement、版本和 drain 状态 |

不为 1024 个逻辑 Partition 直接创建 1024 个 Raft Group；大量 Group 会放大 heartbeat、日志文件、快照和恢复成本。建议从 64 或 128 个物理 Data Shard 开始，并在集群创建时固定 dataShardCount。`PartitionTable` 将带类型的逻辑 Partition 映射到 Data Shard；一个 Data Shard 可以承载 Session、Retain 和 Shared Group 等多个逻辑 Partition。每个 Data Shard 对应一个 Raft Group，每个 Broker 可以同时承载多个 Group 的 Leader 和 Follower，placement 以磁盘、Leader 数和负载权重保持均衡。

### 10.3 Core 事务边界

Shard 架构不能支持“一个 WriteBatch 原子修改任意 Session、Retain 和共享组”。runtime 必须把事务边界显式限制在一个 Shard：

    interface ShardStore {
        <T> Mono<T> transact(ShardKey shard, StoreOperation<T> operation);
    }

- 单个 Session 命令只能原子修改其 Session Shard。
- 跨 Session Delivery、Retain 更新和共享订阅选择使用持久 Outbox/Saga。
- core Transition 不能假设 MessageRecord 与所有订阅者 Delivery 在一个全局事务中完成。
- PostgreSQL 实现即使能够执行跨表事务，也不得向 runtime 暴露更强的跨 Shard 假设。

建议无论第一版选择 PostgreSQL 还是 RocksDB + Ratis，都从一开始采用 Shard-local transaction。这样共享 Store 可以作为较简单的首个实现，而不锁死未来的复制存储路线。

### 10.4 跨 Shard 发布

    Publisher Session Shard Leader
              |
              | Raft commit
              v
    MessageRecord + QoS State + RouteOutbox
              |
              +----> Target Session Shard
              +----> Retain Shard
              `----> Shared Group Shard

1. 发布者 Session Shard 提交规范化 MessageRecord、入站 QoS 状态和 RouteOutbox。
2. Outbox 使用稳定 messageId 和 commandId 把 Publication 发送到目标 Shard Leader。
3. 目标 Session Shard 把消息副本或交付所需内容与 DeliveryRecord 一起提交，避免发送时依赖已失效的源 Leader。
4. 接收端以 `(commandId, targetShard)` 和 Delivery 唯一键去重，超时、Leader 切换和网络重试不会重复投递副作用。
5. 源 Shard 在目标提交后记录进度；未完成 Outbox 在恢复或 Leader 切换后继续执行。

普通 Publication 在源 Shard 已持久承担路由责任后即可进入后续 ACK 流程。retain=1 时，为保证 PUBACK 之后其他节点订阅能观察到 RetainedRecord，源 Shard 保存 Saga 状态，等待 Retain Shard 幂等提交后再完成需要的 ACK 状态。崩溃发生在两个提交之间时，由新 Leader 继续 Saga，不使用分布式 2PC。

Shared Group Shard 先提交 `(messageId, group, filter) -> selectedClientId`，再通过 Outbox 写入目标 Session Shard。唯一选择记录保证 Leader 切换和重试后仍只选择一个共享消费者。

### 10.5 Metadata、扩缩容与恢复

Metadata Raft Group 保存 nodeId/incarnation、Shard replicas、Leader hint、placement generation、clusterProtocolVersion 和 drain 状态。它必须使用 3 或 5 个稳定 voter；DNS 只能发现 seed，不能自动决定一个新集群的合法初始 voter 集合。首次 bootstrap 需要显式初始成员或一次性 join token，避免两个独立多数派使用同一 clusterId 启动。

Shard 迁移流程固定为：

    add learner
      -> install RocksDB snapshot
      -> replay remaining Raft Log
      -> promote voter
      -> transfer leadership when needed
      -> remove old replica

新副本 catch-up 完成前不参与多数派。节点 drain 先停止获得新 Leader，再迁移 Follower；活动 MQTT TCP 连接不能随 RocksDB snapshot 透明迁移，仍按第 11 节流程断开或等待客户端重连。

### 10.6 模块增量

cluster-replicated 路线额外需要：

| 模块 | 职责 |
| --- | --- |
| cluster-consensus-ratis | Metadata/Data Raft Group、成员变更、term、snapshot install |
| store-rocksdb-sharded | ShardStateMachine、WriteBatch、checkpoint、lastAppliedIndex 和恢复 |

cluster-consensus-ratis 和 store-rocksdb-sharded 都依赖 runtime-cluster 定义的 Shard/StateMachine 端口，彼此不形成循环依赖。现有 store-rocksdb 继续作为 standalone 实现，不能直接被多个集群节点共享。

### 10.7 部署约束

| 环境 | Shard + RocksDB 要求 |
| --- | --- |
| Docker Compose | 固定 Broker 实例、独立本地 volume，适合开发和故障测试 |
| Docker Swarm | Task 重调度必须绑定可恢复存储位置，或以 learner 从其他副本重建；不能共享 RocksDB volume |
| Kubernetes | StatefulSet + 每 Pod 独立 RWO PVC；Pod 重建后按 Raft snapshot/log catch-up |
| 单机 Docker | 使用 standalone-rocksdb，不为单节点引入一套单 voter Raft 集群 |

Kubernetes 和 Swarm 的副本数不是 Raft replication factor。编排器只能保证进程数量，Ratis placement 才决定每个 Shard 的数据副本。节点就绪必须等待 Metadata Group 加入、目标 Shard catch-up 和 peer transport 可用。

### 10.8 与共享 PostgreSQL 对比

| 维度 | PostgreSQL 共享 Store | Shard + RocksDB + Ratis |
| --- | --- | --- |
| 初期复杂度 | 较低 | 很高 |
| 外部数据库 | 需要 | 不需要 |
| 水平写扩展 | 受数据库容量限制 | 可按 Shard 扩展 |
| 事务范围 | 容易跨表提交 | 强制 Shard-local + Outbox |
| 复制与恢复 | PostgreSQL HA 负责 | Broker 自己负责 Raft、snapshot 和 rebalance |
| 存储运维 | 数据库运维 | Broker 磁盘、Raft、日志和副本运维 |
| 容器存储 | Broker 可无状态 | 每个数据节点需要独立持久卷或完整副本重建 |
| 实现周期 | 适合先完成协议和集群闭环 | 适合长期无外部数据库和水平扩展目标 |

如果长期产品目标明确要求无外部数据库、高吞吐和 Broker 自管理副本，应在实现 runtime 前确认该路线，因为 Shard-local 事务会影响所有 Store 用例。若近期目标是先交付正确可用的 MQTT 5 集群，则先实现 PostgreSQL profile，但仍按 Shard-local 端口和 Outbox 约束编写 runtime。

## 11. 故障、Drain 与升级

| 场景 | 行为 |
| --- | --- |
| Peer RPC 超时 | 保留 Outbox 并重试；决策链路按 Server Busy/Unavailable 失败 |
| Owner 进程退出 | shared-store 等待 lease/新 epoch；replicated-store 由多数派选出新 Leader/term |
| 旧 Owner 网络恢复 | shared-store 由 Store epoch 拒绝写入；replicated-store 由 Raft term 和 Leader 规则拒绝 |
| Ingress 退出 | TCP 连接断开，Owner 按 connectionGeneration 处理 Will 和 Session |
| PostgreSQL 不可用 | shared-store profile 停止新的所有权和状态提交，不对未持久化 QoS 1/2 发送成功 ACK |
| Raft Shard 失去多数派 | replicated profile 停止该 Shard 的写入和成功 ACK，其他健康 Shard 可继续服务 |
| 路由队列满 | 任务保留在持久 Outbox，节点进入 DEGRADED 并施加入口背压 |

Graceful drain 流程：

1. 节点将状态改为 DRAINING，并从新 assignment 候选中移除。
2. 停止接受新 MQTT 连接和新 Session lane。
3. 等待或迁移未完成 Outbox，停止获得新分区。
4. 对不能透明迁移的活动连接发送合法 DISCONNECT/Server Reference（客户端支持时），随后关闭。
5. shared-store 的新 Owner 加载状态并递增 epoch；replicated-store 在 Follower catch-up 后转移 Leader，旧节点再释放资源。

Peer Handshake 必须支持当前版本和前一兼容版本滚动升级。Protobuf 只增加可选字段，删除或改变字段语义需要提升 clusterProtocolVersion。不兼容节点保持 NOT_READY，不能进入 assignment。

## 12. 部署适配

| 环境 | 推荐部署 | 关键配置 |
| --- | --- | --- |
| Docker | 单机使用 RocksDB；手工集群使用用户网络和 PostgreSQL | 显式 nodeId、advertise host、volume 和 Secret |
| Docker Compose | 多个 broker service/replica + PostgreSQL healthcheck | 服务 DNS、唯一 incarnation、启动重试，不依赖 depends_on 保证就绪 |
| Docker Swarm | replicated service + overlay network + secrets | DNSRR、Task identity、滚动更新和 drain grace period |
| Kubernetes | StatefulSet + Headless Service + PostgreSQL HA | Downward API 注入 Pod IP、readiness、preStop、PDB 和 Secret |

Kubernetes 首选 StatefulSet 以获得可预测 nodeId 和有序滚动；共享 Store 仍是状态真相，Pod 本地磁盘不能保存唯一 Session 副本。外部 MQTT Service 与内部 peer Service 分离，peer 端口不得暴露到公网。

所有平台都必须提供可达的 advertise address。编排平台的 readiness 只有在 Store、coordination、required plugins、Peer Handshake 和至少一个 MQTT Listener 就绪后才返回成功。

## 13. 安全与可观测性

Peer TCP 默认启用 mTLS。证书身份绑定 clusterId 和 nodeId；Handshake 再校验 incarnation、协议版本和 capability。节点 RPC 不复用客户端 MQTT Listener，也不接受匿名连接。

至少提供以下指标：

- monaco_cluster_members、monaco_cluster_partitions、monaco_cluster_rebalances_total。
- monaco_cluster_peer_requests_total、monaco_cluster_peer_latency_seconds。
- monaco_cluster_session_lane_inflight、monaco_cluster_route_queue_size。
- monaco_cluster_outbox_pending、monaco_cluster_retries_total。
- monaco_cluster_fencing_rejections_total、monaco_cluster_assignment_generation。
- monaco_cluster_raft_term、monaco_cluster_raft_commit_index、monaco_cluster_snapshot_duration_seconds。

日志关联字段固定包含 clusterId、nodeId、incarnation、peerNodeId、partitionId、epoch、requestId、messageId 和 connectionId。健康状态区分 coordination、store、peer transport、assignment 和 route backlog。

## 14. 测试策略

- PeerTransportContract 验证 RSocket 的顺序、deadline、背压、重连和断线语义。
- CoordinationContract 验证 lease、epoch 单调性、重复 coordinator 和旧 Owner fencing。
- Store 契约验证 Outbox、Delivery 幂等和状态事务。
- 组件测试覆盖远程 CONNECT、Session takeover、QoS 1/2、Retain、Will 和离线恢复。
- 共享订阅测试验证跨节点同组每条消息只选择一个消费者。
- 故障测试在 Store 提交、RouteAck、PUBACK/PUBREC 和分区迁移的每个边界终止节点。
- 网络测试覆盖延迟、丢包、半开、双向分区、慢 peer 和证书轮换。
- 部署测试至少覆盖 Compose 和 Kubernetes；Swarm 配置通过独立 smoke test 验证。
- replicated profile 验证多数派丢失、双 Leader 尝试、日志重放、snapshot install、learner 提升和 Shard 迁移。
- 在 Raft commit、RocksDB apply、跨 Shard Outbox 和 RouteAck 的每个边界注入崩溃。
- 使用 StepVerifier 验证 Connection/Shard 流的顺序、取消、timeout、backpressure 和错误映射。
- 验证多个 Reactor Netty I/O EventLoop 都能接收连接，同时单连接及单 Shard 命令严格有序。
- 在 I/O EventLoop 上启用阻塞检测，覆盖 RocksDB、插件、证书和 snapshot 场景。

禁止用 sleep 推测集群稳定。测试通过 readiness、assignment generation、Outbox drain 和确定性 probe 等待状态。

## 15. 分阶段交付

### C0：Reactor 单机基线

- core 引入 Reactor Core 并承接稳定端口，领域包保持纯 Java。
- 将 runtime-reactor 重命名为 runtime，吸收 transport-reactor，并创建 runtime-standalone 迁移 LocalDispatcher。
- 完成 TCP/TLS/WS、Netty MQTT codec、LoopResources、ConnectionProcessor 和 Shard lanes。
- standalone-memory/rocksdb 使用本地 Dispatcher，协议行为与集群模式一致。

### C1：共享 Store 与控制平面

- 实现 store-postgres、cluster-outbox-postgres、租约、assignment、epoch 和 fencing。
- 完成节点启动、失效接管、drain 和恢复契约测试。

### C2：RSocket 数据平面

- 实现 Handshake、request-channel Session lanes、Route channels、Lease、Outbox 和 peer mTLS。
- 完成普通订阅、QoS、Retain 和 Will 跨节点闭环。

### C3：共享订阅与部署

- 实现 Shared Group Owner 和幂等消费者选择。
- 提供 Docker Compose、Swarm 和 Kubernetes 部署及故障演练。

### C4：RSocket 生产验证

- 对 RSocket Session/Route lanes 进行容量、长稳、背压和断线恢复压测。
- 固化 Request N、Lease、Resume、lane 数量、Payload 上限和超时的生产参数。
- Pekko 仅在决定重新评估整体 Actor 架构时进入独立 PoC。

### R1：复制存储基础

- 定义 ShardStore 和 ShardStateMachine 端口，引入 Apache Ratis Metadata Group。
- 实现 store-rocksdb-sharded、lastAppliedIndex、日志重放和 checkpoint。

### R2：数据 Shard 与跨 Shard 流程

- 实现 Session、Retain、Shared Group Shard 和 replication factor=3。
- 完成跨 Shard Outbox、retain Saga、共享消费者选择和 Leader 切换恢复。

### R3：迁移与生产化

- 实现 learner、snapshot install、voter 变更、Leader transfer 和 rebalance。
- 完成 StatefulSet/PVC、Swarm 重建、磁盘容量、压缩和灾难恢复演练。

R1-R3 是替代 C1 中 PostgreSQL coordination/store 的复制存储路线，不要求在同一个首发版本同时交付两套生产 Store。

## 16. 开放决策与验收标准

实施前需要确认：

1. 第一版选择 PostgreSQL shared-store，还是直接选择 RocksDB + Apache Ratis replicated-store。
2. 目标节点数、连接数、消息吞吐、Payload 分布和允许的 P99 延迟，用于确定 ioWorkers、Shard lanes、Request N、Lease 和队列水位。
3. QoS 0 是否默认进入持久 Outbox，还是允许显式配置非持久快速路径。
4. 扩容时是否允许通过 DISCONNECT 迁移活动 Session，还是只迁移离线分区。
5. replicated profile 的 dataShardCount、replication factor、最大 Payload 和 snapshot 目标时长。

集群设计验收标准：

1. standalone 不启动集群组件，替换为 cluster 模式不修改 protocol 状态机或 Reactor use case。
2. 同一 partition 在任意时刻只有当前 epoch 能提交状态。
3. 跨节点消息重复、超时和重连不会产生重复 Delivery 或破坏 QoS。
4. Publication 已持久化后，任一 Broker 退出都能由 Outbox 和新 Owner 继续路由。
5. 共享订阅跨节点仍保证一个组只选择一个消费者。
6. Peer 慢或断开不会阻塞 Reactor Netty I/O EventLoop 或造成无界内存增长。
7. Docker Compose、Swarm 和 Kubernetes 使用相同集群协议与所有权规则。
8. runtime-cluster 只依赖 PeerTransport 端口，唯一生产实现为 RSocket，传输细节不会进入 Store 和 MQTT 正确性语义。
9. replicated profile 只有多数派 Leader 能提交，同一日志在重放和 snapshot 恢复后得到相同 RocksDB 状态。
10. 生产 runtimeClasspath 不包含 vertx-core、vertx-mqtt 或 Vert.x EventBus，所有公开异步端口统一使用 Mono/Flux。
11. 多个 I/O EventLoop 能并行服务连接，同一 connectionId 和 shardId 的状态命令始终串行。
