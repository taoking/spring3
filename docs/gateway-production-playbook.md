# Gateway 生产能力专题

## 定位

当前 `gateway-service` 已覆盖静态路由、Nacos 服务发现路由、请求审计、认证头透传、本地限流和 fallback。生产面试会继续追问 CORS、灰度路由、动态路由、分布式限流、网关鉴权与服务侧授权边界。

本专题补齐这些生产化能力，并在现有 Gateway 中加入两个轻量示例：

- CORS 预检配置。
- 基于 `X-Canary: true` 的订单灰度路由。

## 当前实现

| 能力 | 当前实现 |
| --- | --- |
| 普通 catalog 路由 | `/catalog/**` -> `demo.gateway.routes.catalog-uri` |
| 普通 order 路由 | `/orders/**` -> `demo.gateway.routes.order-uri` |
| order 灰度路由 | `X-Canary: true` -> `demo.gateway.routes.order-canary-uri` |
| CORS | `spring.cloud.gateway.server.webflux.globalcors.*` |
| 请求审计 | `RequestAuditGlobalFilter` 生成/透传 `X-Request-Id` |
| 认证透传 | `AuthenticationRelayGlobalFilter` 增加 `X-Gateway-Auth-Type` |
| 本地限流 | `LocalRateLimitGlobalFilter` 单实例窗口计数 |
| fallback | Spring Cloud Gateway CircuitBreaker -> `/fallback/{service}` |

## 配置入口

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          globalcors:
            add-to-simple-url-handler-mapping: true
            cors-configurations:
              '[/**]':
                allowed-origins:
                  - http://localhost:3000
                  - http://localhost:5173
                allowed-methods:
                  - GET
                  - POST
                  - PUT
                  - DELETE
                  - OPTIONS
                allowed-headers:
                  - "*"
                allow-credentials: true
                max-age: 3600

demo:
  gateway:
    routes:
      catalog-uri: http://localhost:8081
      order-uri: http://localhost:8080
      order-canary-uri: ${DEMO_GATEWAY_ROUTES_ORDER_CANARY_URI:http://localhost:8080}
```

灰度路由本地验证：

```bash
curl -H 'X-Canary: true' http://localhost:8088/orders/api/orders/admin/stats
```

## Gateway 职责边界

| 能力 | Gateway 适合做 | 服务侧必须保留 |
| --- | --- | --- |
| 认证 | 入口 token 校验、失败快速拒绝 | 关键接口仍校验 token 或服务身份 |
| 授权 | 粗粒度路由权限 | 业务细粒度权限、数据权限 |
| 限流 | 入口全局流量治理 | 业务资源局部保护 |
| CORS | 统一跨域策略 | 管理后台等特殊接口可更严格 |
| 灰度 | Header/权重/用户分组路由 | 兼容新旧版本协议 |
| fallback | 下游不可用时统一错误 | 业务语义 fallback |
| 审计 | 入口 requestId/traceId | 业务操作审计 |

面试要点：Gateway 是入口治理层，不是业务权限和数据一致性的唯一防线。服务侧必须保留最小授权，防止绕过网关、内网误调用或网关策略配置错误。

## CORS 生产要点

- 不要使用 `*` 搭配 credentials。
- 当前示例只对 allowed headers 使用 `*`，allowed origins 仍显式列出。
- 明确允许的 origin，区分本地、测试、生产域名。
- 预检请求 `OPTIONS` 不应打到下游业务服务。
- 暴露 header 要谨慎，认证 token 不应通过响应 header 泄漏。
- CORS 是浏览器安全策略，不是服务端访问控制。

## 灰度路由

常见维度：

| 方式 | 示例 | 适用 |
| --- | --- | --- |
| Header | `X-Canary: true` | 测试人员、灰度脚本 |
| Cookie | `canary=true` | 浏览器用户 |
| 用户 ID hash | `userId % 100 < 5` | 百分比灰度 |
| 权重 | 95% stable / 5% canary | 流量切分 |
| Region/Tenant | 指定地区或租户 | 企业客户灰度 |

灰度发布要求：

- 新旧版本 API 兼容。
- 可观测性按版本拆分。
- 出问题能快速切回 stable。
- 灰度规则变更要有审计。

## 分布式限流设计

当前 `LocalRateLimitGlobalFilter` 是单实例内存限流，适合学习和本地验证。多实例生产需要共享状态：

```text
gateway instance A \
gateway instance B  -> Redis / rate limit service -> allow/reject
gateway instance C /
```

关键设计：

- key：IP、用户、租户、clientId、routeId、method。
- 算法：令牌桶、漏桶、滑动窗口、固定窗口。
- 原子性：Redis Lua 或独立限流服务。
- 故障策略：fail-open 保护可用性，fail-closed 保护核心资源。
- 观测：限流命中数、routeId、key 类型、剩余额度。

## 动态路由边界

动态路由可以来自 Nacos、配置中心、数据库或网关管理平台。

生产风险：

- 错误路由配置可能放大故障。
- 动态规则需要审批、回滚和审计。
- 路由刷新必须考虑连接池、DNS、缓存和已有请求。
- 路由规则过多会增加排障复杂度。

当前项目不引入生产级动态路由平台，只保留 Nacos profile 服务发现路由和文档化设计。

## 故障矩阵

| 场景 | 表现 | 处理 |
| --- | --- | --- |
| CORS 配错 | 浏览器报跨域，服务端可能无异常 | 检查 Origin、预检、允许方法和 header |
| 灰度后 5xx 增加 | canary route 错误率升高 | 立即切回 stable，保留 trace/log 排查 |
| 网关限流过严 | 429 增多 | 按 routeId/key 类型看命中，调整阈值 |
| 下游不可用 | Gateway fallback 503 | 看 circuit breaker、下游 health、trace |
| 认证 header 丢失 | 下游 401 | 检查 filter 顺序和敏感头过滤 |
| 网关绕过 | 服务直接被访问 | 服务侧保留认证授权，网络策略限制入口 |

## 面试追问与回答要点

| 追问 | 回答要点 |
| --- | --- |
| 鉴权放网关还是服务？ | 网关做入口统一认证和粗粒度授权，服务保留业务细粒度授权 |
| CORS 是安全认证吗？ | 不是。CORS 是浏览器跨域策略，不能替代服务端鉴权 |
| 灰度怎么做？ | Header/Cookie/权重/用户 hash，配合指标、日志、快速回滚 |
| 分布式限流怎么做？ | Redis Lua 或限流服务，key 维度、算法、故障策略、指标 |
| 本地限流有什么问题？ | 多实例不共享，每个实例各算各的，总流量不可控 |
| Gateway fallback 和业务 fallback 区别？ | Gateway fallback 是入口下游不可达兜底，业务 fallback 需要理解业务语义 |
| 动态路由有什么风险？ | 配错影响全站，需要审批、灰度、回滚和审计 |
| 如何防止绕过网关？ | 网络层限制、服务侧认证授权、mTLS/service mesh |
| 网关如何传递用户身份？ | token relay、header 透传或 token exchange，不能信任可伪造 header |
| 限流应该按 IP 还是用户？ | 看业务目标，IP 防爬，用户/租户保护配额，routeId 保护接口 |

## 自检清单

- 能解释 CORS 预检和 credentials 的限制。
- 能说明 `X-Canary` 灰度路由如何命中。
- 能说明本地限流与 Redis 分布式限流的差异。
- 能区分 Gateway fallback 和业务 fallback。
- 能说明网关鉴权后服务侧为什么仍保留授权。
- 能给出灰度发布的监控和回滚条件。
