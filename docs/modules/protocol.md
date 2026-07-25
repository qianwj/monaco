# protocol 模块实现任务

> 模块：protocol
> 基础包：cn.elvis.monaco.protocol
> 依赖：仅 Java 标准库（零外部依赖）
> 关联设计：[模块详细设计 §2.1](../mqtt5-module-design.md#21-protocol-模块)

## 概述

protocol 是纯 Java 模块，负责表达 MQTT 5.0 语义。不负责 socket 编解码，不依赖任何框架。所有类型不可变，使用 Java record 和 sealed interface。

---

## 任务清单

### P0-2: 值对象与报文模型

#### P0-2-1: 基础值对象（model/）

| 类 | 说明 | 状态 |
|---|---|---|
| `QoS` | enum：AT_MOST_ONCE(0)、AT_LEAST_ONCE(1)、EXACTLY_ONCE(2)，含 min() | ✅ 完成 |
| `ClientId` | record，校验 null、UTF-8 长度 0-65535 字节 | ✅ 完成 |
| `ConnectionId` | record，UUID 标识物理连接，含 generate() | ✅ 完成 |
| `PacketId` | record，范围 1-65535 | ✅ 完成 |
| `TopicName` | record，不可变，含 levels() 拆分 | ✅ 完成 |
| `TopicFilter` | record，支持通配符和共享订阅解析（isWildcard/isShared/shareGroup/actualFilter/levels） | ✅ 完成 |
| `ShareGroup` | record，校验非空、无通配符、无 `/` | ✅ 完成 |
| `Payload` | record wrapping byte[]，含 FormatIndicator enum | ✅ 完成 |

#### P0-2-2: Reason Code（reason/）

| 类 | 说明 | 状态 |
|---|---|---|
| `ReasonCode` | enum，所有 MQTT 5.0 Reason Code（0x00-0xA2），含 isError()/isSuccess() | ✅ 完成 |

#### P0-2-3: 报文类型（packet/）

| 类 | 说明 | 状态 |
|---|---|---|
| `PacketType` | enum：CONNECT(1) ~ AUTH(15) | ✅ 完成 |
| `ClientPacket` | sealed interface，所有客户端发送的报文 | ✅ 完成 |
| `ServerPacket` | sealed interface，所有服务端发送的报文 | ✅ 完成 |
| `ConnectPacket` | record：clientId, cleanStart, keepAlive, will, username, password, properties | ✅ 完成 |
| `ConnAckPacket` | record：sessionPresent, reasonCode, properties | ✅ 完成 |
| `PublishPacket` | record：topicName, qos, retain, dup, packetId, payload, properties | ✅ 完成 |
| `PubAckPacket` | record：packetId, reasonCode, properties | ✅ 完成 |
| `PubRecPacket` | record：packetId, reasonCode, properties | ✅ 完成 |
| `PubRelPacket` | record：packetId, reasonCode, properties | ✅ 完成 |
| `PubCompPacket` | record：packetId, reasonCode, properties | ✅ 完成 |
| `SubscribePacket` | record：packetId, subscriptions[], properties | ✅ 完成 |
| `SubAckPacket` | record：packetId, reasonCodes[], properties | ✅ 完成 |
| `UnsubscribePacket` | record：packetId, topicFilters[], properties | ✅ 完成 |
| `UnsubAckPacket` | record：packetId, reasonCodes[], properties | ✅ 完成 |
| `PingReqPacket` | record（无字段） | ✅ 完成 |
| `PingRespPacket` | record（无字段） | ✅ 完成 |
| `DisconnectPacket` | record：reasonCode, properties | ✅ 完成 |
| `AuthPacket` | record：reasonCode, authMethod, authData, properties | ✅ 完成 |
| `Subscription` | record：topicFilter, qos, noLocal, retainAsPublished, retainHandling | ✅ 完成 |
| `WillMessage` | record：topic, payload, qos, retain, properties | ✅ 完成 |

#### P0-2-4: 错误（error/）

| 类 | 说明 | 状态 |
|---|---|---|
| `ProtocolViolation` | record：reasonCode, responseType, closeConnection, message，含工厂方法 | ✅ 完成 |

#### P0-2-5: 属性系统（property/）

| 类 | 说明 | 状态 |
|---|---|---|
| `UserProperty` | record：key, value | ✅ 完成 |
| `ConnectProperties` | record，CONNECT 报文属性 | ✅ 完成 |
| `ConnAckProperties` | record，CONNACK 报文属性 | ✅ 完成 |
| `PublishProperties` | record，PUBLISH 报文属性 | ✅ 完成 |
| `WillProperties` | record，遗嘱属性 | ✅ 完成 |
| `SubscribeProperties` | record，SUBSCRIBE 报文属性 | ✅ 完成 |
| `DisconnectProperties` | record，DISCONNECT 报文属性 | ✅ 完成 |
| `AckProperties` | record，通用 ACK 属性（PUBACK/PUBREC/PUBREL/PUBCOMP/UNSUBACK） | ✅ 完成 |
| `AuthProperties` | record，AUTH 报文属性 | ✅ 完成 |

---

### P0-3: 校验与主题匹配

#### P0-3-1: 校验（validation/）

| 类 | 说明 | 状态 |
|---|---|---|
| `TopicNameValidator` | Topic Name 校验：非空、无 U+0000、禁止 +/#、长度 1-65535 字节 | ✅ 完成 |
| `TopicFilterValidator` | Topic Filter 校验：通配符位置规则、共享订阅格式、无 U+0000 | ✅ 完成 |

#### P0-3-2: 主题匹配（topic/）

| 类 | 说明 | 状态 |
|---|---|---|
| `TopicLevels` | 主题分层工具，按 `/` 拆分，处理边界（空层级、首尾 `/`） | ✅ 完成 |
| `TopicMatcher` | 主题匹配：+ 匹配单层、# 匹配零或多层、$ 前缀主题不被通配符匹配 | ✅ 完成 |

#### P0-3-3: 单元测试

| 测试类 | 覆盖范围 | 状态 |
|---|---|---|
| `QoSTest` | valueOf、min、value | ✅ 完成 |
| `ReasonCodeTest` | isError、isSuccess、code 值 | ✅ 完成 |
| `TopicNameValidatorTest` | 合法/非法 Topic Name 全场景 | ✅ 完成 |
| `TopicFilterValidatorTest` | 通配符规则、共享订阅、边界用例 | ✅ 完成 |
| `TopicMatcherTest` | +/#/精确匹配、$SYS、共享订阅、多层 | ✅ 完成 |
| `ValueObjectTest` | ClientId/PacketId/TopicFilter/ShareGroup 校验 | ✅ 完成 |

---

## 验收标准

1. `./gradlew :protocol:build` 编译通过
2. `./gradlew :protocol:test` 所有单元测试通过
3. protocol 模块不出现任何外部依赖 import（无 Vert.x、Netty、Reactor）
4. 所有报文和值对象为不可变 record
5. TopicNameValidator 和 TopicFilterValidator 覆盖 MQTT 5.0 规范要求的全部规则
6. TopicMatcher 正确处理 +、#、$SYS 前缀和共享订阅
