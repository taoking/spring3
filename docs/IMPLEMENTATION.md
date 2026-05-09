# Spring Boot 3 学习项目实施文档

## 目标

这个仓库用于 Spring Boot 3 长期学习、复盘和面试准备。当前版本不接入数据库和 Redis，把精力放在 Web、配置、注解、服务调用、监控、错误上报和测试。

## 验收标准

- `./mvnw test` 全部通过。
- `catalog-service` 可以独立启动，`/actuator/health` 返回 `UP`。
- `order-service` 可以独立启动，并通过 OpenFeign 调用 `catalog-service`。
- `gateway-service` 可以独立启动，并能把 `/catalog/**`、`/orders/**` 路由到对应服务。
- Swagger UI 可以访问并展示业务接口。
- 未登录访问业务接口返回 `401`，普通用户访问 admin 接口返回 `403`。
- Prometheus 可以抓取三个服务的 `/actuator/prometheus`。
- Grafana 可以看到 JVM、HTTP 请求和自定义业务指标。
- Zipkin 可以查询一次 `gateway-service -> order-service -> catalog-service` 请求的 trace。
- `order-service` 和 `catalog-service` 日志可以看到同一个 traceId。
- 设置 `SENTRY_DSN` 后，调用异常触发接口能在 Sentry 看到事件。
- Nacos 作为可选专题补充，不影响默认 profile 的启动和测试。
- 项目没有数据库、Redis、Kafka、RabbitMQ、RocketMQ 运行依赖。

## 当前实现

### 工程结构

- `common`：共享 DTO、自定义 AOP 注解。
- `catalog-service`：商品 provider，端口 `8081`。
- `order-service`：订单 consumer，端口 `8080`。
- `gateway-service`：Spring Cloud Gateway 统一入口，端口 `8088`。

### 组件覆盖

- Web MVC：`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@RequestParam`、`@PathVariable`、`@RequestBody`。
- 参数校验：Jakarta Validation、`@Valid`、`@Validated`、`@NotBlank`、`@Positive`。
- 配置绑定：`@ConfigurationProperties`、YAML 配置。
- 错误处理：`@RestControllerAdvice`、`@ExceptionHandler`、Spring Boot 3 `ProblemDetail`。
- 安全：Spring Security Basic、`@PreAuthorize`、公开 health/prometheus/swagger。
- 服务调用：OpenFeign、服务间 Basic Auth、超时配置。
- 韧性：Spring Cloud CircuitBreaker + Resilience4j，Feign fallback。
- 网关：Spring Cloud Gateway WebFlux、静态路由、Nacos 服务发现路由、全局过滤器、认证头透传、本地限流、CircuitBreaker fallback。
- 缓存：Spring Cache + Caffeine。
- AOP：自定义 `@DemoLog` 和耗时日志切面。
- 异步与事件：`@Async`、Spring Event、`@EventListener`。
- 定时任务：`@Scheduled` 心跳任务。
- 观测：Actuator、Micrometer、Prometheus registry、自定义 Counter、Micrometer Tracing、Zipkin。
- Trace 传播：Web MVC/WebFlux 入口自动生成或接收 W3C trace context，`order-service` 的 Feign 配置把当前 trace context 注入出站请求。
- 错误上报：Sentry Jakarta starter，DSN 通过环境变量读取。
- API 文档：SpringDoc OpenAPI / Swagger UI。
- Nacos 可选专题：通过 `-Pnacos` Maven profile 和 `SPRING_PROFILES_ACTIVE=nacos` 启用服务注册发现、配置中心和 Feign 服务名调用。

## 运行方式

```bash
./mvnw test
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl gateway-service spring-boot:run
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

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview
```

## Sentry 验证

```bash
export SENTRY_DSN='你的 Sentry DSN'
./mvnw -pl order-service spring-boot:run
curl -u admin:admin123 -X POST http://localhost:8080/api/orders/admin/sentry-error
```

没有设置 `SENTRY_DSN` 时，SDK 不会上报真实事件，应用仍然可以正常启动。

## Prometheus + Grafana + Zipkin 验证

先启动三个 Spring Boot 服务，再启动观测栈：

```bash
docker compose -f observability/docker-compose.yml up
```

访问：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Zipkin: `http://localhost:9411/zipkin`

Prometheus 使用 `host.docker.internal` 抓取宿主机上的三个 Spring Boot 服务。Zipkin 通过 `http://localhost:9411/api/v2/spans` 接收三个服务上报的 span。

链路追踪验证：

```bash
TRACE_ID=4bf92f3577b34da6a3ce929d0e0e4736

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H "traceparent: 00-${TRACE_ID}-00f067aa0ba902b7-01" \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview

sleep 5
curl -fsS "http://localhost:9411/api/v2/trace/${TRACE_ID}"
rg "$TRACE_ID" gateway-service/target/run.log order-service/target/run.log catalog-service/target/run.log
```

## Nacos 可选专题

Nacos 属于面试和微服务实践高频内容，已补充为可选专题：

- 本地 Docker Compose：`platform/nacos/docker-compose.yml`
- 专题手册：`docs/nacos-playbook.md`
- 面试路线：`docs/interview-roadmap.md`
- 可选依赖：`-Pnacos`
- Spring profile：`SPRING_PROFILES_ACTIVE=nacos`
- Gateway 路由：`gateway-service` 在 `nacos` profile 下使用 `lb://catalog-service` 和 `lb://order-service`

当前不把 Nacos 加入默认运行依赖，避免学习项目启动门槛变高。只有同时使用 Maven `nacos` profile 和 Spring `nacos` profile 时才会启用 Nacos。

本地 Nacos 配置校验：

```bash
docker compose -f platform/nacos/docker-compose.yml config
```

## 测试策略

- `catalog-service` 覆盖 health/prometheus 公开访问、业务认证、商品查询、404 ProblemDetail、admin 权限。
- `order-service` 覆盖 health 公开访问、业务认证、参数校验、Feign 正常调用、Feign 失败降级、admin 权限、Prometheus endpoint。
- `order-service` 增加 W3C `traceparent` 传播测试，验证 Feign 出站请求携带同一个 traceId。
- `gateway-service` 覆盖路由匹配、前缀改写、`Authorization` 透传、`X-Request-Id`、下游 `401` 透出、fallback、本地限流、health/prometheus。
- Feign 测试使用 MockWebServer，不依赖公网和手动启动 provider。

## 明确不做

- 不接入 MySQL、PostgreSQL、JPA、MyBatis、Flyway、Liquibase。
- 不接入 Redis 或 Spring Session Redis。
- 不实现 Kafka、RabbitMQ、RocketMQ 代码。
- 不把 Nacos 作为默认 profile 的必需依赖。
- 不把 Zipkin 作为业务服务启动的硬依赖。
- 不提交真实 Sentry DSN。
- 不做前端页面，只使用 REST API、Swagger UI、Prometheus、Grafana 和 Zipkin。
