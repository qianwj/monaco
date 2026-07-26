# Monaco 测试 Case

> 状态：Proposed
>
> 更新日期：2026-07-25
>
> 配套方案：[Testkit 实现方案](testkit-implementation.md)

## 1. 使用约定

### 1.1 Case 编号

| 前缀 | 范围 |
| --- | --- |
| `TK` | testkit 自测试 |
| `PR` | protocol 纯规则和值对象 |
| `ST` | BrokerStore 契约 |
| `CO` | core 组件场景 |
| `TR` | runtime-reactor 的 transport.netty 接入层 |
| `BR` | broker 装配和生命周期 |
| `E2E` | 单机 MQTT 端到端 |
| `PL` | 插件系统 |
| `CL` | 集群 shared-store |
| `RF` | replicated-store/Raft |
| `IOP` | 客户端互操作 |
| `NF` | 非功能和依赖边界 |

### 1.2 层级与阶段

层级：`unit`、`component`、`contract`、`integration`、`recovery`、`interop`、`cluster`。

阶段：单机 `P0-P4`，集群 `C0-C6`。早期集群设计中的复制存储 `R1-R3` 按最新模块详细设计归入 `C5`；一个 case 标记的阶段表示最迟应进入默认 CI 的阶段。

统一前提：

- 测试使用 JUnit 5 Assertions；Reactor 流使用 StepVerifier。
- Broker Listener 使用端口 `0`，fixture 等待 `READY` 后才返回。
- 组件测试使用确定性 Clock/Scheduler，不调用 `Thread.sleep`。
- 每个 case 结束后断言无未消费异步错误、无资源泄漏；失败输出统一 Trace。
- 表中的“提交”指 `BrokerStore.transact` 成功完成，不是仅执行了事务回调。

## 2. Testkit 自测试

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| TK-001 | unit/P0 | Clock 从固定时刻开始，前进 5 秒 | `now()` 精确增加 5 秒；禁止负 Duration 和时间倒退 |
| TK-002 | unit/P0 | 注册三个不同到期时间的任务，一次跨过全部时间 | 按 dueAt 执行；Clock 最终值和执行轨迹可预测 |
| TK-003 | unit/P0 | 同一 key 注册两次任务后推进时间 | 只运行后一任务，旧任务不产生副作用 |
| TK-004 | unit/P0 | 注册任务后 cancel 两次 | 首次返回成功、再次返回未找到，任务不执行 |
| TK-005 | unit/P0 | 多个任务同一时刻到期，其中一个再注册同刻任务 | 按注册序号稳定执行，并处理级联任务；超过上限明确失败 |
| TK-006 | unit/P0 | 到期 action 返回 `Mono.error` | `advanceBy` 以同一错误终止，错误不被吞掉，其余任务状态可诊断 |
| TK-007 | unit/P0 | 多线程并发写入 Packet/Event Probe | 快照完整、顺序号唯一递增、调用方不能修改快照 |
| TK-008 | unit/P0 | `awaitMatching` 到期仍不满足 | AssertionError 包含 predicate、当前快照、最后错误和 timeout |
| TK-009 | unit/P0 | FaultPlan 配置 once、always 和第 N 次触发 | 每种规则只在声明调用上触发，计数线程安全 |
| TK-010 | unit/P0 | Fixture 启动到第二阶段失败 | 已启动资源严格逆序关闭，原始异常为主异常，清理异常为 suppressed |
| TK-011 | integration/P1 | 并行启动多个端口为 0 的 Broker fixture | 实际端口均非 0、互不冲突，所有实例达到 READY |
| TK-012 | integration/P1 | 测试抛异常且已有客户端连接 | 客户端、Listener、Scheduler、Store 和临时目录全部释放 |
| TK-013 | unit/P0 | PacketAssertions 比较 Payload 和有序 User Properties | 使用内容而非数组引用比较；失败输出首个差异位置 |
| TK-014 | contract/P0 | 解析生产模块的 `runtimeClasspath` | 不含 testkit、JUnit、reactor-test、测试 MQTT client 或 Testcontainers |

## 3. Protocol Case

### 3.1 值对象与主题规则

以下矩阵通过 JUnit 参数化测试实现，每一行必须在失败信息中显示输入值。

| ID | 层级/阶段 | 输入或动作 | 期望 |
| --- | --- | --- | --- |
| PR-001 | unit/P0 | `PacketId`：1、65535、0、65536 | 边界值接受；0 和 65536 拒绝 |
| PR-002 | unit/P0 | `QoS.valueOf`：0、1、2、-1、3 | 合法值精确映射，非法值抛 `IllegalArgumentException` |
| PR-003 | unit/P0 | `ClientId`：空、ASCII、合法多字节、65535/65536 UTF-8 字节 | 空值按 CONNECT 规则保留；按编码字节而非 Java char 计长 |
| PR-004 | unit/P0 | Payload 源 byte[] 在构造后被调用方修改 | record 保持不可变，`data()` 返回值不能修改内部内容 |
| PR-005 | unit/P0 | Topic Name：`a`、`a/b`、`/a/`、空、含 `+/#`、U+0000、超长 | 只接受合法非空 Topic Name，错误分类为 Topic Name Invalid |
| PR-006 | unit/P0 | Topic Filter：`a/+`、`a/#`、`+`、`#`、`a+`、`a/#/b`、空、U+0000 | 通配符只在合法层级出现，非法值分类为 Topic Filter Invalid |
| PR-007 | unit/P1 | Shared Filter：`$share/g/a/+`、空 group、group 含 `+/#/`、缺实际 filter | 解析合法 group/filter；非法格式全部拒绝 |
| PR-008 | unit/P0 | 使用 `+` 匹配空层级和普通层级 | `+` 恰好匹配一个层级，包括空层级 |
| PR-009 | unit/P0 | 使用尾部 `#` 匹配父主题和任意后缀 | `sport/#` 同时匹配 `sport`、`sport/` 和更深层级 |
| PR-010 | unit/P0 | 普通过滤器 `#`/`+/x` 匹配 `$SYS/x` | 不匹配；显式 `$SYS/#` 可以匹配 |
| PR-011 | unit/P0 | 含前后/连续 `/` 的名称和过滤器匹配 | 空层级被保留，不因 split 丢失而误匹配 |
| PR-012 | unit/P0 | ReasonCode 遍历 | wire code 正确；`<0x80` 为 success，`>=0x80` 为 error；别名 code 合法 |
| PR-013 | unit/P0 | 构造含 byte[]/List 的报文和属性后修改源值或 accessor 返回值 | Payload、密码/Auth Data、User Properties 和 Subscription 列表保持不可变 |

`PR-004` 是当前 `Payload(byte[])` 实现必须补齐的回归 case；不可变报文模型不能只保证 record 引用不可变。

### 3.2 属性与 UTF-8

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| PR-020 | unit/P1 | 对每类 packet 遍历允许属性矩阵 | 允许的属性通过，禁止属性返回 Protocol Error 且响应类型正确 |
| PR-021 | unit/P1 | 单值属性出现两次 | 返回 Protocol Error；User Property 和允许重复的 Subscription Identifier 不受影响 |
| PR-022 | unit/P1 | User Property 含重复 key/value 并交错排列 | 顺序和重复值在 decode/model/encode 后完全保留 |
| PR-023 | unit/P3 | 一个出站 PUBLISH 合并多个匹配订阅的 Subscription Identifier | 全部 ID 保留，顺序按确定的订阅匹配规则输出 |
| PR-024 | unit/P1 | Receive Maximum 为 0、1、65535 | 0 拒绝；1 和 65535 接受 |
| PR-025 | unit/P1 | Maximum Packet Size 为 0 和合法正整数 | 0 拒绝；合法值接受且无整数溢出 |
| PR-026 | unit/P3 | Topic Alias 为 0、边界值、超过协商上限 | 0 和越界返回 Topic Alias Invalid；合法值接受 |
| PR-027 | unit/P3 | Subscription Identifier 为 0、合法值、超过规范上限 | 只接受合法范围 |
| PR-028 | unit/P3 | Payload Format=UTF-8，但 Payload 含非法 UTF-8 | 返回 Payload Format Invalid，不持久化消息 |
| PR-029 | integration/P1 | Raw client 发送含 U+0000、未配对代理项编码或禁止控制字符的 UTF-8 字段 | 按字段规则返回 Malformed Packet/Protocol Error 后关闭，不进入 core |
| PR-030 | unit/P1 | PacketResponseFactory 处理请求问题信息、合法 ACK 属性 | 响应只包含规范允许且客户端请求的属性 |

### 3.3 状态机

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| PR-040 | unit/P2 | 入站 QoS 2 NEW 收到 PUBLISH | 进入 RECEIVED，action 包含持久化和 PUBREC |
| PR-041 | unit/P2 | RECEIVED 收到相同 Packet ID 的 DUP PUBLISH | 状态不重复推进，返回 PUBREC，不产生第二次 route action |
| PR-042 | unit/P2 | RECEIVED 收到 PUBREL | 进入 COMPLETE，生成 PUBCOMP 和清理/完成 action |
| PR-043 | unit/P2 | COMPLETE/未知 ID 收到 PUBREL | 返回合法 Packet Identifier Not Found 结果，无业务副作用 |
| PR-044 | unit/P2 | 入站 QoS 2 收到当前状态不允许的 ACK | Transition 明确拒绝，Reason Code 和 close 行为符合阶段 |
| PR-045 | unit/P1 | 出站 QoS 1 PUBLISH_SENT 收到 PUBACK | 进入 COMPLETE，释放 Packet ID |
| PR-046 | unit/P2 | 出站 QoS 2 PUBLISH_SENT 收到 PUBREC | 进入 PUBREL_SENT，action 发送 PUBREL |
| PR-047 | unit/P2 | 出站 QoS 2 PUBREL_SENT 收到 PUBCOMP | 进入 COMPLETE，释放 Packet ID |
| PR-048 | unit/P2 | 出站状态机收到重复 PUBREC | 重发 PUBREL，不创建第二条 Delivery |
| PR-049 | unit/P2 | 出站状态机收到乱序/未知 Packet ID ACK | 返回 Packet Identifier Not Found 或协议映射，不改变其他 Inflight |
| PR-050 | unit/P3 | Enhanced AUTH 从 initial 到 Continue、Success | Authentication Method 绑定不变，Success 后状态清理 |
| PR-051 | unit/P3 | Enhanced AUTH 中途改变 method、无状态发 AUTH 或非法 reason | 返回 Bad Authentication Method/Protocol Error 并关闭 |

## 4. BrokerStore Contract

Memory 和单机 RocksDB 都继承同一 `BrokerStoreContract`。`ST-014` 以后按 capability 运行，但不得因适配器名称跳过。`ST-021` 至 `ST-023` 是 `cluster-testkit` 的 `ClusterOutboxContract`/`ClusterCoordinatorContract` 扩展，供 PostgreSQL 和 sharded RocksDB 复用。

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| ST-001 | contract/P0 | 空 Store 调用 `recover()` | 返回结构完整的空 Snapshot，不返回 null |
| ST-002 | contract/P0 | 一个事务写 Session、Subscription、Message、Delivery、Inflight、Retain、Will | 提交后一次 recover 能观察全部实体及关系 |
| ST-003 | contract/P0 | 事务写多个实体后回调抛异常 | 所有写入都不可见，没有部分提交 |
| ST-004 | contract/P0 | Store 在 commit 前注入失败 | Mono 失败且 Snapshot 不变 |
| ST-005 | contract/P1 | 相同业务 ID 的 Message/Delivery 重复写 | 根据端口语义幂等或返回确定冲突；绝不生成两份 Delivery |
| ST-006 | contract/P1 | 替换同 clientId + filter 的订阅 | 只保留新订阅选项，不生成重复索引项 |
| ST-007 | contract/P1 | 同一 clientId 分配 Packet ID，释放后重用 | 活跃 Inflight 内唯一；完成后允许安全重用 |
| ST-008 | contract/P1 | 事务删除 Session 及其从属状态 | 删除范围与 Session Expiry 规则一致，不误删 Message 的其他引用 |
| ST-009 | contract/P1 | 空 Payload、最大边界属性和多个 Subscription ID round-trip | 内容、顺序、QoS、expiry 和 DUP 信息无损 |
| ST-010 | contract/P1 | Retained 同 Topic 连续 upsert，再用空 retained publish 删除 | 每个 Topic 最多一条，删除后 recover 不返回记录 |
| ST-011 | contract/P2 | 两个并发事务更新同一 Session generation | 结果可串行化，generation 单调且无丢失更新 |
| ST-012 | contract/P2 | 事务回调返回后保留 TransactionView 并再次调用 | 明确拒绝，不能在事务完成后泄漏异步写入 |
| ST-013 | contract/P2 | 调用 `close()` 两次，再 transact/recover | close 幂等；关闭后的操作稳定失败且不挂起 |
| ST-014 | recovery/P2 | 提交后正常 close/restart | 持久 Store 完整恢复；Memory capability 明确跳过 |
| ST-015 | recovery/P2 | commit 完成后不 close 直接 crash/restart | 已确认事务存在，未确认事务按 Store 原子边界全有或全无 |
| ST-016 | recovery/P2 | 每个 QoS 1/2 Inflight 状态分别持久化后 restart | state、packetId、messageId、DUP 所需信息完整恢复 |
| ST-017 | recovery/P2 | 写入过期和未过期 Session/Message/Will 后恢复/清理 | 只清理到期数据，边界时刻语义一致 |
| ST-018 | contract/P2 | mutate 原始 byte[]/集合或 recover 返回对象 | 已存数据不变化，Snapshot/record 不泄漏可变引用 |
| ST-019 | recovery/P2 | 旧 schema 数据打开新 Store | 支持的迁移原子完成；不支持版本在写入前明确失败并保留数据 |
| ST-020 | contract/P2 | FaultPlan 在 commit 后向调用方返回错误，随后按同 ID 重试 | 不产生重复 Message/Delivery/Inflight，最终状态收敛 |
| ST-021 | contract/C2 | 同事务写 Message、Inbound Inflight 和 ClusterOutbox | 三者原子提交，任何一个失败时全部不可见 |
| ST-022 | contract/C2 | RouteTask/Delivery 使用相同稳定 ID 重试 | 唯一约束返回同一业务结果，不重复投递 |
| ST-023 | contract/C2 | 当前 epoch 和旧 epoch 并发写同一 partition | 当前 epoch 可提交，旧 epoch 在持久化边界被 fencing |

## 5. Core 组件 Case

### 5.1 CONNECT、Session 与连接生命周期

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CO-001 | component/P0 | 新物理连接发送首个合法 CONNECT，Clean Start=true | 认证/授权后提交新 Session，再发成功 CONNACK，Session Present=false |
| CO-002 | component/P0 | 未 CONNECT 的连接发送 PINGREQ/PUBLISH | 返回 Protocol Error 的 DISCONNECT/close，不创建 Session |
| CO-003 | component/P0 | 已 CONNECT 的连接再次发送 CONNECT | 连接级 Protocol Error，原 Session 不被重置 |
| CO-004 | component/P2 | 无旧 Session，Clean Start=false | 创建 Session，CONNACK Session Present=false |
| CO-005 | component/P2 | 有未过期 Session，Clean Start=false | 恢复订阅和 Inflight，Session Present=true |
| CO-006 | component/P2 | 有旧 Session，Clean Start=true | 原 Session/订阅/Inflight 原子清理，创建新 Session，Session Present=false |
| CO-007 | component/P2 | Session 已过期，Clean Start=false 重连 | 清理过期状态并创建新 Session，Session Present=false |
| CO-008 | component/P2 | 同 clientId 第二连接成功 CONNECT | 旧连接收到 Session Taken Over 并关闭，新 generation 生效 |
| CO-009 | component/P2 | 接管完成后旧连接迟到 `closed` 回调 | 不删除新连接状态，不发布/取消新连接的 Will |
| CO-010 | component/P0 | 认证拒绝或异常 | CONNECT 阶段发送合法失败 CONNACK，不创建持久 Session |
| CO-011 | component/P1 | 授权拒绝 connect | 返回 Not Authorized，不执行 Session 事务 |
| CO-012 | component/P0 | Keep Alive 到期且无控制报文 | Scheduler 触发 Keep Alive Timeout close，按异常断线处理 Will/Session |
| CO-013 | component/P0 | 在 Keep Alive deadline 前收到报文 | 旧 timer 被替换；推进到旧 deadline 不关闭，推进到新 deadline 才关闭 |
| CO-014 | component/P0 | 开启服务端分配 Client ID，空 Client ID 分别使用 Clean Start=true/false | true 时分配唯一 Client ID 并在 CONNACK 返回；false 时以 Client Identifier Not Valid 拒绝；关闭分配能力时两者均拒绝 |
| CO-015 | component/P2 | Session Expiry=0 的连接断开 | 事务清理 Session、Subscription、Inflight/queue；后续重连 Session Present=false |

### 5.2 PUBLISH、QoS 与投递

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CO-020 | component/P1 | 合法 QoS 0 PUBLISH 有匹配订阅 | 不创建入站 Inflight；生成目标 Delivery/发送，发送失败不伪造 ACK |
| CO-021 | component/P1 | 合法 QoS 1 PUBLISH | Message/Inflight/Delivery commit 后才发送 PUBACK；Trace 顺序精确 |
| CO-022 | component/P1 | QoS 1 Store commit 失败 | 不发送成功 PUBACK、不发布 MessageAccepted，消息不可见 |
| CO-023 | component/P1 | QoS 1 commit 成功但 ConnectionSink 发送失败 | 状态保留供恢复，连接关闭；不回滚 Message/Delivery |
| CO-024 | component/P2 | 首次 QoS 2 PUBLISH 后收到 PUBREL | 每阶段先提交再 ACK；最终仅一个 Message 和一组 Delivery |
| CO-025 | component/P2 | 重复 QoS 2 PUBLISH/PUBREL | 重发对应 ACK，不重新执行 Publish Hook、不重复路由 |
| CO-026 | component/P1 | 同一发布命中同客户端多个重叠订阅 | 每客户端只生成一个 Delivery，QoS/Retain/Subscription ID 按合并规则计算 |
| CO-027 | component/P1 | 发布无匹配订阅 | QoS 1/2 返回 No Matching Subscribers（允许时），不创建 Delivery |
| CO-028 | component/P1 | Publish 授权拒绝 | QoS 对应 PUBACK/PUBREC reason 合法；QoS 0 按错误映射处理；无持久化/路由 |
| CO-029 | component/P1 | Receive Maximum=1，已有一个出站 Inflight | 第二条 Delivery 留队列；收到 ACK 后立即 drain 下一条 |
| CO-030 | component/P1 | ACK 未知 Packet ID 或与 QoS 状态不符 | 返回确定错误，不释放其他 Inflight 或扩大窗口 |
| CO-031 | component/P2 | 出站 QoS 1 在 PUBLISH_SENT 时断线重连 | 恢复后以相同 Packet ID、DUP=1 重发 PUBLISH |
| CO-032 | component/P2 | 出站 QoS 2 在 PUBLISH_SENT/PUBREL_SENT 时分别重连 | 分别重发 DUP PUBLISH/PUBREL，不重复 Message/Delivery |
| CO-033 | component/P1 | 离线持久 Session 有排队 Delivery 后重连 | CONNACK 后按窗口和原队列顺序投递 |
| CO-034 | component/P1 | 离线队列达到上限 | 按配置拒绝/丢弃并记录明确 reason/telemetry，内存不继续增长 |
| CO-035 | component/P1 | 两个目标 Session，一个 sink 缓慢 | 目标 Session 可并行，慢目标不阻塞发布者 ACK 和其他目标 |
| CO-036 | component/P1 | DomainEventSink 在 MessageAccepted 上失败 | Store/ACK/Delivery 不回滚、不重复；记录事件失败 |

### 5.3 SUBSCRIBE、UNSUBSCRIBE 与 Retain

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CO-040 | component/P1 | 一个 SUBSCRIBE 含合法、未授权和非法 filter | SUBACK reason 数量/顺序与输入一致，只提交接受项 |
| CO-041 | component/P1 | 相同 filter 再订阅不同 QoS/options | 原订阅被替换而非追加，Routing Index 原子切换 |
| CO-042 | component/P1 | UNSUBSCRIBE 同时包含存在和不存在 filter | 顺序返回 Success/No Subscription Existed，删除存在项 |
| CO-043 | component/P1 | SUBSCRIBE commit 失败 | 不更新 Routing Index、不发成功 SUBACK、不 replay retained |
| CO-044 | component/P1 | Store commit 成功，Routing Index 切换后 Event sink 失败 | SUBACK/路由有效，事件失败不回滚 |
| CO-045 | component/P1 | No Local=true，发布者与订阅者 clientId 相同 | 不创建本地 Delivery；其他 client 正常收到 |
| CO-046 | component/P3 | RAP=false/true 收到原 retained 来源的实时/回放消息 | Retain 标志按订阅选项设置 |
| CO-047 | component/P3 | Retain Handling=0/1/2，分别新订阅和替换订阅 | 0 总回放，1 仅新建时回放，2 从不回放 |
| CO-048 | component/P3 | retained PUBLISH 非空后新订阅 | 回放 QoS 为 publish/subscription QoS 最小值，保留允许属性 |
| CO-049 | component/P3 | retained PUBLISH 空 Payload | 原 RetainedRecord 删除，后续订阅无回放 |
| CO-050 | component/P3 | 多个重叠订阅带不同 Subscription Identifier | 单次转发包含所有命中 ID，顺序稳定且允许重复语义正确 |
| CO-051 | component/P3 | `$SYS/x` 发布与普通 `#`、显式 `$SYS/#` 订阅并存 | 只投递给显式系统主题订阅 |
| CO-052 | component/P3 | 同共享组有多个本地消费者 | 每条 publication 只选一个在线成员，选择策略稳定轮转 |

### 5.4 Topic Alias、Will、Expiry 与限制

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CO-060 | component/P3 | 首个 PUBLISH 同时给 Topic 和合法 Alias，后续只给 Alias | 两次使用同一 Topic；第二次持久 Message 保存解析后的 Topic |
| CO-061 | component/P3 | 使用未建立 Alias、Alias=0 或超过协商上限 | Topic Alias Invalid，报文不进入 Hook/Store |
| CO-062 | component/P3 | 断线后新连接复用旧 Alias | 旧映射不存在，按 Topic Alias Invalid 处理 |
| CO-063 | component/P3 | 网络异常断开且 Will Delay=0 | Will 经普通 PublishService 发布一次，产生正常 Message/Delivery/Event |
| CO-064 | component/P3 | 正常 DISCONNECT | pending Will 被取消且永不发布 |
| CO-065 | component/P3 | DISCONNECT reason=Disconnect With Will | 不等待 Will Delay，立即发布一次 |
| CO-066 | component/P3 | Will Delay=30s，Session Expiry=10s | 在 10s Session 到期边界发布 Will，Session/Will 清理顺序可追踪 |
| CO-067 | component/P3 | Will Delay=10s，Session Expiry=30s | 10s 发布 Will，Session 保留至 30s |
| CO-068 | component/P3 | 同 clientId 在 Will deadline 前恢复 Session | 取消旧 pending Will；推进超过 deadline 仍无发布 |
| CO-069 | component/P3 | 连接接管且旧 Will Delay=0 | 旧连接 Will 最多发布一次，迟到 close 不影响新连接 Will |
| CO-070 | component/P1 | 入站 packet 超 Broker Maximum Packet Size | 不持久化，返回 Packet Too Large 的连接级错误 |
| CO-071 | component/P1 | 目标客户端声明更小 Maximum Packet Size | Broker 绝不发送超限 packet；Delivery 进入明确 drop/error 策略并计数 |
| CO-072 | component/P3 | Message Expiry 在离线期间到期 | 重连不投递，清理 Message/Delivery 且记录 expiry/drop |

### 5.5 Mailbox 与并发

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CO-080 | component/P0 | 同 connection 连续提交两个命令，第一个被 Gate 暂停 | 第二个不越过第一个执行；释放后按接收顺序完成 |
| CO-081 | component/P1 | 不同 connection/clientId 的命令，第一个被暂停 | 第二个可以独立完成，证明不存在全局串行化 |
| CO-082 | component/P2 | 两个 connection 同 clientId 同时 CONNECT/SUBSCRIBE | Session Mailbox 形成确定顺序，无双 generation 或丢订阅 |
| CO-083 | component/P1 | Routing Index 更新与 PUBLISH 匹配并发 | publication 使用提交前或提交后的完整快照，不观察半更新状态 |
| CO-084 | component/P1 | 一个 Mailbox action 失败 | 后续命令仍可按策略执行/连接关闭，队列不会永久卡死 |
| CO-085 | component/P1 | Mailbox 达到配置上限 | 停止接收或返回 Server Busy/Quota，队列大小不超过上限 |
| CO-086 | component/P1 | 在 mailbox action 中检测阻塞调用 | core 路径没有 `block/join`，阻塞检测器不报告 EventLoop 阻塞 |
| CO-087 | component/P2 | 跨 Session 投递同时发生连接接管 | 不同步等待另一个 Mailbox，无死锁；Delivery 属于最终 generation |

## 6. Transport 与 Broker Case

### 6.1 Transport

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| TR-001 | integration/P0 | TCP Listener 配置端口 0 并 start | start 完成时已绑定，暴露实际端口且可连接 |
| TR-002 | integration/P0 | 端口已占用 | start 返回原始 bind 错误，不进入 READY，已创建资源关闭 |
| TR-003 | component/P0 | Netty CONNECT/PINGREQ/DISCONNECT 双向映射 | 所有字段、reason 和属性无损，ByteBuf 不泄漏到 protocol |
| TR-004 | component/P1 | PUBLISH/SUBSCRIBE/ACK 双向映射参数矩阵 | QoS、DUP、Retain、Packet ID 和属性精确映射 |
| TR-005 | integration/P1 | 同连接快速连续发送多个 packet | 调用 `BrokerEngine.received` 严格 `concatMap` 有序，最大并发为 1 |
| TR-006 | integration/P0 | socket reset、TLS error、codec error | 映射为正确 DisconnectCause，`closed` 恰好调用一次 |
| TR-007 | integration/P3 | WebSocket 使用 `mqtt` subprotocol | 握手成功且复用同一 BrokerEngine 场景；错误 subprotocol 拒绝 |
| TR-008 | integration/P4 | TLS/mTLS 合法、过期、未知 CA、client cert 缺失 | 合法连接通过；非法连接在进入 core 前失败且不泄露敏感信息 |
| TR-009 | integration/P1 | Raw client 发送畸形 Remaining Length/固定头 | 直接关闭或合法 DISCONNECT，不抛未处理异常、不分配 Session |
| TR-010 | component/P1 | 出站 sink 写失败或 channel 已关闭 | Mono 失败并触发一次 close 清理，不重复释放 ByteBuf |

### 6.2 Broker 生命周期与配置

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| BR-001 | integration/P0 | 有效默认测试配置启动 | 顺序为 Store recover -> core -> plugins -> bind -> READY |
| BR-002 | component/P0 | 每个启动阶段依次注入失败 | 已启动资源按逆序关闭；健康状态不曾为 READY |
| BR-003 | integration/P0 | READY 后 graceful stop 两次 | STOPPING 后 Listener 拒绝新连接，资源释放，stop 幂等 |
| BR-004 | component/P0 | 参数/System Property/Environment/File/Default 同时给不同值 | 按文档覆盖顺序产生一个不可变 BrokerConfig |
| BR-005 | component/P0 | 端口、TLS、限制或路径配置非法 | bind/Store 打开前集中失败，错误包含配置 key 不包含 secret |
| BR-006 | integration/P1 | 非关键 telemetry/event backend 启动失败 | 按策略进入 DEGRADED，MQTT 主链路仍工作 |
| BR-007 | integration/P3 | required plugin 失败或版本不兼容 | Broker 不进入 READY；optional plugin 失败时按策略 DEGRADED |
| BR-008 | integration/P0 | Fixture 启动后读取 address | 返回实际监听地址，不返回请求值 0 或默认 1883 |
| BR-009 | unit/P0 | 读取 `BrokerConfigDefaults.defaults()` | 默认配置通过校验；QoS2、Retain、Wildcard、Subscription ID、Shared Subscription 默认可用，端口/上限符合配置设计 |
| BR-010 | unit/P0 | 对 maxPacketSize、Receive Maximum、Topic Alias、Expiry、Keep Alive 做边界参数化测试 | 接受上下界，拒绝越界及 max 小于 default 的组合，错误包含配置 key |
| BR-011 | unit/P0 | TCP/WS 都关闭，或 TLS 开启但 cert/key 缺失 | 集中校验拒绝，尚未创建 transport 类型或访问文件 |
| BR-012 | unit/P0 | 配置输入秒转换为内部毫秒，覆盖 0、正常值和溢出值 | 转换精确，Keep Alive 1.5 倍计算无溢出；非法大值在构建阶段拒绝 |

## 7. 单机端到端与恢复 Case

以下 case 在真实 TCP 上运行；适用场景模板同时复用于 WebSocket。组件层已经证明的内部顺序不在 E2E 通过私有状态重复断言。

| ID | 层级/阶段 | 场景 | 端到端结果 |
| --- | --- | --- | --- |
| E2E-001 | integration/P0 | CONNECT -> PINGREQ -> DISCONNECT | 成功 CONNACK、PINGRESP、正常关闭 |
| E2E-002 | integration/P1 | subscriber SUBSCRIBE，publisher QoS 0/1 PUBLISH | payload/topic/properties 正确，QoS 1 双向 ACK 完成 |
| E2E-003 | integration/P1 | wildcard、重叠订阅和 `$SYS` 矩阵 | 投递目标和单客户端去重符合 CO-026/CO-051 |
| E2E-004 | integration/P2 | 四种 Clean Start/已有 Session 组合 | Session Present、订阅恢复和清理符合 CO-004~CO-007 |
| E2E-005 | integration/P2 | 同 clientId 连接接管 | 旧客户端收到/观察 Session Taken Over，新连接继续工作 |
| E2E-006 | integration/P2 | 完整双向 QoS 2 | PUBREC/PUBREL/PUBCOMP 顺序正确，subscriber 只收到一次业务消息 |
| E2E-007 | recovery/P2 | 在入站 QoS 2 的 PUBLISH commit、PUBREC、PUBREL commit 边界 crash | restart 后协议继续，业务投递最多一次且状态最终清理 |
| E2E-008 | recovery/P2 | 在出站 QoS 1/2 各 Inflight 状态 crash | reconnect 后 DUP/Packet ID 正确，无消息丢失 |
| E2E-009 | integration/P3 | Retain Handling/RAP/空 payload 删除矩阵 | 客户端观察结果符合 CO-046~CO-049 |
| E2E-010 | integration/P3 | Will Delay、Session Expiry、恢复取消矩阵 | 使用注入 Clock 精确触发，Will 只在规定边界发布一次 |
| E2E-011 | integration/P3 | Topic Alias 建立、复用、越界和重连 | 合法复用成功；非法值 reason 正确；重连清空 |
| E2E-012 | integration/P3 | Enhanced AUTH Continue/Success/Reject | AUTH exchange provider 绑定，最终 CONNACK/close 正确 |
| E2E-013 | integration/P1 | Receive Maximum=1 且 subscriber 延迟 ACK | 第二条不越过窗口，ACK 后继续 |
| E2E-014 | integration/P1 | Raw client 非法属性/UTF-8/packet phase | reason、响应 packet 类型和 close 行为符合错误矩阵 |
| E2E-015 | integration/P3 | 共享订阅组 3 个客户端接收多条消息 | 每条仅一个成员接收，成员不可用时其余成员继续 |

## 8. Plugin Case

`PL-001` 至 `PL-016` 由 `PluginHarness` 运行；Invoker 相关 case 对 Local 和 gRPC 使用同一参数化契约。

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| PL-001 | contract/P3 | 插件依赖图、priority 和 id 同时影响顺序 | 先拓扑排序，再 priority 升序，再 id 字典序 |
| PL-002 | contract/P3 | 依赖缺失或形成环 | 在插件 start 前拒绝 Catalog，错误包含依赖链 |
| PL-003 | contract/P3 | Authentication providers 返回 Abstain、Success、Reject | first-applicable；Success/Reject 后不调用后续 provider |
| PL-004 | contract/P3 | Authorization policies 混合 Abstain/Allow/Deny | deny-overrides；无 Allow 时进入默认策略 |
| PL-005 | contract/P3 | Publish interceptors 顺序修改 Topic/Payload/User Property | 后一 Hook 看到前一结果；最终结果重新校验后再授权/持久化 |
| PL-006 | contract/P3 | Hook 尝试提高 QoS、改 Packet ID/DUP/clientId | runtime 拒绝非法 Decision，映射 Implementation Specific Error |
| PL-007 | contract/P3 | Subscription Hook 对请求条目分别 allow/reject/降 QoS | 输出条目和 SUBACK reason 保持原顺序，不能扩大 filter |
| PL-008 | contract/P3 | 单插件 timeout 和 chain budget 分别到期 | security/decision fail closed；调用不自动重试；指标各增加一次 |
| PL-009 | contract/P3 | Event listener timeout/异常/队列溢出 | fail open，其他 listener 继续，MQTT Store/ACK 不改变，drop 指标增加 |
| PL-010 | component/P3 | QoS 2 duplicate、offline delivery、Retain replay、outbound retry | Publish 修改 Hook 只在首次接纳前调用一次，持久结果被复用 |
| PL-011 | contract/P3 | required/optional 插件 start 失败 | required 阻止 READY；optional 标记 DEGRADED；已启动插件逆序 stop |
| PL-012 | contract/P3 | 正常停止含依赖链 | 停止新请求，drain 有界完成，按依赖逆序 stop |
| PL-013 | contract/P3 | ServiceLoader 无 Provider、多个 Provider、父 ClassLoader Provider | 三种情况均拒绝，错误可诊断且不激活 Hook |
| PL-014 | contract/P3 | Factory 构造/create 失败或 capabilities 与 hooks 不一致 | 插件进入 FAILED，不污染其他插件 Chain |
| PL-015 | contract/P3 | 插件 JAR 内打包 plugin-api/protocol 或依赖版本冲突 | 重复 API 类拒绝；私有依赖冲突由独立 ClassLoader 隔离 |
| PL-016 | contract/P3 | API major 不兼容、只增加可选 DTO 字段 | major 不兼容拒绝；兼容版本可加载 |
| PL-017 | contract/P3 | Local/Grpc Invoker 输入同一 Hook/Decision/timeout | 合并、失败、Reason Code 和 invocationId 语义一致 |
| PL-018 | integration/P3 | UDS Handshake 版本/identity/capability 不匹配 | required source 启动失败，连接关闭且不进入 READY |
| PL-019 | integration/P3 | gRPC 断连、半开、deadline 后迟到响应、重连 | 当前调用按策略完成一次；迟到响应丢弃；队列有界并可恢复 |
| PL-020 | integration/P3 | EventStream 慢消费和队列满 | 按 drop-oldest/newest 配置丢弃，MQTT I/O 不阻塞 |
| PL-021 | component/P3 | Enhanced AUTH Continue 后另一个 provider 试图接管 | 只调用首轮绑定 provider；连接结束后认证状态清理 |
| PL-022 | component/P3 | Will Hook 修改内容后断线触发 | CONNECT 时校验并持久化一次；触发时不重复 Hook，发布持久结果 |

## 9. Cluster Case

集群模块当前尚未加入构建。以下 case 由未来独立的 `cluster-testkit` 提供，在 C1-C6 分阶段转为自动化，不应给基础 testkit 增加集群编译依赖。

`cluster-testkit` 发布六套契约：`PeerTransportContract`、`ClusterCoordinatorContract`、`ClusterOutboxContract`、`ReplicatedLogContract`、`ShardStateStoreContract` 和 `ClusterProfileContract`。

### 9.1 Peer 与 Coordination 契约

| ID | 层级/阶段 | Given / When | Then |
| --- | --- | --- | --- |
| CL-001 | contract/C3 | RSocket/gRPC Peer 使用相同 Handshake 输入 | clusterId、node/incarnation、version、capability 校验语义一致 |
| CL-002 | contract/C3 | 同 connection 的 Session commands 并发进入多个 lane | 按 sequence 严格交付，重复 sequence 幂等，缺口不乱序执行 |
| CL-003 | contract/C3 | Peer deadline 到期后迟到响应 | 调用只完成一次；重试保持 requestId，不生成新业务 ID |
| CL-004 | contract/C3 | Route channel downstream 不 request | 上游尊重背压，lane/heap 队列不超过配置上限 |
| CL-005 | contract/C3 | Peer 断开、半开、Resume 成功/失败 | 未完成任务保留 Outbox；Session lane 不确定时关闭客户端连接 |
| CL-006 | contract/C2 | 节点 lease 续租使用数据库时间 | lease 单调，不受 Broker 本地时钟漂移决定所有权 |
| CL-007 | contract/C2 | 两个 coordinator 同时尝试 assignment | 只有一个 generation 提交，epoch 不回退 |
| CL-008 | contract/C2 | lease 过期但新 assignment 尚未提交 | 新节点不能写，旧节点也不能靠 DNS/心跳证明所有权 |
| CL-009 | contract/C2 | assignment 切换后旧 Owner 恢复并提交旧 epoch | Store 明确 fencing，fencing 指标和日志字段完整 |
| CL-010 | contract/C2 | nodeId 相同、incarnation 不同 | 旧 incarnation 的请求全部拒绝，新实例需新 assignment |
| CL-011 | contract/C1 | v1 PeerEnvelope/Session/Route/Control 消息 encode/decode | 所有稳定 ID、epoch、deadline、sequence 和 trace 字段无损 round-trip |
| CL-012 | contract/C1 | 新版本追加未知 Protobuf 字段，由前一兼容版本读取并转发 | 已知字段可用，未知字段不导致解码失败或破坏兼容性 |
| CL-013 | contract/C1 | 不支持的 major protocol version 或必需 capability 缺失 | Handshake 在进入 assignment 前明确拒绝 |
| CL-014 | unit/C1 | 相同 clientId/group+filter/routeKey 在不同节点计算 partition | stable hash 结果一致，partition 范围正确且不受 JVM 进程随机性影响 |

### 9.2 多节点协议与故障

| ID | 层级/阶段 | 场景 | 集群结果 |
| --- | --- | --- | --- |
| CL-020 | cluster/C3 | Client 连接 Ingress A，Session Owner 为 B | CONNECT 在 B 完成认证/事务，CONNACK 经 A 返回，Channel 不跨节点 |
| CL-021 | cluster/C3 | 同 clientId 分别从 A/C 同时 CONNECT | Owner 串行接管，只保留一个 generation，旧连接合法关闭 |
| CL-022 | cluster/C3 | QoS 1 publish 在 A，订阅 Session 位于 B/C | Message+Inflight+Outbox 提交后 PUBACK；各目标幂等 Delivery |
| CL-023 | recovery/C3 | RouteAck 提交后响应丢失，发送端重试同 routeTaskId | 接收端命中唯一约束，不创建/发送第二份 Delivery |
| CL-024 | recovery/C3 | Outbox 已提交、发送前 Owner 退出并迁移 | 新 Owner 继续 drain，publication 不丢失 |
| CL-025 | recovery/C3 | Store commit 前/后、PUBACK/PUBREC 前/后依次终止节点 | 未提交不成功 ACK；已提交可恢复；无重复业务交付 |
| CL-026 | cluster/C4 | Retain 写在节点 A，Subscriber 经 B 订阅 | B 从共享状态生成一次正确 retained Delivery |
| CL-027 | cluster/C4 | Will Owner 退出，deadline 后由新 Owner 接管 | Will 通过普通 Publish+Outbox 发布一次 |
| CL-028 | cluster/C4 | 同共享组成员跨三个节点 | 每个 message/group/filter 只选一个消费者，唯一键幂等 |
| CL-029 | recovery/C4 | Shared Group Owner 在选择提交前/后退出 | 前者由新 Owner 选择；后者恢复既有选择，不双投 |
| CL-030 | cluster/C3 | Peer 极慢且 Outbox/lane 达上限 | 入口施加背压或 Server Busy，内存有界，I/O EventLoop 不阻塞 |
| CL-031 | cluster/C6 | 双向网络分区 | 无合法 epoch/Store 权限的一侧不能提交；恢复后状态收敛 |
| CL-032 | cluster/C4 | graceful drain | 不再接新连接，迁移 assignment/Outbox，旧 epoch 失效后才释放资源 |
| CL-033 | cluster/C3 | 不兼容 cluster protocol 或 required plugin fingerprint | 节点保持 NOT_READY，不能进入 assignment |
| CL-034 | cluster/C6 | mTLS 证书轮换，期间保持既有/新 peer 连接 | 合法新证书生效；错误 identity 拒绝；不匿名降级 |
| CL-035 | cluster/C0 | 多 I/O EventLoop 接受大量连接 | 至少两个 I/O worker 有连接；每 connection/shard 命令仍严格有序 |
| CL-036 | contract/C5 | standalone、shared-store、replicated-store 运行同一 MQTT 行为集 | 外部可观察协议结果一致，差异仅在 Profile 的恢复/可用性边界 |
| CL-037 | cluster/C4 | Docker Compose 多节点启动、滚动重启和 advertise 检查 | readiness、节点发现、接管和数据恢复通过 |
| CL-038 | cluster/C4 | Kubernetes StatefulSet/headless service/PDB/preStop smoke test | nodeId/advertise 正确，drain 后滚动更新无双 Owner |
| CL-039 | cluster/C4 | Swarm DNSRR/Secret/rolling update smoke test | 使用同一所有权协议，peer 端口不公开且 Secret 不出现在日志 |

集群稳定条件必须通过 readiness、assignment generation、Outbox pending=0 和确定性 Probe 判断，禁止固定 sleep。

### 9.3 Replicated Store/Raft

| ID | 层级/阶段 | 场景 | 期望 |
| --- | --- | --- | --- |
| RF-001 | cluster/C5 | 三副本正常 append/commit/apply | `Mono` 仅在多数派 commit 且本地 RocksDB apply 后成功 |
| RF-002 | cluster/C5 | Shard 失去多数派 | 该 Shard 停止写和 MQTT 成功 ACK；其他健康 Shard 继续 |
| RF-003 | cluster/C5 | 两节点同时尝试 Leader 写 | 只有当前 term Leader 可提交，旧 Leader 被拒绝 |
| RF-004 | recovery/C5 | Raft commit 后、RocksDB apply 前 crash | 重启按日志重放一次，lastAppliedIndex 单调，无重复副作用 |
| RF-005 | recovery/C5 | RocksDB apply 后、响应前 crash | 重放依据 lastAppliedIndex 幂等跳过/确认，客户端恢复正确 |
| RF-006 | recovery/C5 | snapshot 创建/安装中断后重试 | 状态、lastAppliedIndex 和 checksum 一致，旧临时 snapshot 可清理 |
| RF-007 | cluster/C5 | learner catch-up 后提升 voter | 未 catch-up 不提升；配置变更保持多数派安全 |
| RF-008 | recovery/C5 | Leader transfer 和 Shard migration 中有持续 publish | commit 顺序不破坏，Outbox 最终 drain，无双 Owner 写 |
| RF-009 | recovery/C5 | 跨 Shard Outbox 在源 commit、目标 apply、RouteAck 各边界 crash | 相同稳定 ID 重试，目标状态最终一次生效 |

## 10. 互操作与非功能 Case

| ID | 层级/阶段 | 场景 | 期望 |
| --- | --- | --- | --- |
| IOP-001 | interop/P3 | HiveMQ MQTT Client 运行 CONNECT/QoS0/1/2/SUB/Retain/Will | 所有能力按 MQTT 5 完成，无客户端协议告警 |
| IOP-002 | interop/P3 | Eclipse Paho MQTT 5 Client 运行同一核心矩阵 | 结果与 HiveMQ 一致，记录客户端特有差异 |
| IOP-003 | interop/P3 | `mosquitto_pub/sub -V mqttv5` 运行 TCP/TLS 基础矩阵 | CLI exit code、接收 payload 和 reason 正确 |
| IOP-004 | interop/P3 | 三种客户端交叉发布/订阅 | 属性、QoS 和 retained 行为互通，不依赖同一客户端实现 |
| NF-001 | integration/P4 | 慢消费者、Receive Maximum 小值、持续发布 | 队列/Inflight 有界，背压生效，快消费者不被拖慢 |
| NF-002 | integration/P4 | RocksDB/文件插件在阻塞检测开启时运行 | I/O EventLoop 无阻塞调用；工作在线程池且队列有界 |
| NF-003 | integration/P4 | 反复启动停止 Broker/客户端 100 次 | 无线程、端口、文件句柄和临时目录持续增长 |
| NF-004 | integration/P4 | 日志覆盖认证、协议错误、插件和集群失败 | 包含规定关联字段；不包含 password、Authentication Data、Payload/secret |
| NF-005 | integration/P4 | 指标后端采集正常和错误路径 | 连接、publish、inflight、store、plugin/cluster 指标与观察事件一致 |
| NF-006 | integration/P4 | 大量随机合法 packet sequence 做有界 property/state-machine fuzz | 无未处理异常/死锁；非法转换只产生规范允许结果 |

## 11. 需求追踪矩阵

| 设计要求 | 主要 Case |
| --- | --- |
| protocol 属性、主题、Reason Code、全部状态转换 | PR-001~PR-051 |
| Store 同一契约、原子性、恢复和迁移 | ST-001~ST-020 |
| persist-before-ack/send/event | CO-021~CO-025、CO-036、CO-043~CO-044、CL-022~CL-025、RF-001 |
| Connection/Session 分离和 generation | CO-001~CO-009、E2E-004~E2E-005 |
| QoS 1/2 重复、乱序、恢复 | PR-040~PR-049、CO-020~CO-033、E2E-006~E2E-008 |
| Receive Maximum、Packet Size、队列上限 | CO-029、CO-034、CO-070~CO-071、E2E-013、NF-001 |
| 订阅选项、Retain、共享订阅 | CO-040~CO-052、E2E-003、E2E-009、E2E-015 |
| Topic Alias | CO-060~CO-062、E2E-011 |
| Will Delay 与 Session Expiry | CO-063~CO-069、E2E-010 |
| Mailbox 串行和跨 Session 并行 | CO-080~CO-087、TR-005、CL-035 |
| 动态端口、READY 和生命周期 | TK-010~TK-012、TR-001~TR-002、BR-001~BR-008 |
| 插件顺序、超时、隔离、本地/远程一致性 | PL-001~PL-022 |
| 集群 epoch/fencing/Outbox/Peer 背压 | ST-021~ST-023、CL-001~CL-035 |
| Raft commit/apply/snapshot | RF-001~RF-009 |
| Paho/HiveMQ/Mosquitto 互操作 | IOP-001~IOP-004 |
| 无 EventLoop 阻塞和资源泄漏 | CO-086、CL-030、NF-001~NF-003 |

## 12. CI 执行建议

| 流水线 | 每次提交 | Nightly | Release |
| --- | --- | --- | --- |
| unit + testkit self-test | 是 | 是 | 是 |
| Memory Store contract + core component | 是 | 是 | 是 |
| Transport/Broker TCP integration | 是 | 是 | 是 |
| RocksDB contract + recovery | 关键分支 | 是 | 是 |
| TLS/WS/plugin remote | 关键分支 | 是 | 是 |
| Interop | 否 | 是 | 是 |
| Cluster fault/network | 集群模块关键分支 | 是 | 是 |
| Raft crash matrix/长稳/容量 | 否 | 是（分片执行） | 是 |

任何标记 `recovery`、`cluster` 或 `interop` 的 case 失败都必须保留 Broker 日志、Trace、配置、随机种子、节点状态和临时数据目录索引；敏感字段在归档前脱敏。
