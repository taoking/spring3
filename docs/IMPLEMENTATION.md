# Spring Boot 3 学习项目实施文档

## 目标

这个仓库用于 Spring Boot 3 长期学习、复盘和面试准备。当前版本不接入数据库和 Redis，把精力放在 Web、配置、注解、服务调用、监控、错误上报和测试。

## 验收标准

- `./mvnw test` 全部通过。
- `catalog-service` 可以独立启动，`/actuator/health` 返回 `UP`。
- `order-service` 可以独立启动，并通过 OpenFeign 调用 `catalog-service`。
- Swagger UI 可以访问并展示业务接口。
- 未登录访问业务接口返回 `401`，普通用户访问 admin 接口返回 `403`。
- Prometheus 可以抓取两个服务的 `/actuator/prometheus`。
- Grafana 可以看到 JVM、HTTP 请求和自定义业务指标。
- 设置 `SENTRY_DSN` 后，调用异常触发接口能在 Sentry 看到事件。
- Nacos 作为可选专题补充，不影响默认 profile 的启动和测试。
- 项目没有数据库、Redis、Kafka、RabbitMQ、RocketMQ 运行依赖。

## 当前实现

### 工程结构

- `common`：共享 DTO、自定义 AOP 注解。
- `catalog-service`：商品 provider，端口 `8081`。
- `order-service`：订单 consumer，端口 `8080`。

### 组件覆盖

- Web MVC：`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@RequestParam`、`@PathVariable`、`@RequestBody`。
- 参数校验：Jakarta Validation、`@Valid`、`@Validated`、`@NotBlank`、`@Positive`。
- 配置绑定：`@ConfigurationProperties`、YAML 配置。
- 错误处理：`@RestControllerAdvice`、`@ExceptionHandler`、Spring Boot 3 `ProblemDetail`。
- 安全：Spring Security Basic、`@PreAuthorize`、公开 health/prometheus/swagger。
- 服务调用：OpenFeign、服务间 Basic Auth、超时配置。
- 韧性：Spring Cloud CircuitBreaker + Resilience4j，Feign fallback。
- 缓存：Spring Cache + Caffeine。
- AOP：自定义 `@DemoLog` 和耗时日志切面。
- 异步与事件：`@Async`、Spring Event、`@EventListener`。
- 定时任务：`@Scheduled` 心跳任务。
- 观测：Actuator、Micrometer、Prometheus registry、自定义 Counter。
- 错误上报：Sentry Jakarta starter，DSN 通过环境变量读取。
- API 文档：SpringDoc OpenAPI / Swagger UI。

## 运行方式

```bash
./mvnw test
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
```

默认账号：

- `user / user123`
- `admin / admin123`

示例：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

## Sentry 验证

```bash
export SENTRY_DSN='你的 Sentry DSN'
./mvnw -pl order-service spring-boot:run
curl -u admin:admin123 -X POST http://localhost:8080/api/orders/admin/sentry-error
```

没有设置 `SENTRY_DSN` 时，SDK 不会上报真实事件，应用仍然可以正常启动。

## Prometheus + Grafana 验证

先启动两个 Spring Boot 服务，再启动观测栈：

```bash
docker compose -f observability/docker-compose.yml up
```

访问：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Prometheus 使用 `host.docker.internal` 抓取宿主机上的两个 Spring Boot 服务。

## Nacos 补充计划

Nacos 属于面试和微服务实践高频内容，已补充为可选专题：

- 本地 Docker Compose：`platform/nacos/docker-compose.yml`
- 专题手册：`docs/nacos-playbook.md`
- 面试路线：`docs/interview-roadmap.md`

当前不把 Nacos 加入默认运行依赖，避免学习项目启动门槛变高。后续如果实现代码接入，应使用 `nacos` profile，默认 profile 仍然保持不需要 Nacos。

本地 Nacos 配置校验：

```bash
docker compose -f platform/nacos/docker-compose.yml config
```

## 测试策略

- `catalog-service` 覆盖 health/prometheus 公开访问、业务认证、商品查询、404 ProblemDetail、admin 权限。
- `order-service` 覆盖 health 公开访问、业务认证、参数校验、Feign 正常调用、Feign 失败降级、admin 权限、Prometheus endpoint。
- Feign 测试使用 MockWebServer，不依赖公网和手动启动 provider。

## 明确不做

- 不接入 MySQL、PostgreSQL、JPA、MyBatis、Flyway、Liquibase。
- 不接入 Redis 或 Spring Session Redis。
- 不实现 Kafka、RabbitMQ、RocketMQ 代码。
- 不把 Nacos 作为默认 profile 的必需依赖。
- 不提交真实 Sentry DSN。
- 不做前端页面，只使用 REST API、Swagger UI、Prometheus 和 Grafana。
