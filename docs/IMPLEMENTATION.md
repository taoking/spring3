# Spring Boot 3 学习项目实施文档

## 目标

这个仓库用于 Spring Boot 3 长期学习、复盘和面试准备。当前版本不接入数据库和 Redis，把精力放在 Web、配置、注解、服务调用、监控、错误上报和测试。

## 验收标准

- `./mvnw test` 全部通过。
- `catalog-service` 可以独立启动，`/actuator/health` 返回 `UP`。
- `order-service` 可以独立启动，并通过 OpenFeign 调用 `catalog-service`。
- `order-service` 可以通过 `demo.clients.catalog.mode=restclient` 切换为 RestClient 调用，并保留相同认证、超时和 fallback 行为。
- `catalog-service` 和 `order-service` 可以通过 `jwt` profile 启用 OAuth2 Resource Server，Bearer token 能完成认证授权。
- `gateway-service` 可以独立启动，并能把 `/catalog/**`、`/orders/**` 路由到对应服务。
- Swagger UI 可以访问并展示业务接口。
- 未登录访问业务接口返回 `401`，普通用户访问 admin 接口返回 `403`。
- Prometheus 可以抓取三个服务的 `/actuator/prometheus`。
- Grafana 可以看到 JVM、HTTP 请求和自定义业务指标。
- Zipkin 可以查询一次 `gateway-service -> order-service -> catalog-service` 请求的 trace。
- `order-service` 和 `catalog-service` 日志可以看到同一个 traceId。
- 设置 `SENTRY_DSN` 后，调用异常触发接口能在 Sentry 看到事件。
- `integration-test` profile 可以通过 Testcontainers 验证 Gateway 到容器化下游的真实路由。
- GitHub Actions 分别执行默认单元测试和 Docker 集成测试。
- `catalog-service` 和 `order-service` 镜像可以构建，并能通过本地 Compose 在容器网络中完成服务间调用。
- 容器版 Prometheus 可以通过服务名抓取业务服务指标。
- `order-service` 可以通过 `virtual-thread` profile 启用 Java 21 虚拟线程，并保留默认 profile 的传统线程池。
- `order-service` 可以通过 `-Psentinel` + `sentinel` profile 启用 Sentinel 本地规则，演示限流、热点参数和慢调用熔断。
- Nacos 作为可选专题补充，不影响默认 profile 的启动和测试。
- 项目没有数据库、Redis、Kafka、RabbitMQ、RocketMQ 运行依赖。
- `@DemoLog` AOP 能通过自定义 starter 自动装配，并允许关闭或覆盖默认 Bean。

## 当前实现

### 工程结构

- `common`：共享 DTO、自定义 AOP 注解。
- `demo-observability-autoconfigure`：`@DemoLog` 自动配置、属性绑定、默认 reporter、切面 Bean。
- `demo-observability-spring-boot-starter`：依赖聚合模块，不包含业务代码。
- `catalog-service`：商品 provider，端口 `8081`。
- `order-service`：订单 consumer，端口 `8080`。
- `gateway-service`：Spring Cloud Gateway 统一入口，端口 `8088`。

### 组件覆盖

- Web MVC：`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@RequestParam`、`@PathVariable`、`@RequestBody`。
- 参数校验：Jakarta Validation、`@Valid`、`@Validated`、`@NotBlank`、`@Positive`。
- 配置绑定：`@ConfigurationProperties`、YAML 配置。
- 错误处理：`@RestControllerAdvice`、`@ExceptionHandler`、Spring Boot 3 `ProblemDetail`。
- 安全：Spring Security Basic、`@PreAuthorize`、公开 health/prometheus/swagger。
- JWT：`jwt` profile 下启用 OAuth2 Resource Server，HS256 本地开发密钥验证 Bearer token，`roles` claim 映射到 `ROLE_*`，`scope` claim 映射到 `SCOPE_*`。
- 服务调用：OpenFeign、RestClient、服务间 Basic Auth、超时配置、调用模式配置切换。
- 韧性：Spring Cloud CircuitBreaker + Resilience4j，Feign fallback，RestClient 统一 fallback 支撑。
- 网关：Spring Cloud Gateway WebFlux、静态路由、Nacos 服务发现路由、全局过滤器、认证头透传、本地限流、CircuitBreaker fallback。
- 缓存：Spring Cache + Caffeine。
- AOP：自定义 `@DemoLog`，耗时日志切面由自定义 starter 自动装配。
- 异步与事件：`@Async`、Spring Event、`@EventListener`。
- 定时任务：`@Scheduled` 心跳任务。
- 观测：Actuator、Micrometer、Prometheus registry、自定义 Counter、Micrometer Tracing、Zipkin。
- Trace 传播：Web MVC/WebFlux 入口自动生成或接收 W3C trace context，`order-service` 的 Feign 配置把当前 trace context 注入出站请求。
- 错误上报：Sentry Jakarta starter，DSN 通过环境变量读取。
- API 文档：SpringDoc OpenAPI / Swagger UI。
- 集成测试：`integration-test` Maven profile 使用 Failsafe 运行 `**/*IT.java`，当前通过 Testcontainers 启动固定版本 Nginx 容器验证 Gateway 真实下游。
- CI：GitHub Actions 使用 JDK 21 和 Maven cache，默认 job 运行 `./mvnw -B test`，Docker job 运行 `./mvnw -B -Pintegration-test verify`。
- 容器化：`catalog-service/Dockerfile` 和 `order-service/Dockerfile` 使用 JDK 21 JRE Alpine 镜像、非 root 用户和 `JAVA_OPTS`；`deployment/docker-compose.yml` 启动两个业务服务、Prometheus、Grafana、Zipkin。
- 容器网络：`order-service` 在 Compose 中通过 `DEMO_CLIENTS_CATALOG_BASE_URL=http://catalog-service:8081` 调用 `catalog-service`，Prometheus 通过 `catalog-service:8081` 和 `order-service:8080` 抓取指标。
- 虚拟线程：`order-service` 的 `virtual-thread` profile 设置 `spring.threads.virtual.enabled=true`，并把 `demoTaskExecutor` 从默认 `ThreadPoolTaskExecutor` 切换为虚拟线程 `SimpleAsyncTaskExecutor`。
- 线程观察：`/api/orders/thread-probe` 支持请求线程和 `@Async` 线程两种模式，响应返回线程名、是否虚拟线程和模拟 I/O 等待时长。
- Nacos 可选专题：通过 `-Pnacos` Maven profile 和 `SPRING_PROFILES_ACTIVE=nacos` 启用服务注册发现、配置中心和 Feign 服务名调用。
- Sentinel 可选专题：通过 `-Psentinel` Maven profile 编译隔离源码，通过 `SPRING_PROFILES_ACTIVE=sentinel` 加载本地 Flow、ParamFlow、Degrade 规则。

### 自定义 starter / autoconfigure

`@DemoLog` 能力已从 `catalog-service` 和 `order-service` 的重复 `LoggingAspect` 迁移到演示型 starter：

- `demo-observability-autoconfigure`：提供 `DemoLogAutoConfiguration`、`DemoLogAspect`、`DemoLogReporter`、`DemoLogProperties`。
- `demo-observability-spring-boot-starter`：聚合 autoconfigure 和 `spring-boot-starter-aop`，业务服务只依赖 starter。
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：Spring Boot 3 自动配置注册入口。

自动配置触发条件：

- classpath 存在 `DemoLog`、AspectJ `@Aspect`、`ProceedingJoinPoint`。
- `demo.observability.demolog.enabled=true` 或未配置。
- 不存在用户自定义的 `DemoLogReporter` 或 `DemoLogAspect` 时才创建默认 Bean。

配置入口：

```yaml
demo:
  observability:
    demolog:
      enabled: true
      slow-threshold: 500ms
```

测试覆盖：

- 自动配置默认生效。
- `demo.observability.demolog.enabled=false` 时不创建相关 Bean。
- 用户自定义 `DemoLogReporter` 时默认 reporter 退让。
- 用户自定义 `DemoLogAspect` 时默认 aspect 退让。
- 服务模块引入 starter 后，原 `@DemoLog` 行为保持。

### HTTP client 模式

`order-service` 的 catalog 调用封装在 `CatalogProductClient` 抽象后面，当前提供两种实现：

- `FeignCatalogProductClient`：默认模式，复用 OpenFeign、Spring Cloud CircuitBreaker 和现有 fallback。
- `RestClientCatalogProductClient`：`demo.clients.catalog.mode=restclient` 时启用，使用 Spring Framework 6 `RestClient`、Basic Auth、独立连接/读取超时和同一套 fallback 结果。

配置入口：

```yaml
demo:
  clients:
    catalog:
      mode: feign
      connect-timeout: 500ms
      read-timeout: 3s
```

`CatalogLookupService` 会按模式选择实现，并把模式写入缓存 key，避免 Feign 与 RestClient 在同一进程内切换时复用错误缓存结果。

### Resilience4j 服务治理

`CatalogGovernanceService` 位于 `OrderService` 和 `CatalogLookupService` 之间，专门承载服务治理示例：

- 默认失败路径使用同步 `@Retry(name = "catalog-service")` + `@CircuitBreaker(name = "catalog-service")`，`failCatalog=true` 时下游 fallback 结果会被治理层转换为 `CatalogLookupFailedException`，从而触发重试和熔断统计。
- `slowCatalog=true` 使用异步 `@TimeLimiter(name = "catalog-service")`，由 `catalogGovernanceExecutor` 执行下游调用，并通过 Micrometer tracing 的 `CurrentTraceContext` 包装 executor，避免普通调用链的 trace 传播回退。
- `rateLimit=true` 使用独立 `@RateLimiter(name = "catalog-rate-limit")`，便于本地快速复现限流。
- `bulkhead=true&holdBulkhead=true` 使用独立 `@Bulkhead(name = "catalog-bulkhead")`，通过短暂持有舱壁稳定触发并发拒绝。
- 所有治理 fallback 统一复用 `CatalogFallbackSupport`，响应体中的 `fallback=true` 与 `fallbackUsed=true` 会明确告诉调用方发生了降级。

配置集中在 `order-service/src/main/resources/application.yml` 的 `resilience4j.*` 和 `demo.resilience.catalog.*` 下。当前 TimeLimiter 为 `1s`，Feign read timeout 为 `3s`，因此慢调用演示优先由 Resilience4j 截断；如果 HTTP client timeout 更短，则会先走 client timeout。

### Sentinel 可选专题

Sentinel 只在 `order-service` 的 Maven `sentinel` profile 中引入：

- `spring-cloud-starter-alibaba-sentinel` 由 Spring Cloud Alibaba BOM 管理，当前版本基线为 Spring Cloud Alibaba `2025.0.0.0` + Sentinel `1.8.9`。
- `build-helper-maven-plugin` 只在 `-Psentinel` 时把 `src/sentinel/java` 和 `src/sentinel-test/java` 加入编译，默认 `./mvnw test` 不编译 Sentinel 专题源码。
- `application-sentinel.yml` 只在 `SPRING_PROFILES_ACTIVE=sentinel` 时加载，设置 Sentinel transport、日志目录和本地规则参数。

主代码通过无 Sentinel 依赖的 `OrderTrafficGuard` 抽象隔离：

- 默认 profile 使用 `NoopOrderTrafficGuard`，`sentinelFlow`、`sentinelHotSku` 参数不改变默认行为。
- `sentinel` profile 使用 `SentinelOrderTrafficGuard`，通过 Sentinel `SphU.entry(...)` 显式进入资源。
- `GlobalExceptionHandler` 把 `SentinelBlockedException` 转换为 `429` ProblemDetail，并返回 `resource`、`strategy` 等排查字段。

当前本地规则：

- `order-preview-flow`：`POST /api/orders/preview?sentinelFlow=true`，默认 QPS `1`。
- `order-preview-hot-sku`：`POST /api/orders/preview?sentinelHotSku=true`，按 `sku` 热点参数限流。
- `order-catalog-degrade-probe`：`GET /api/orders/sentinel/degrade-probe?slow=true`，通过慢调用比例触发 Sentinel Degrade。

Sentinel 与 Resilience4j 的定位刻意并存：Resilience4j 保持默认服务治理路径，Sentinel 作为阿里系流控/热点参数/规则中心专题，用独立 profile 防止默认启动门槛升高。

### JWT profile

默认安全模式是 `demo.security.mode=basic`，保持原有 Basic Auth 学习路径。`application-jwt.yml` 设置：

```yaml
demo:
  security:
    mode: jwt
    jwt:
      secret: ${DEMO_SECURITY_JWT_SECRET:spring3-local-dev-secret-key-32-bytes-minimum}
```

两个业务服务在 `jwt` profile 下都会启用 Resource Server：

- 无 token 或错误 token 返回 `401`。
- 普通用户 token 的 `roles=["USER"]` 能访问业务接口，但访问 admin 接口返回 `403`。
- 管理员 token 的 `roles=["USER","ADMIN"]` 能访问 `@PreAuthorize("hasRole('ADMIN')")` 接口。
- `scope` claim 也会转换为 `SCOPE_*` 权限，方便后续演示 scope 级授权。

本项目为了保留服务间调用示例，在 JWT 模式下仍保留 Basic Auth。`order-service` 调用 `catalog-service` 继续使用现有 Basic 凭证；生产实现可以改为 OAuth2 `client_credentials` service token 或由网关/服务网格处理内部身份。

## 运行方式

```bash
./mvnw test
./mvnw -Pintegration-test verify
./mvnw package -DskipTests
docker compose -f deployment/docker-compose.yml up -d
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
- `catalog-service` 增加 JWT profile 测试，覆盖无 token、错误 token、普通用户 token、管理员 token，以及 JWT 模式下 Basic 服务凭证仍可用于内部调用。
- `demo-observability-autoconfigure` 使用 `ApplicationContextRunner` 覆盖自动配置默认生效、关闭配置、用户 Bean 覆盖和 AOP 事件上报。
- `order-service` 覆盖 health 公开访问、业务认证、参数校验、Feign 正常调用、Feign 失败降级、admin 权限、Prometheus endpoint。
- `order-service` 增加 W3C `traceparent` 传播测试，验证 Feign 出站请求携带同一个 traceId。
- `order-service` 增加 RestClient 模式测试，覆盖正常调用、Basic Auth 出站、500 fallback 和读超时 fallback。
- `order-service` 增加 Resilience4j 集成测试，覆盖 Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 触发方式，并验证 Prometheus 暴露对应指标。
- `order-service` 增加 JWT profile 测试，覆盖无 token、错误 token、普通用户 token、管理员 token，并验证服务间调用仍使用 Basic。
- `gateway-service` 覆盖路由匹配、前缀改写、`Authorization` 透传、`X-Request-Id`、下游 `401` 透出、fallback、本地限流、health/prometheus。
- `gateway-service` 增加 Testcontainers 集成测试，使用 `nginx:1.27.3-alpine` 作为容器化下游，覆盖真实 Gateway 路由到外部依赖的路径。
- `.github/workflows/ci.yml` 包含 `unit-tests` 和 `integration-tests` 两个 job，分别覆盖默认测试和 Docker 集成测试。
- `deployment/docker-compose.yml` 使用 Actuator readiness 作为容器 healthcheck，验证本地容器化部署链路。
- `order-service` 增加 `OrderVirtualThreadProfileTest`，覆盖 `virtual-thread` profile 下订单预览正常路径和 `@Async` 虚拟线程观察接口。
- `order-service` 增加 `OrderSentinelProfileTest`，在 `-Psentinel` 下覆盖 Sentinel profile 启动、默认业务路径、QPS 限流、热点参数限流和慢调用熔断探针。
- Feign 测试使用 MockWebServer，不依赖公网和手动启动 provider。

## 明确不做

- 不接入 MySQL、PostgreSQL、JPA、MyBatis、Flyway、Liquibase。
- 不接入 Redis 或 Spring Session Redis。
- 不实现 Kafka、RabbitMQ、RocketMQ 代码。
- 不把 Nacos 作为默认 profile 的必需依赖。
- 不把 Sentinel 作为默认 profile 的必需依赖。
- 不把 Zipkin 作为业务服务启动的硬依赖。
- 不提交真实 Sentry DSN。
- 不做前端页面，只使用 REST API、Swagger UI、Prometheus、Grafana 和 Zipkin。
