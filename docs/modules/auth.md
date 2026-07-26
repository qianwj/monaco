# auth 认证模块设计

> 状态：Proposed
>
> 更新日期：2026-07-26
>
> 前置：`core` 端口定义（Authenticator）、`plugin:api` 插件接口

## 1. 目标

`auth` 是认证聚合模块，取代原 `security-default`。每个子模块按 plugin 标准实现，由 plugin:runtime 加载。支持 MQTT 5.0 基础认证和增强认证（Enhanced Authentication）。

## 2. MQTT 5.0 认证方式

### 2.1 基础认证（CONNECT Username/Password）

客户端在 CONNECT 报文中携带 Username 和 Password，Broker 校验凭证。依赖 TLS 保护传输层明文。

### 2.2 增强认证（Enhanced Authentication）

MQTT 5.0 新增，通过 AUTH 报文实现多轮质询-响应（challenge-response）。由 CONNECT 的 Authentication Method 属性触发。

支持的认证机制：

| Authentication Method | 说明 |
|---|---|
| SCRAM-SHA-256 | 盐化质询-响应，不传输明文密码 |
| SCRAM-SHA-512 | 同上，更强哈希 |
| OAUTHBEARER | OAuth 2.0 Bearer Token 验签 |

## 3. 架构分层

认证分为两个维度：

- **认证方式（Authenticator）** — 决定如何验证（协议交互逻辑）
- **凭证提供者（CredentialProvider）** — 决定从哪里加载/比对凭证

```
Authenticator ──uses──> CredentialProvider
     │                        │
  simple/scram             env/file/jdbc
```

## 4. 模块结构

```
auth/
├── build.gradle.kts          # 空壳父模块
├── credential/               # CredentialProvider 接口 + 各实现
│   ├── build.gradle.kts      # 公共接口，依赖 core
│   ├── env/                  # 从环境变量读取
│   ├── file/                 # 从 properties/passwd 文件读取
│   └── jdbc/                 # 从数据库读取（R2DBC）
├── simple/                   # Username/Password 明文比对
├── scram/                    # SCRAM-SHA-256/512 质询-响应
└── token/                    # OAuth/JWT Bearer Token 验签
```

### 4.1 settings.gradle.kts

```kotlin
"auth",
"auth:credential",
"auth:credential:env",
"auth:credential:file",
"auth:credential:jdbc",
"auth:simple",
"auth:scram",
"auth:token",
```

## 5. 核心接口

### 5.1 CredentialProvider（auth:credential）

```java
package cn.elvis.monaco.auth.credential;

import reactor.core.publisher.Mono;

public interface CredentialProvider {
    Mono<StoredCredential> lookup(String username);
}

public record StoredCredential(
    String username,
    String hashedPassword,   // 存储的哈希值
    String salt,             // SCRAM 需要
    int iterations,          // SCRAM 需要
    Map<String, String> attributes
) {}
```

### 5.2 SimpleAuthenticator（auth:simple）

```java
package cn.elvis.monaco.auth.simple;

// 实现 core 的 Authenticator 端口
// 注入 CredentialProvider，比对 password hash
public class SimpleAuthenticator implements Authenticator {
    private final CredentialProvider credentialProvider;
    private final PasswordEncoder encoder;
    // ...
}
```

### 5.3 ScramAuthenticator（auth:scram）

```java
package cn.elvis.monaco.auth.scram;

// 实现增强认证的多轮质询-响应
// 使用 AUTH 报文交换 client-first / server-first / client-final / server-final
public class ScramAuthenticator implements EnhancedAuthenticator {
    private final CredentialProvider credentialProvider;
    private final ScramMechanism mechanism; // SHA-256 or SHA-512
    // ...
}
```

### 5.4 TokenAuthenticator（auth:token）

```java
package cn.elvis.monaco.auth.token;

// 验签 JWT/OAuth Bearer Token
// 不依赖 CredentialProvider，独立验签
public class TokenAuthenticator implements EnhancedAuthenticator {
    private final TokenVerifier verifier; // JWK Set / public key
    // ...
}
```

## 6. 凭证提供者实现

| 子模块 | 凭证来源 | 适用场景 |
|---|---|---|
| credential:env | 环境变量（`MQTT_USER_xxx`） | 开发/容器单用户 |
| credential:file | properties 或 passwd 格式文件 | 小规模部署 |
| credential:jdbc | R2DBC 数据库查询 | 生产多用户 |

## 7. 依赖关系

```
auth:credential        → core（CredentialProvider 接口定义可选放此处或 core.port）
auth:credential:env    → auth:credential
auth:credential:file   → auth:credential
auth:credential:jdbc   → auth:credential, r2dbc-spi
auth:simple            → core, auth:credential, plugin:api
auth:scram             → core, auth:credential, plugin:api
auth:token             → core, plugin:api, jose4j/nimbus-jose
```

## 8. 与 plugin 系统的关系

每个 Authenticator 实现是一个标准插件：
- 实现 plugin:api 定义的认证 Hook 接口
- 由 plugin:runtime 的 PluginLoader 发现和加载
- broker 通过配置选择加载哪个认证插件

配置示例：

```yaml
auth:
  method: simple          # simple | scram-sha-256 | scram-sha-512 | token
  credential:
    provider: file        # env | file | jdbc
    file:
      path: /etc/monaco/passwd
```

## 9. 迁移计划

1. 删除 `security-default` 模块
2. 创建 `auth/` 模块骨架
3. P0 阶段实现 `auth:simple` + `credential:env`（替代 AnonymousAuthenticator）
4. P3 阶段实现 `auth:scram` 和 `auth:token`
5. 按需增加 `credential:file` 和 `credential:jdbc`
