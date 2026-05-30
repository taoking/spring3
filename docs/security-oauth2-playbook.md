# OAuth2 / JWT 生产化专题

## 定位

当前项目已经有 `jwt` profile，支持 Spring Security OAuth2 Resource Server、本地 HS256 测试 token、`roles` 和 `scope` 权限映射。该实现适合学习和本地自动化测试。

生产系统通常不会把共享 HS256 secret 写在应用配置里，而是接入授权服务器，通过 `issuer-uri` 或 `jwk-set-uri` 验证签名，并使用 `client_credentials`、mTLS 或服务网格处理服务间身份。

本专题补齐生产化 OAuth2/JWT 面试要点，同时保留当前默认 Basic Auth 和本地 JWT 学习路径。

## 当前实现

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| 默认认证 | Basic Auth | 默认 profile 不变 |
| JWT profile | `SPRING_PROFILES_ACTIVE=jwt` | 启用 Resource Server |
| 本地 token | HS256 secret | 只用于学习和测试 |
| roles 映射 | `roles` -> `ROLE_*` | 支持 `@PreAuthorize("hasRole('ADMIN')")` |
| scope 映射 | `scope` -> `SCOPE_*` | 使用 Spring 默认 scope 映射 |
| 内部服务调用 | 仍保留 Basic | 明确是学习取舍 |
| 生产 IdP 配置 | `issuer-uri` / `jwk-set-uri` | 已预留配置入口 |

配置优先级：

1. `demo.security.jwt.jwk-set-uri`
2. `demo.security.jwt.issuer-uri`
3. `demo.security.jwt.secret`

如果配置 `jwk-set-uri`，应用直接使用远程 JWK Set 验签。如果配置 `issuer-uri`，Resource Server 会通过 issuer 的 discovery metadata 查找 JWK Set，并校验 issuer。

## 配置方式

### 本地 HS256

```bash
SPRING_PROFILES_ACTIVE=jwt \
DEMO_SECURITY_JWT_SECRET=spring3-local-dev-secret-key-32-bytes-minimum \
./mvnw -pl order-service spring-boot:run
```

### 生产 issuer-uri

```bash
SPRING_PROFILES_ACTIVE=jwt \
DEMO_SECURITY_JWT_ISSUER_URI=https://idp.example.com/realms/spring3 \
./mvnw -pl order-service spring-boot:run
```

### 生产 jwk-set-uri

```bash
SPRING_PROFILES_ACTIVE=jwt \
DEMO_SECURITY_JWT_JWK_SET_URI=https://idp.example.com/realms/spring3/protocol/openid-connect/certs \
./mvnw -pl order-service spring-boot:run
```

不要同时在生产环境依赖本地 HS256 secret。真实环境优先使用授权服务器发布的非对称密钥和 JWK rotation。

## JWT、Opaque Token、Session 对比

| 方案 | 优势 | 风险 | 适用 |
| --- | --- | --- | --- |
| JWT | 自包含、无需每次查授权服务器、跨服务传播方便 | 吊销困难、payload 变大、过期前权限变更不即时 | 微服务 Resource Server |
| Opaque Token | 服务端可控、易吊销、权限实时 | 每次 introspection 增加远程调用或缓存复杂度 | 高安全、强吊销要求 |
| Session | 服务端状态明确、传统 Web 友好 | 分布式会话、CSRF、防粘连复杂 | 浏览器应用、后台系统 |

## role 与 scope

| 概念 | 含义 | 项目映射 |
| --- | --- | --- |
| role | 用户或主体的角色，例如 ADMIN | `roles` claim -> `ROLE_ADMIN` |
| scope | token 被授权访问的能力范围，例如 `orders:read` | `scope` claim -> `SCOPE_orders:read` |
| authority | Spring Security 内部统一权限表达 | `ROLE_*` 和 `SCOPE_*` 都是 authority |

建议：

- 用户管理和后台接口常用 role。
- API 访问能力和第三方客户端常用 scope。
- 服务间 token 优先用 scope 限制能力，不要直接给 ADMIN。

## 服务间调用

当前项目在 JWT profile 下仍允许 Basic Auth，`order-service -> catalog-service` 继续使用已有 Basic 凭证。这是为了保留服务间调用学习路径，不是生产推荐。

生产候选方案：

| 方案 | 做法 | 适用 |
| --- | --- | --- |
| `client_credentials` | order-service 用 client id/secret 换 service token 调 catalog | OAuth2 标准微服务间调用 |
| Token relay | 网关或 BFF 透传用户 token | 需要下游基于用户身份授权 |
| Token exchange | 用用户 token 换下游 audience token | 多服务零信任、权限最小化 |
| mTLS | 服务证书认证服务身份 | 服务网格或强内网身份 |
| Service mesh auth | Istio/Linkerd 等统一身份和策略 | 平台化微服务环境 |

### client_credentials 时序

```text
order-service
  |
  | POST /oauth2/token grant_type=client_credentials
  v
Authorization Server
  |
  | access_token audience=catalog-service scope=catalog:read
  v
order-service
  |
  | GET catalog-service with Bearer service token
  v
catalog-service Resource Server
```

设计要点：

- service token 要有独立 audience 和 scope。
- token 缓存到过期前，避免每个请求都打授权服务器。
- 授权服务器不可用时要有短时缓存和降级策略。
- 不要把用户 token 随意传给所有下游。

## 网关与服务侧职责

| 职责 | Gateway | Service |
| --- | --- | --- |
| TLS 终止 | 常见 | 通常内网 |
| 粗粒度认证 | 可以做 | 仍应验证关键请求 |
| 路由和限流 | 主要职责 | 局部保护 |
| 权限细粒度判断 | 可做粗粒度 | 业务 owner，必须保留 |
| Token relay | 可以 | 接收并验证 |
| 审计 | 入口审计 | 业务审计 |

面试回答：网关鉴权不能替代服务侧授权。服务应保留最小权限保护，特别是管理接口、跨服务调用和可绕过网关的内网路径。

## token 吊销

JWT 自包含，签发后在过期前通常不查服务端状态，因此吊销是难点。

常见方案：

| 方案 | 说明 | 取舍 |
| --- | --- | --- |
| 短 access token + refresh token | access token 很短，降低风险窗口 | refresh token 管理复杂 |
| 黑名单 | 记录被吊销的 jti | 需要共享存储，和无状态 JWT 冲突 |
| token version | 用户权限变更后提升版本 | Resource Server 需要查版本或缓存 |
| Opaque token | 每次 introspection | 延迟和可用性成本 |
| 密钥轮换 | 失效一批 token | 粒度粗，影响面大 |

实际工程里常组合：短 access token、refresh token、关键操作二次校验、风险用户黑名单。

## JWK rotation

JWK Set 支持多个 key，JWT header 中的 `kid` 指定使用哪个 key。

轮换流程：

1. 授权服务器发布新公钥到 JWK Set。
2. 新 token 使用新 `kid` 签名。
3. 旧公钥保留到所有旧 token 过期。
4. 旧 token 全部过期后移除旧公钥。

Resource Server 要能缓存并刷新 JWK Set。生产要监控 JWK 拉取失败、`kid` 不匹配和 issuer 校验失败。

## 常见失败场景

| 场景 | 表现 | 处理 |
| --- | --- | --- |
| token 过期 | `401` | 客户端刷新 token |
| 签名不匹配 | `401` | 检查 JWK/secret、kid、算法 |
| issuer 不匹配 | `401` | 检查 `issuer-uri` 和 token `iss` |
| audience 不匹配 | 可能被错误接受 | 生产应增加 audience validator |
| scope 不足 | `403` | 返回权限不足，避免误报认证失败 |
| JWK Set 不可用 | 启动或验签失败 | 缓存、重试、监控 IdP |
| 网关透传丢 header | 下游 `401` | 检查 Gateway filter 和敏感头策略 |
| 时钟偏移 | 误判 `nbf`/`exp` | NTP、合理 clock skew |

## 面试追问与回答要点

| 追问 | 回答要点 |
| --- | --- |
| OAuth2 和 JWT 是什么关系？ | OAuth2 是授权框架，JWT 是 token 格式之一；OAuth2 不要求必须用 JWT |
| Resource Server 做什么？ | 校验 token、解析 authority、保护资源接口 |
| `issuer-uri` 和 `jwk-set-uri` 区别？ | issuer 可通过 discovery 获取元数据并校验 issuer；jwk-set-uri 直接指向公钥集合 |
| JWT 如何吊销？ | 短 token、refresh token、黑名单、token version、opaque token；JWT 天然吊销不便 |
| role 和 scope 区别？ | role 表示主体角色，scope 表示 token 授权范围；Spring 都转成 authority |
| 网关鉴权后服务还要鉴权吗？ | 要。服务保留最小权限，防止绕过网关或网关策略配置错误 |
| 服务间调用用用户 token 还是 client token？ | 看是否需要用户上下文；后台服务能力优先 client_credentials，跨用户授权要 token relay/exchange |
| JWK rotation 怎么做？ | 新旧 key 并存，kid 匹配，旧 token 过期后移除旧 key |
| JWT payload 能放敏感信息吗？ | 不能。JWT 默认只是签名不是加密，payload 可被解码 |
| 为什么生产不建议 HS256 共享密钥？ | 所有 Resource Server 都有签名能力，一旦泄漏影响大；非对称密钥更适合分发公钥验签 |
| `401` 和 `403` 怎么区分？ | 未认证或 token 无效是 401；已认证但权限不足是 403 |
| token 缓存怎么做？ | client_credentials token 可缓存到过期前；opaque introspection 结果可短缓存但要考虑吊销延迟 |
| audience 为什么重要？ | 限定 token 适用的资源服务，防止一个服务 token 被拿去调另一个服务 |

## 自检清单

- 能说明当前项目 JWT profile 是本地学习模式。
- 能说明 `issuer-uri`、`jwk-set-uri`、HS256 secret 的使用顺序。
- 能说明 Basic 内部调用为什么只是学习取舍。
- 能设计 `client_credentials` 服务间调用。
- 能解释 role、scope、authority 的关系。
- 能解释 JWT 吊销、JWK rotation 和 audience 校验。
- 能区分认证失败 `401` 和授权失败 `403`。
