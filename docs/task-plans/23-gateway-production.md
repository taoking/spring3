# 23 Gateway 生产能力计划

## 目标

在当前 Spring Cloud Gateway 基线上补齐 CORS、灰度路由、分布式限流设计、动态路由边界、网关鉴权与服务侧授权职责边界，并保留轻量可测试实现。

## 任务 Prompt

```text
深化当前 Spring Cloud Gateway 专题。请先阅读：

- README.md
- docs/USAGE.md
- docs/interview-roadmap.md
- docs/task-plans/02-gateway.md
- docs/task-plans/19-interview-expansion.md
- docs/gateway-production-playbook.md
- gateway-service/src/main/java
- gateway-service/src/test/java

目标：
1. 补充 CORS 预检配置和测试。
2. 补充一个轻量灰度路由示例，例如 `X-Canary: true` 路由到 canary backend。
3. 文档说明网关鉴权与服务侧授权职责边界。
4. 文档说明本地限流和生产分布式限流的区别。
5. 文档说明动态路由风险、灰度发布监控和回滚条件。
6. 保持现有 Gateway 默认路由可用。
7. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或相关 task plan。
8. 记录实施过程到本地日志文件。

验收：
1. Gateway 测试覆盖 CORS preflight。
2. Gateway 测试覆盖 canary header 路由。
3. 文档能回答鉴权放网关还是服务、CORS 是否是认证、分布式限流怎么做、灰度怎么回滚。
4. `./mvnw -pl gateway-service test` 通过。
```

## 当前实施结果

- 新增 [Gateway 生产能力专题](../gateway-production-playbook.md)。
- 使用 Spring Cloud Gateway 内置 `spring.cloud.gateway.server.webflux.globalcors.*` 配置 CORS。
- `GatewayRouteConfig` 新增 `orders-canary-route`，当请求携带 `X-Canary: true` 时路由到 `demo.gateway.routes.order-canary-uri`。
- `GatewayRouteTest` 覆盖 CORS preflight 和 canary header 路由。

## 配置入口

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          globalcors:
            cors-configurations:
              '[/**]':
                allowed-origins:
                  - http://localhost:3000
                  - http://localhost:5173

demo:
  gateway:
    routes:
      order-canary-uri: ${DEMO_GATEWAY_ROUTES_ORDER_CANARY_URI:http://localhost:8080}
```

## 验收标准

- `/orders/**` 默认仍路由到 stable backend。
- `X-Canary: true` 路由到 canary backend。
- `OPTIONS` preflight 返回允许的 Origin、Method 和 Headers。
- 文档说明 CORS、灰度、分布式限流、动态路由和鉴权职责边界。

## 验收命令

```bash
./mvnw -pl gateway-service test
git diff --check
```

## 不做

- 不引入生产级动态路由管理平台。
- 不引入 Redis 分布式限流代码。
- 不把 Gateway 作为服务侧授权的唯一防线。
- 不接入真实灰度发布平台。
