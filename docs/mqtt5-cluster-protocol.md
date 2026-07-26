# Monaco MQTT 5.0 集群通信协议

> 状态：Proposed
>
> 协议版本：1.0
>
> 更新日期：2026-07-25
>
> 前置文档：[集群架构设计](mqtt5-cluster-architecture.md)、[集群技术设计](mqtt5-cluster-design.md)、[集群模块设计](mqtt5-cluster-module-design.md)

## 1. 范围与原则

本文定义 Broker 节点间的 RSocket/Protobuf 协议，以及 replicated-store 写入 Ratis Log 的应用命令格式。它不定义 MQTT 5 wire format，也不替代 Ratis 自身的 Raft 协议。

1. Broker 节点间只使用 RSocket，不提供 gRPC peer transport。
2. RSocket Payload data 使用 Protobuf，不使用 Java serialization、JSON 或 Netty 对象。
3. 跨节点操作按至少一次处理；稳定 ID、generation、epoch 和 Store 唯一约束负责幂等。
4. RSocket 完成只表示传输完成，业务成功由 `Status`、`CommandResult` 或 `RouteAck` 表达。
5. 所有流、batch、Payload 和重排窗口有硬上限，不能使用无界缓冲。
6. wire schema 与 `core` 领域模型分离，由 adapter mapper 显式转换和校验。

## 2. 协议栈与 RSocket 路由

```text
Protobuf application messages
RSocket request-response / request-channel / request-stream
RSocket composite metadata + routing metadata
TLS 1.3 mutual authentication
TCP
```

RSocket 连接参数：

| 项目 | 值 |
| --- | --- |
| Data MIME type | `application/vnd.monaco.cluster.v1+protobuf` |
| Metadata MIME type | `message/x.rsocket.composite-metadata.v0` |
| Route metadata | `message/x.rsocket.routing.v0` |
| Setup data | `SetupRequest` Protobuf |
| Keepalive、Lifetime | RSocket SETUP 原生字段，由集群配置统一约束 |
| Fragment MTU | SETUP 提议 `SetupRequest.max_fragment_bytes`，Handshake 通过 `Limits.max_fragment_bytes` 确认双方配置的较小值 |

固定 route：

| Route | Interaction | Request | Response |
| --- | --- | --- | --- |
| `monaco.cluster.handshake.v1` | request-response | `HandshakeRequest` | `HandshakeResponse` |
| `monaco.cluster.control.v1` | request-response | `ControlRequest` | `ControlResponse` |
| `monaco.cluster.session.v1` | request-channel | `IngressSessionFrame` | `OwnerSessionFrame` |
| `monaco.cluster.route.v1` | request-channel | `RouteRequestFrame` | `RouteResponseFrame` |
| `monaco.cluster.health.v1` | request-response | `HealthRequest` | `HealthResponse` |
| `monaco.cluster.health.watch.v1` | request-stream | `HealthRequest` | `HealthResponse` stream |

未知 route 返回 RSocket application error `CHANNEL_ERROR_CODE_UNKNOWN_ROUTE`。业务级 `NOT_OWNER`、`STALE_EPOCH` 或 `OVERLOADED` 必须放入 Protobuf `Status`，不能关闭整个 peer connection。

## 3. 建连与连接唯一性

建连顺序固定为：

1. TCP + TLS 1.3，双向证书校验并提取 `clusterId/nodeId` 身份。
2. initiator 发送 RSocket SETUP，data 为 `SetupRequest`。
3. 双方校验 cluster、节点身份、incarnation、最大 frame 和协议大版本。
4. initiator 调用 Handshake route，协商 minor version、capabilities、限制和 required plugin fingerprint。
5. Handshake 成功后才能打开 Session/Route channel；失败发送结构化 ERROR 并关闭连接。

每对活动节点只保留一个 peer connection。`nodeId` 字典序较小的一方是规范 initiator；同时建连时保留规范方向，另一条连接以 `DUPLICATE_PEER` 关闭。节点重启后的新 incarnation 总是替换旧 incarnation，旧连接不得 Resume。

Resume token 绑定 `clusterId + sourceNodeId/incarnation + targetNodeId/incarnation + negotiatedVersion`，并有短 TTL。Resume 失败时重新 Handshake 和打开 lanes；Session command 与 Outbox 使用原稳定 ID 重试，不能把 RSocket Resume 当作业务恢复日志。

## 4. Protobuf 基础规则

Schema 使用 `proto3`：

```proto
syntax = "proto3";

package monaco.cluster.v1;

option java_multiple_files = true;
option java_package = "cn.elvis.monaco.cluster.wire.v1";
```

编码约束：

- 所有 enum 的 `0` 值命名为 `*_UNSPECIFIED`，解码后仍为 0 时拒绝业务帧。
- 可缺省 scalar 使用 `optional`，不使用 magic value 表达缺失。
- ID 使用受限 UTF-8 string：UUID 为 36 字符，ULID 为 26 字符；解码器校验格式和长度。
- MQTT 二进制字段和 Payload 使用 `bytes`；进入 domain 前复制为有明确所有权的不可变值。
- `uint32` 表示 MQTT packet id、QoS、Reason Code 和无符号配置值；mapper 再校验协议范围。
- 需要保序或参与 hash 的键值对使用 `repeated`，禁止使用 Protobuf `map`。
- 已发布 field number 永不复用；删除字段使用 `reserved`。
- Ratis Log 和签名/hash 输入使用 deterministic serialization。

目标 schema 文件：

| 文件 | 所有内容 |
| --- | --- |
| `common.proto` | version、identity、header、partition、status |
| `handshake.proto` | Setup、Handshake、Limits、Capabilities |
| `mqtt_packet.proto` | ClientPacket、ServerPacket 及 MQTT properties |
| `session.proto` | Session lane 双向 frame、connection、command、action |
| `route.proto` | Publication、RouteTask、batch、ack |
| `control.proto` | Control、Health 和 diagnostics |
| `raft.proto` | deterministic mutation batch 与 snapshot manifest |
| `error.proto` | channel/fatal error |

## 5. 公共 Envelope

`common.proto` 定义所有消息共享的身份、分区目标和结果：

```proto
message ProtocolVersion {
  uint32 major = 1;
  uint32 minor = 2;
}

message NodeRef {
  string node_id = 1;
  string incarnation = 2;       // canonical UUID
}

enum PartitionType {
  PARTITION_TYPE_UNSPECIFIED = 0;
  PARTITION_TYPE_SESSION = 1;
  PARTITION_TYPE_RETAIN = 2;
  PARTITION_TYPE_SHARED_GROUP = 3;
}

message PartitionTarget {
  PartitionType type = 1;
  uint32 partition_id = 2;      // logical partition
  uint32 data_shard_id = 3;     // physical ownership/Raft unit
  uint64 mapping_generation = 4;
  uint64 shard_epoch = 5;
}

message TraceContext {
  string traceparent = 1;
  optional string tracestate = 2;
}

message EnvelopeHeader {
  ProtocolVersion version = 1;
  string cluster_id = 2;
  NodeRef source = 3;
  string target_node_id = 4;
  string request_id = 5;        // ULID, stable across retry
  uint64 timeout_millis = 6;    // remaining local processing budget
  TraceContext trace = 7;
  uint64 sent_at_unix_millis = 8; // observability only
}
```

`timeout_millis` 是接收方从收到报文起计算的处理预算，必须大于 0 且不能超过本地配置上限；为 0 或超限返回 `INVALID_ARGUMENT`。接收方在入队和进入持久化提交前检查本地 deadline；超时且尚未开始提交时返回 `TIMEOUT`。提交一旦开始，超时只表示结果未知，不能承诺没有副作用，调用方必须用稳定 ID 重试或查询幂等结果。重试或转发要按发送方本地 deadline 重新计算剩余预算，不使用跨主机单调时钟。`sent_at_unix_millis` 只用于日志和延迟分析，不能用于 lease、fencing、expiry 或正确性判断。

统一结果：

```proto
enum StatusCode {
  STATUS_CODE_UNSPECIFIED = 0;
  STATUS_CODE_OK = 1;
  STATUS_CODE_INVALID_ARGUMENT = 2;
  STATUS_CODE_UNAUTHENTICATED = 3;
  STATUS_CODE_PERMISSION_DENIED = 4;
  STATUS_CODE_CLUSTER_MISMATCH = 5;
  STATUS_CODE_VERSION_UNSUPPORTED = 6;
  STATUS_CODE_NOT_OWNER = 7;
  STATUS_CODE_STALE_EPOCH = 8;
  STATUS_CODE_STALE_MAPPING = 9;
  STATUS_CODE_OVERLOADED = 10;
  STATUS_CODE_TIMEOUT = 11;
  STATUS_CODE_UNAVAILABLE = 12;
  STATUS_CODE_DUPLICATE = 13;
  STATUS_CODE_INTERNAL = 14;
  STATUS_CODE_CORRUPT_STATE = 15;
  STATUS_CODE_SEQUENCE_GAP = 16;
  STATUS_CODE_STALE_GENERATION = 17;
}

message Status {
  StatusCode code = 1;
  optional string message = 2;          // sanitized, never a Java stack trace
  optional uint64 retry_after_millis = 3;
  PartitionTarget current_target = 4;
}
```

`OK` 与 `DUPLICATE` 都表示调用方不应重复产生副作用。`NOT_OWNER/STALE_*` 必须携带已知的 `current_target` 或触发调用方刷新 `ClusterView`。`INTERNAL/CORRUPT_STATE` 不自动无限重试。

## 6. Setup 与 Handshake

```proto
message SetupRequest {
  ProtocolVersion minimum_version = 1;
  ProtocolVersion maximum_version = 2;
  string cluster_id = 3;
  NodeRef source = 4;
  string target_node_id = 5;
  uint32 max_fragment_bytes = 6;
}

message Limits {
  uint32 max_envelope_bytes = 1;
  uint32 max_payload_bytes = 2;
  uint32 max_batch_items = 3;
  uint32 session_lane_count = 4;
  uint32 route_lane_count = 5;
  uint32 max_inflight_per_lane = 6;
  uint32 max_batch_bytes = 7;
  uint32 max_reorder_window = 8;
  uint32 max_fragment_bytes = 9;
}

message Capabilities {
  repeated string features = 1;       // sorted canonical names
  repeated string required_features = 2;
  string required_plugin_fingerprint = 3;
  string build_version = 4;
}

message HandshakeRequest {
  EnvelopeHeader header = 1;
  ProtocolVersion minimum_version = 2;
  ProtocolVersion maximum_version = 3;
  Limits offered_limits = 4;
  Capabilities capabilities = 5;
  reserved 6;
}

message HandshakeResponse {
  EnvelopeHeader header = 1;
  Status status = 2;
  ProtocolVersion negotiated_version = 3;
  Limits negotiated_limits = 4;
  Capabilities capabilities = 5;
  reserved 6;
}
```

协商规则：major 必须相同，minor 取双方支持交集中的最大值，所有发送功能受 negotiated minor 和 capability 限制。限制值逐项取较小值。required feature/plugin fingerprint 不满足时返回 `VERSION_UNSUPPORTED` 或 `PERMISSION_DENIED`，节点不能进入 READY。

## 7. MQTT 报文表示

`mqtt_packet.proto` 是 `protocol` 不可变模型的 wire projection，不携带 Netty `ByteBuf`，也不重新编码原始 MQTT 二进制帧：

```proto
import "google/protobuf/empty.proto";

message ClientPacket {
  oneof packet {
    ConnectPacket connect = 1;
    PublishPacket publish = 2;
    AckPacket pub_ack = 3;
    AckPacket pub_rec = 4;
    AckPacket pub_rel = 5;
    AckPacket pub_comp = 6;
    SubscribePacket subscribe = 7;
    UnsubscribePacket unsubscribe = 8;
    google.protobuf.Empty ping_req = 9;
    DisconnectPacket disconnect = 10;
    AuthPacket auth = 11;
  }
}

message ServerPacket {
  oneof packet {
    ConnAckPacket conn_ack = 1;
    PublishPacket publish = 2;
    AckPacket pub_ack = 3;
    AckPacket pub_rec = 4;
    AckPacket pub_rel = 5;
    AckPacket pub_comp = 6;
    MultiAckPacket sub_ack = 7;
    MultiAckPacket unsub_ack = 8;
    google.protobuf.Empty ping_resp = 9;
    DisconnectPacket disconnect = 10;
    AuthPacket auth = 11;
  }
}

message PublishPacket {
  string topic_name = 1;
  uint32 qos = 2;
  bool retain = 3;
  bool dup = 4;
  optional uint32 packet_id = 5;
  bytes payload = 6;
  PublishProperties properties = 7;
}

message AckPacket {
  uint32 packet_id = 1;
  uint32 reason_code = 2;
  AckProperties properties = 3;
}
```

主要 MQTT message 字段固定如下：

| Message | Field number 与字段 |
| --- | --- |
| `ConnectPacket` | `1 client_id`、`2 clean_start`、`3 keep_alive_seconds`、`4 will`、`5 username`、`6 password`、`7 properties` |
| `ConnAckPacket` | `1 session_present`、`2 reason_code`、`3 properties` |
| `WillMessage` | `1 topic`、`2 payload`、`3 qos`、`4 retain`、`5 properties` |
| `SubscribePacket` | `1 packet_id`、`2 subscriptions`、`3 properties` |
| `Subscription` | `1 topic_filter`、`2 max_qos`、`3 no_local`、`4 retain_as_published`、`5 retain_handling` |
| `UnsubscribePacket` | `1 packet_id`、`2 topic_filters`、`3 properties` |
| `MultiAckPacket` | `1 packet_id`、`2 reason_codes`、`3 properties` |
| `DisconnectPacket` | `1 reason_code`、`2 properties` |
| `AuthPacket` | `1 reason_code`、`2 properties` |

Property message 不使用通用键值袋，字段与 domain record 一一对应：

| Message | Field number 与字段 |
| --- | --- |
| `ConnectProperties` | `1 session_expiry_interval`、`2 receive_maximum`、`3 maximum_packet_size`、`4 topic_alias_maximum`、`5 request_response_information`、`6 request_problem_information`、`7 authentication_method`、`8 authentication_data`、`9 user_properties` |
| `ConnAckProperties` | `1 session_expiry_interval`、`2 receive_maximum`、`3 maximum_qos`、`4 retain_available`、`5 maximum_packet_size`、`6 assigned_client_identifier`、`7 topic_alias_maximum`、`8 reason_string`、`9 wildcard_subscription_available`、`10 subscription_identifier_available`、`11 shared_subscription_available`、`12 server_keep_alive`、`13 response_information`、`14 server_reference`、`15 authentication_method`、`16 authentication_data`、`17 user_properties` |
| `PublishProperties` | `1 payload_format_indicator`、`2 message_expiry_interval`、`3 topic_alias`、`4 response_topic`、`5 correlation_data`、`6 subscription_identifiers`、`7 content_type`、`8 user_properties` |
| `WillProperties` | `1 will_delay_interval`、`2 payload_format_indicator`、`3 message_expiry_interval`、`4 content_type`、`5 response_topic`、`6 correlation_data`、`7 user_properties` |
| `SubscribeProperties` | `1 subscription_identifier`、`2 user_properties` |
| `AckProperties` | `1 reason_string`、`2 user_properties` |
| `DisconnectProperties` | `1 session_expiry_interval`、`2 reason_string`、`3 server_reference`、`4 user_properties` |
| `AuthProperties` | `1 authentication_method`、`2 authentication_data`、`3 reason_string`、`4 user_properties` |
| `UserProperty` | `1 key`、`2 value` |

表中的 domain Optional scalar 在 Protobuf 中使用 `optional`；repeated 字段保留插入顺序。message 字段使用 proto3 自带 presence，不添加 `optional` label。

CONNECT、SUBSCRIBE、AUTH、Will 和全部 property 使用对应的结构化 message。映射必须遵守以下规则：

- `ClientPacket`/`ServerPacket` oneof tag 是报文种类；不能仅靠一个自由整数区分类型。
- QoS、Reason Code、Packet ID、Topic 和属性在接收端重新执行 `protocol` 校验。
- `optional packet_id` 只允许 QoS 1/2；QoS 0 必须缺失。
- User Property 和 Subscription Identifier 使用 `repeated` 并保留原顺序。
- password、authentication data、correlation data 和 payload 使用 `bytes`，日志禁止输出内容。
- Topic Alias 仍属于连接状态；Ingress 不提前删除，Owner 按同一连接 generation 处理。
- 未知 oneof、未知 enum、非法 UTF-8 或超限字段返回 `INVALID_ARGUMENT` 并关闭对应 MQTT 连接。

## 8. Session Lane 协议

一个 RSocket request-channel 是一个长期 Session lane，并复用多个 MQTT 连接。Ingress 发送 request stream，Session Owner 发送 response stream。

### 8.1 公共引用

```proto
message ConnectionRef {
  string connection_id = 1;          // canonical UUID string
  uint64 generation = 2;             // takeover/rebind generation
  string client_id = 3;
  string ingress_node_id = 4;
}

enum DisconnectCause {
  DISCONNECT_CAUSE_UNSPECIFIED = 0;
  DISCONNECT_CAUSE_CLIENT_REQUEST = 1;
  DISCONNECT_CAUSE_NETWORK_EOF = 2;
  DISCONNECT_CAUSE_KEEP_ALIVE_TIMEOUT = 3;
  DISCONNECT_CAUSE_PROTOCOL_ERROR = 4;
  DISCONNECT_CAUSE_SESSION_TAKEN_OVER = 5;
  DISCONNECT_CAUSE_SERVER_SHUTDOWN = 6;
  DISCONNECT_CAUSE_TRANSPORT_ERROR = 7;
}

message SessionLaneOpen {
  uint32 lane_id = 1;
  uint64 lane_generation = 2;
  Limits limits = 3;
}
```

`lane_id` 范围为 `[0, negotiated.session_lane_count)`。peer 重连后递增 `lane_generation`，旧 lane 的延迟帧直接丢弃并计数。

### 8.2 Ingress 到 Owner

```proto
message IngressSessionFrame {
  EnvelopeHeader header = 1;
  uint32 lane_id = 2;
  uint64 lane_generation = 3;
  uint64 lane_sequence = 4;
  PartitionTarget target = 5;

  oneof body {
    SessionLaneOpen open = 10;        // 必须是 request stream 第一帧
    AttachConnection attach = 11;
    ClientCommand command = 12;
    DetachConnection detach = 13;
    ActionResult action_result = 14;
  }
}

message AttachConnection {
  ConnectionRef connection = 1;
  uint64 command_sequence = 2;        // first value is 1
  ConnectPacket connect = 3;
  string remote_address = 4;
  optional string tls_principal = 5;
  optional string assigned_client_id = 6;
}

message ClientCommand {
  ConnectionRef connection = 1;
  uint64 command_sequence = 2;
  ClientPacket packet = 3;            // CONNECT is forbidden here
}

message DetachConnection {
  ConnectionRef connection = 1;
  uint64 command_sequence = 2;
  DisconnectCause cause = 3;
  reserved 4;
}

message ActionResult {
  ConnectionRef connection = 1;
  string action_id = 2;
  Status status = 3;                  // actual Channel write/close result
}
```

物理连接在收到合法 CONNECT 前只存在于 Ingress。CONNECT 确定 `clientId` 和目标 Partition 后，以 `AttachConnection` 一次性发送 opened + CONNECT 语义。后续再次出现 CONNECT 是协议错误。

CONNECT 的 clientId 为空且满足 MQTT 分配条件时，Ingress 先生成一个集群唯一、可重试复用的 Assigned Client ID，再计算 Session Partition。此时 `connect.client_id` 保持客户端原始空值，`assigned_client_id` 与 `connection.client_id` 都填最终值；Owner 校验并在 CONNACK 返回 Assigned Client Identifier。非空 clientId 时 `assigned_client_id` 必须缺失。

Attach 请求中的 `connection.generation` 固定为 0。Owner 在 Session takeover 事务中分配大于 0 的 generation，并在首个 `ServerAction/CommandResult` 的 ConnectionRef 返回；相同 requestId 的 Attach 重试必须得到同一 generation。后续 ClientCommand、Detach 和 ActionResult 都使用该 generation。

### 8.3 Owner 到 Ingress

```proto
message OwnerSessionFrame {
  EnvelopeHeader header = 1;
  uint32 lane_id = 2;
  uint64 lane_generation = 3;
  uint64 lane_sequence = 4;
  PartitionTarget target = 5;

  oneof body {
    LaneAccepted accepted = 10;
    CommandResult command_result = 11;
    ServerAction action = 12;
    LaneRedirect redirect = 13;
    LaneFailure failure = 14;
  }
}

message CommandResult {
  ConnectionRef connection = 1;
  uint64 command_sequence = 2;
  Status status = 3;                  // completed only after required commit
}

message ServerAction {
  ConnectionRef connection = 1;
  uint64 action_sequence = 2;
  string action_id = 3;               // ULID, stable across retry
  oneof action {
    ServerPacket send = 10;
    CloseConnection close = 11;
  }
}

message LaneRedirect {
  PartitionTarget current_target = 1;
  Status status = 2;
}

message LaneAccepted {
  uint32 lane_id = 1;
  uint64 lane_generation = 2;
  Status status = 3;
}

message LaneFailure {
  Status status = 1;
  optional uint64 expected_lane_sequence = 2;
}

message CloseConnection {
  DisconnectPacket final_packet = 1;
  bool flush_before_close = 2;
}
```

`DetachConnection` 只报告网络事实，Will 是否发布由 Owner 根据 DisconnectCause、Will 和 Session 状态决定。`CommandResult.OK` 只在命令所需 Store/Raft 提交及同步响应的 `ActionResult.OK` 后发送。`ServerAction` 也可以由后续异步投递产生；Ingress 按 connection generation 和 `action_sequence` 发送，完成后回传 `ActionResult`。

`target` 在 Open/Accepted 和 lane 级 Failure 中可以缺失，其他 Session frame 必须携带。Owner 在处理副作用前校验 partition type、mapping generation、data shard 和 shard epoch；Ingress 也只接受与当前 connection binding 一致的 action/result。迁移中的旧目标返回 `NOT_OWNER/STALE_MAPPING/STALE_EPOCH`，不能依赖 lane 所连接的 nodeId 隐式推断所有权。

`CloseConnection.final_packet` 使用 proto3 message presence：缺失表示直接关闭网络连接；存在时先发送该 MQTT DISCONNECT。`flush_before_close` 只在 `final_packet` 存在时允许为 true，并要求等待写出结果后再关闭。

### 8.4 顺序与重试

- `lane_sequence` 在单个方向、单个 lane generation 内从 1 严格递增，用于检测 wire 重复或缺口。
- `command_sequence` 在单个 connection generation 内严格递增；Owner 使用 `connectionId + generation + sequence` 幂等。
- `action_sequence` 是 Owner 到 Ingress 的独立序列，不能与 command sequence 共用。
- Ingress 在 connection generation 生命周期内保存有界 actionId 结果缓存；重发相同 actionId 时返回原结果，不再次写出 MQTT 报文。
- 同一连接只允许一个未完成 Client command；不同连接可在同一 lane 交错。
- 已完成命令的旧 generation 返回 `DUPLICATE`，其他旧 connection generation 返回 `STALE_GENERATION`，不得修改新连接状态。
- lane 中断后只重发没有持久完成证明的命令；相同命令保留 requestId 和 command sequence。

## 9. Publication Route 协议

Route request-channel 按目标 Owner 复用多个逻辑 Partition。已打开 channel 内的逐帧流控只使用 Reactive Streams Request N，v1 不再设计应用层 credit message；RSocket Lease 只限制新 request/channel 的准入，不控制既有 channel 的每个 frame。

```proto
message SourcePosition {
  uint32 source_data_shard_id = 1;
  uint64 source_epoch = 2;
  uint64 route_sequence = 3;          // per source shard + target partition
  optional uint64 raft_commit_index = 4;
}

message Publication {
  string message_id = 1;              // ULID
  string publisher_client_id = 2;
  string topic_name = 3;
  uint32 qos = 4;
  bool retain = 5;
  bytes payload = 6;
  RoutedPublishProperties properties = 7;
  optional uint64 remaining_expiry_millis = 8;
  bytes payload_sha256 = 9;           // exactly 32 bytes
}

message RoutedPublishProperties {
  optional uint32 payload_format_indicator = 1;
  optional string response_topic = 2;
  optional bytes correlation_data = 3;
  optional string content_type = 4;
  repeated UserProperty user_properties = 5;
}

message RouteTask {
  string route_task_id = 1;           // stable ULID
  PartitionTarget target = 2;
  SourcePosition source_position = 3;
  Publication publication = 4;
}

message PublicationBatch {
  string batch_id = 1;
  repeated RouteTask tasks = 2;
}

message RouteLaneOpen {
  uint32 lane_id = 1;
  uint64 lane_generation = 2;
  Limits limits = 3;
}

message RouteRequestFrame {
  EnvelopeHeader header = 1;
  uint32 lane_id = 2;
  uint64 lane_generation = 3;
  uint64 lane_sequence = 4;
  oneof body {
    RouteLaneOpen open = 10;
    PublicationBatch batch = 11;
  }
}

message RouteTaskResult {
  string route_task_id = 1;
  Status status = 2;
  optional uint64 target_commit_index = 3;
  optional uint64 expected_route_sequence = 4;
}

message RouteResponseFrame {
  EnvelopeHeader header = 1;
  uint32 lane_id = 2;
  uint64 lane_generation = 3;
  uint64 lane_sequence = 4;
  oneof body {
    LaneAccepted accepted = 10;
    RouteAck ack = 11;
    LaneFailure failure = 12;
  }
}

message RouteAck {
  string batch_id = 1;
  repeated RouteTaskResult results = 2;
}
```

Route 语义：

- Session Owner 在写 Outbox 前解析并移除 Topic Alias，把 Message Expiry 转成 remaining expiry；旧 Subscription Identifier 不进入 RouteTask，由目标订阅匹配重新生成。
- `RouteTaskResult.OK/DUPLICATE` 只在目标 Delivery 唯一键提交后返回。
- batch 可以部分成功；发送端只把已成功 task 标记为已确认，其余保留在 Outbox。
- 同一 `(sourceDataShard, targetPartition)` 的 route sequence 按序发送，前一项未获得终态前不越过；不同目标可以并行。接收端发现缺口时返回 `SEQUENCE_GAP + expected_route_sequence`。
- `route_sequence` 持久化在 source shard 中，Leader/Owner 或 `source_epoch` 变化后继续递增，不能从 1 重新开始。
- 过期或策略性丢弃的 task 仍提交一个终态 tombstone 并推进 route sequence，不能留下永久缺口。
- 不同 source Shard 的 Publication 没有全局顺序，不构造分布式 total order。
- `remaining_expiry_millis` 每次从持久化 deadline 重新计算，接收端使用本地 clock 建立新 deadline；0 表示已过期。
- payload hash 不用于身份或授权，只用于检测编码/存储损坏。

## 10. Control 与 Health

```proto
message ControlRequest {
  EnvelopeHeader header = 1;
  oneof command {
    DrainNotice drain = 10;
    AssignmentHint assignment_hint = 11;
    LeaderTransferHint leader_transfer = 12;
    DiagnosticsRequest diagnostics = 13;
  }
}

message ControlResponse {
  EnvelopeHeader header = 1;
  Status status = 2;
  DiagnosticsResponse diagnostics = 3;
}

message HealthRequest {
  EnvelopeHeader header = 1;
  bool include_shards = 2;
}

message HealthResponse {
  EnvelopeHeader header = 1;
  Status status = 2;
  NodeState node_state = 3;
  uint64 cluster_view_generation = 4;
  repeated ShardHealth shards = 5;
  uint64 health_sequence = 6;
}
```

Control/Health 子消息字段：

| Message | 字段 |
| --- | --- |
| `DrainNotice` | `drain_id`、`node`、`grace_period_millis`、`cluster_view_generation` |
| `AssignmentHint` | `cluster_view_generation`、`partition_mapping_generation`、`targets[]` |
| `LeaderTransferHint` | `data_shard_id`、`expected_epoch`、`suggested_node_id` |
| `DiagnosticsRequest` | `include_lanes`、`include_outbox`、`include_consensus` |
| `DiagnosticsResponse` | 有上限的 lane/outbox/consensus 摘要，不含业务 Payload |
| `ShardHealth` | `data_shard_id`、`epoch`、`role`、`commit_index`、`last_applied_index`、`state` |

Health watch 的 `health_sequence` 在单个 response stream 内递增；丢失 watch 后重新 request-stream，不进行业务重放。

Control message 是通知或刷新提示，不是 assignment 权威写入。shared-store 必须回到 PostgreSQL 读取 generation，replicated-store 必须回到 Metadata Ratis Group。Health 只表示观测到的可用性，不能证明 Owner/Leader 写权限。

## 11. Ratis 应用命令

Ratis transport 和选举使用 Ratis 自身协议；`raft.proto` 只定义写入 Log 的 deterministic application command：

```proto
message ShardCommandEnvelope {
  uint32 command_schema_version = 1;
  string cluster_id = 2;
  uint32 data_shard_id = 3;
  uint64 expected_epoch = 4;
  string command_id = 5;              // stable ULID
  uint64 logical_time_millis = 6;     // Leader resolved input
  optional uint64 expected_shard_revision = 7;

  StateMutationBatch mutations = 10;
  bytes mutation_sha256 = 11;
}

message StateMutationBatch {
  repeated StateMutation mutations = 1; // application order
}

message StateMutation {
  oneof mutation {
    SessionMutation session = 10;
    DeliveryMutation delivery = 11;
    RetainMutation retained = 12;
    SharedSelectionMutation shared = 13;
    OutboxMutation outbox = 14;
  }
}

message SnapshotManifest {
  string cluster_id = 1;
  uint32 data_shard_id = 2;
  string raft_group_id = 3;
  uint64 last_applied_term = 4;
  uint64 last_applied_index = 5;
  uint32 state_schema_version = 6;
  bytes sha256 = 7;
}
```

每种 Mutation 都是结构化 message，至少包含 entity key、expected revision、new revision，以及 upsert value 或 tombstone；外层 `StateMutation.oneof` 保留跨实体类型的应用顺序。禁止放置不带 schema 的 opaque Java bytes。`mutation_sha256` 必须是 deterministic serialized `StateMutationBatch` 的 32 字节 SHA-256。

Leader 在 append 前读取预期 revision、解析所有非确定输入，并执行 `core` Transition：clock、ID、认证结果、插件修改、共享选择和 expiry deadline 都固化为 `StateMutationBatch`。Follower 不执行插件、外部调用或领域决策，只校验 epoch/revision/hash 并原样应用同一 RocksDB WriteBatch。相同 mutation bytes 和前置 revision 必须得到相同 state；不满足时停止该 Shard 并报告 `CORRUPT_STATE`。

不得把 Java object、lambda、class name 或默认 Java serialization 写入 Raft Log。Command schema 和 RocksDB state schema 分别版本化，升级时同时提供旧 command replay 与 snapshot migration 测试。

## 12. 错误与连接关闭

错误分三层处理：

| 层级 | 示例 | 动作 |
| --- | --- | --- |
| 业务结果 | NOT_OWNER、STALE_EPOCH、OVERLOADED | 返回 `Status`，保持 channel/connection |
| Request/Channel 协议错误 | 未知 route、sequence 缺口、首帧不是 Open、非法 oneof | 发送 `ChannelError` 并关闭该 request/channel，其他 lanes 保持 |
| Peer 致命错误 | mTLS 身份不符、cluster mismatch、major 不兼容、超限攻击 | RSocket ERROR 后关闭 peer connection |

Request/channel 级 RSocket ERROR 使用：

```proto
enum ChannelErrorCode {
  CHANNEL_ERROR_CODE_UNSPECIFIED = 0;
  CHANNEL_ERROR_CODE_UNKNOWN_ROUTE = 1;
  CHANNEL_ERROR_CODE_EXPECTED_OPEN = 2;
  CHANNEL_ERROR_CODE_INVALID_FRAME = 3;
  CHANNEL_ERROR_CODE_SEQUENCE_GAP = 4;
  CHANNEL_ERROR_CODE_LIMIT_EXCEEDED = 5;
}

message ChannelError {
  ProtocolVersion version = 1;
  ChannelErrorCode code = 2;
  optional string request_id = 3;
  optional uint64 expected_sequence = 4;
  optional string message = 5;
}
```

Peer connection 级 RSocket ERROR 使用受限的 `PeerFatalError` Protobuf：

```proto
enum PeerFatalCode {
  PEER_FATAL_CODE_UNSPECIFIED = 0;
  PEER_FATAL_CODE_PROTOCOL_VIOLATION = 1;
  PEER_FATAL_CODE_CLUSTER_MISMATCH = 2;
  PEER_FATAL_CODE_IDENTITY_MISMATCH = 3;
  PEER_FATAL_CODE_VERSION_UNSUPPORTED = 4;
  PEER_FATAL_CODE_DUPLICATE_PEER = 5;
  PEER_FATAL_CODE_FRAME_TOO_LARGE = 6;
}

message PeerFatalError {
  ProtocolVersion version = 1;
  PeerFatalCode code = 2;
  optional string request_id = 3;
  optional string message = 4;
}
```

两种错误的 `message` 都必须脱敏，不能包含 stack trace、SQL、路径、证书内容或 MQTT Payload。重复 peer 致命错误触发 peer 级熔断和安全告警。

## 13. 限制与背压

Handshake 协商且配置设硬上限：

- `maxEnvelopeBytes`：单个 Protobuf application message 的编码字节上限，在完整反序列化前检查。
- `maxPayloadBytes`：Publication/MQTT payload 上限，必须不小于 Broker 对外接受的集群级上限。
- `maxBatchItems` 和 `maxBatchBytes`：Route batch 双重限制。
- `maxInflightPerLane`：已发送但未完成的 command/batch 上限。
- `maxReorderWindow`：只用于检测短暂乱序，达到上限立即关闭 lane，不无限等待缺口。
- `maxFragmentBytes`：单个 RSocket fragment 上限；协商后双方发送端都使用较小值。
- `sessionLaneCount/routeLaneCount`：每对 peer 固定范围，不能按客户端数量增长。

Request N 分别约束每个 request-channel/request-stream 方向的在途 frame；mailbox 或 Outbox 达到高水位时停止追加 demand。Lease 根据 peer 总体负载决定是否允许创建新的 request、Session/Route channel，不代替既有 channel 的背压。fragment 重组过程同时受 `maxFragmentBytes` 和 `maxEnvelopeBytes` 约束。

v1 不启用应用层压缩，避免压缩炸弹和 CPU 不可控。RSocket fragmentation 只拆帧，不改变 Protobuf message 边界。后续压缩必须作为 capability 协商，声明算法、原始长度和解压后硬上限。

## 14. 版本演进

- Route major 版本与 `ProtocolVersion.major` 同步；不兼容变更发布 `.v2` route。
- 同 major 内只增加 optional/repeated 字段和 enum 值，不改变既有字段语义。
- 发送方只发送 negotiated minor 支持的字段；接收方保留未知字段但不执行未知 required feature。
- rolling upgrade 期间 Metadata 保存允许的 min/max 版本，新节点在加入 assignment 前完成 Handshake 兼容检查。
- 字段删除先停止写入至少一个兼容窗口，再标记 `reserved`。
- Protobuf schema、capability 名称、Status Code 和 route 名称都需要 golden compatibility test。

## 15. 测试要求

`cluster-testkit` 必须覆盖：

1. 每种 message 的 binary round-trip、golden bytes、未知字段、缺失 required semantic 和大小边界。
2. Handshake major/minor、capability、limits、plugin fingerprint 和重复连接仲裁。
3. Session lane 的空 Client ID 分配、重复 Attach、缺口、stale generation、command/action 独立顺序和中途断线重试。
4. Route batch 的 Topic Alias/Subscription Identifier 清理、部分成功、RouteAck 丢失、Outbox 重试、重复 Delivery 和 stale mapping/epoch。
5. Request N=0、Lease 耗尽、慢消费者、fragmentation、Resume 成功与失败。
6. 非法 UTF-8、未知 oneof、超大 Payload、错误 hash 和恶意长度字段。
7. Ratis command deterministic bytes、旧版本日志 replay、snapshot manifest 和 state hash。
8. mTLS identity 与 payload identity 不一致、跨 cluster 请求和过期 incarnation。

测试不得依赖 `sleep` 推测完成，使用 StepVerifier、虚拟时钟、可控 peer transport 和持久化 probe。

## 16. 协议验收标准

1. Session、Route、Control 和 Health 全部只有 RSocket route，没有 gRPC service/stub。
2. 所有跨节点业务操作包含稳定 request/command/task ID，并有明确的重复处理结果。
3. connection generation、lane sequence、command sequence、action sequence 和 route sequence 互不混用。
4. `NOT_OWNER/STALE_*` 不关闭 peer connection，身份或大版本错误必须关闭。
5. RouteAck、CommandResult.OK 只在各自持久化提交点之后发送。
6. Protobuf 生成类型、RSocket Payload 和 Ratis Message 不进入 `core` 或 `runtime` 公共 API。
7. 所有 message、batch、lane、Resume 和重排状态都有上限与指标。
8. 至少两个相邻 minor 版本可以 rolling upgrade，旧 Raft Log 和 snapshot 可以恢复。
