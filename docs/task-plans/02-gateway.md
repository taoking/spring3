# 02 Spring Cloud Gateway 计划

## 目标

新增 `gateway-service`，把系统入口从直接访问服务升级为网关统一入口，演示路由、过滤器、认证透传、限流和 fallback。

## 任务 Prompt

```text
为当前项目新增 Spring Cloud Gateway 专题。请先阅读 README.md、docs/USAGE.md、docs/interview-roadmap.md 和 docs/task-plans/02-gateway.md。

要求：
1. 新增 gateway-service Maven 模块，默认端口建议 8088。
2. 配置到 order-service 和 catalog-service 的路由。
3. 增加全局过滤器，记录 requestId、耗时、目标 routeId。
4. 增加鉴权透传示例，确保 Basic/JWT 后续可平滑扩展。
5. 增加限流或 fallback 示例，优先使用项目已有 Resilience4j 思路。
6. 如果 Nacos 已实现，则支持通过服务发现路由；如果未实现，则先用 localhost 静态路由。
7. 更新 docs/USAGE.md，补充网关启动、路由验证和排障命令。
8. 增加测试覆盖路由匹配、未授权、fallback 或过滤器行为。
```

## 示例内容

- `GET /catalog/api/catalog/products/SKU-1001` 路由到 `catalog-service`。
- `POST /orders/api/orders/preview` 路由到 `order-service`。
- 全局响应头增加 `X-Request-Id`。
- 网关层记录 `routeId`、`status`、`elapsedMs`。

## 实施要点

- Gateway 使用 WebFlux 栈，不要和 Spring MVC 依赖混用到同一个模块。
- 网关职责是入口治理，不承载业务逻辑。
- 鉴权策略需要明确：网关只做统一入口，服务侧仍保留最小权限保护。
- 限流配置要选择低风险示例，避免本地学习时频繁误触发。

## 验收标准

- `./mvnw test` 通过。
- `./mvnw -pl gateway-service spring-boot:run` 可启动。
- 通过网关能访问 order/catalog 的业务接口。
- 未携带认证信息访问受保护接口返回 `401` 或预期错误。
- 停止下游服务后，网关返回明确 fallback 或 5xx 错误响应。
- Actuator health 和 Prometheus endpoint 可访问。

## 不做

- 不实现复杂前端门户。
- 不把业务校验放到网关。
- 不引入生产级动态路由管理平台。
