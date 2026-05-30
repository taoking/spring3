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
- `integration-test` profile 可以通过 Testcontainers 验证 Gateway 到容器化下游的真实路由；配合 `-Prabbitmq` 或 `-Pkafka` 时可以验证对应消息链路。
- GitHub Actions 分别执行默认单元测试和 Docker 集成测试。
- `catalog-service` 和 `order-service` 镜像可以构建，并能通过本地 Compose 在容器网络中完成服务间调用。
- 容器版 Prometheus 可以通过服务名抓取业务服务指标。
- `order-service` 可以通过 `virtual-thread` profile 启用 Java 21 虚拟线程，并保留默认 profile 的传统线程池。
- `order-service` 可以通过 `-Psentinel` + `sentinel` profile 启用 Sentinel 本地规则，演示限流、热点参数和慢调用熔断。
- 三个服务可以通过 `json-logging` profile 输出 JSON 日志，Servlet 服务请求日志包含 requestId、traceId、spanId、status、elapsedMs，并验证敏感认证头不会原样输出。
- 错误响应包含稳定 `errorCode`、`requestId`、`timestamp`，订单服务同时提供 `/api/v1/orders/preview` 和 `/api/v2/orders/preview` 示例，旧 `/api/orders/preview` 返回废弃提示响应头。
- `catalog-service` 可以通过 Spring Cloud Contract 生成 provider 验证测试和本地 `stubs` jar，`order-service` 可以使用 Stub Runner 消费 stubs 验证服务间契约。
- `order-service` 可以通过 `-Prabbitmq` + `rabbitmq` profile 启用 RabbitMQ 订单预览事件示例，覆盖生产、消费、eventId 幂等、重试和死信队列。
- `order-service` 可以通过 `-Pkafka` + `kafka` profile 启用 Kafka 订单预览事件示例，覆盖生产、消费、message key 分区顺序、manual ack、eventId 幂等、重试和 DLT。
- `catalog-service` 可以完成 Spring AOT 处理，并已通过 Docker buildpacks 完成 native 镜像构建、容器启动和 health check；本机 native binary 编译不进入默认 CI。
- `catalog-service` 和 `order-service` 有 Kubernetes 最小部署 YAML，覆盖 Deployment、Service、ConfigMap、Secret 示例、Actuator readiness/liveness、资源 requests/limits、滚动发布和优雅停机。
- Nacos 作为可选专题补充，不影响默认 profile 的启动和测试。
- 默认 profile 没有数据库、Redis、Kafka、RabbitMQ、RocketMQ 运行依赖；RabbitMQ 和 Kafka 仅作为隔离的可选 profile。
- `@DemoLog` AOP 能通过自定义 starter 自动装配，并允许关闭或覆盖默认 Bean。

## 当前实现

### 工程结构

- `common`：共享 DTO、自定义 AOP 注解、API 错误码和通用响应头常量。
- `demo-observability-autoconfigure`：`@DemoLog` 自动配置、属性绑定、默认 reporter、切面 Bean。
- `demo-observability-spring-boot-starter`：依赖聚合模块，不包含业务代码。
- `catalog-service`：商品 provider，端口 `8081`。
- `order-service`：订单 consumer，端口 `8080`。
- `gateway-service`：Spring Cloud Gateway 统一入口，端口 `8088`。

### 组件覆盖

- Web MVC：`@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@RequestParam`、`@PathVariable`、`@RequestBody`。
- 参数校验：Jakarta Validation、`@Valid`、`@Validated`、`@NotBlank`、`@Positive`。
- 配置绑定：`@ConfigurationProperties`、YAML 配置。
- 错误处理：`@RestControllerAdvice`、`@ExceptionHandler`、Spring Boot 3 `ProblemDetail`、稳定错误码、请求 ID 和时间戳扩展字段。
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
- 结构化日志：`json-logging` profile 使用 Spring Boot 3.5 内建 structured logging 输出 logstash JSON，Servlet 请求日志由 observability starter 自动配置。
- 错误上报：Sentry Jakarta starter，DSN 通过环境变量读取。
- API 文档：SpringDoc OpenAPI / Swagger UI，按订单版本和 Catalog public/admin 分组。
- 集成测试：`integration-test` Maven profile 使用 Failsafe 运行 `**/*IT.java`，当前通过 Testcontainers 启动固定版本 Nginx 容器验证 Gateway 真实下游，并在 `-Prabbitmq`/`-Pkafka` 下启动固定版本 RabbitMQ/Kafka 容器验证消息链路。
- CI：GitHub Actions 使用 JDK 21 和 Maven cache，默认 job 运行 `./mvnw -B test`，Docker job 运行 `./mvnw -B -Pintegration-test verify`、RabbitMQ IT 命令和 Kafka IT 命令。
- 容器化：`catalog-service/Dockerfile` 和 `order-service/Dockerfile` 使用 JDK 21 JRE Alpine 镜像、非 root 用户和 `JAVA_OPTS`；`deployment/docker-compose.yml` 启动两个业务服务、Prometheus、Grafana、Zipkin。
- 容器网络：`order-service` 在 Compose 中通过 `DEMO_CLIENTS_CATALOG_BASE_URL=http://catalog-service:8081` 调用 `catalog-service`，Prometheus 通过 `catalog-service:8081` 和 `order-service:8080` 抓取指标。
- Kubernetes：`deployment/k8s` 提供最小 YAML，包含 `spring3` namespace、业务 ConfigMap、空 Sentry Secret 示例、两个业务 Deployment/Service、Actuator 探针、滚动发布策略、资源配额和 Prometheus 抓取注解。
- 虚拟线程：`order-service` 的 `virtual-thread` profile 设置 `spring.threads.virtual.enabled=true`，并把 `demoTaskExecutor` 从默认 `ThreadPoolTaskExecutor` 切换为虚拟线程 `SimpleAsyncTaskExecutor`。
- 线程观察：`/api/orders/thread-probe` 支持请求线程和 `@Async` 线程两种模式，响应返回线程名、是否虚拟线程和模拟 I/O 等待时长。
- Nacos 可选专题：通过 `-Pnacos` Maven profile 和 `SPRING_PROFILES_ACTIVE=nacos` 启用服务注册发现、配置中心和 Feign 服务名调用。
- Sentinel 可选专题：通过 `-Psentinel` Maven profile 编译隔离源码，通过 `SPRING_PROFILES_ACTIVE=sentinel` 加载本地 Flow、ParamFlow、Degrade 规则。
- RabbitMQ 可选专题：通过 `-Prabbitmq` Maven profile 编译隔离源码，通过 `SPRING_PROFILES_ACTIVE=rabbitmq` 加载 AMQP 连接、exchange、queue、DLQ 和 listener retry 配置。
- Kafka 可选专题：通过 `-Pkafka` Maven profile 编译隔离源码，通过 `SPRING_PROFILES_ACTIVE=kafka` 加载 Kafka producer/consumer、topic、manual ack、重试和 DLT 配置。
- Native Image / AOT：使用 Spring Boot parent 内置 `native` profile 做手动学习验证，当前以 `catalog-service` 为最小目标；本机已通过 `spring-boot:process-aot` 和 Docker buildpacks native 镜像 health check，`native:compile` 因未安装 GraalVM `native-image` 而停止。
- JSON 日志专题：`json-logging` profile 开启 `logging.structured.format.console=logstash` 和 `demo.observability.http-logging.enabled=true`。
- API 治理专题：`common` 维护错误码常量，两个 Servlet 服务的 `ProblemDetail` 统一补充 `errorCode`、`requestId`、`timestamp`，订单服务提供 v1/v2 版本路由和旧路径废弃头。
- 契约测试专题：`contract-test` Maven profile 启用 Spring Cloud Contract Verifier / Stub Runner，`catalog-service` 维护 provider 契约并生成 `stubs` classifier，`order-service` 使用本地 stubs 验证 consumer fallback 行为。

### 自定义 starter / autoconfigure

`@DemoLog` 能力已从 `catalog-service` 和 `order-service` 的重复 `LoggingAspect` 迁移到演示型 starter：

- `demo-observability-autoconfigure`：提供 `DemoLogAutoConfiguration`、`DemoLogAspect`、`DemoLogReporter`、`DemoLogProperties`。
- `DemoHttpRequestLoggingAutoConfiguration`：在 Servlet Web 应用、`demo.observability.http-logging.enabled=true` 且 classpath 存在 Spring Web 时注册请求日志过滤器。
- `DemoHttpRequestLoggingFilter`：生成或透传 `X-Request-Id`，写回响应头，把 `requestId` 放入 MDC，并记录 `event`、`method`、`path`、`status`、`elapsedMs`、`authScheme` 等结构化字段。
- `demo-observability-spring-boot-starter`：聚合 autoconfigure 和 `spring-boot-starter-aop`，业务服务只依赖 starter。
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：Spring Boot 3 自动配置注册入口。

自动配置触发条件：

- classpath 存在 `DemoLog`、AspectJ `@Aspect`、`ProceedingJoinPoint`。
- `demo.observability.demolog.enabled=true` 或未配置。
- 不存在用户自定义的 `DemoLogReporter` 或 `DemoLogAspect` 时才创建默认 Bean。
- `demo.observability.http-logging.enabled=true` 时才启用请求日志过滤器，默认 profile 不新增入口请求日志。

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
- `OrderJsonLoggingProfileTest` 覆盖 `json-logging` profile、`X-Request-Id` 响应头、JSON 请求日志字段和敏感认证头脱敏。
- `CatalogControllerTest` 和 `OrderControllerTest` 覆盖稳定错误码、`X-Request-Id` 回写、版本路由、旧接口废弃头和 OpenAPI 分组。

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

### Spring Cloud Contract 契约测试

契约测试通过独立 `contract-test` Maven profile 隔离，不影响默认构建路径。

Provider 侧：

- `catalog-service/src/contract-test/resources/contracts/catalog/*.groovy` 定义 HTTP 契约。
- `CatalogContractBase` 使用 `@SpringBootTest` + `@AutoConfigureMockMvc` 提供生成测试的基类。
- Spring Cloud Contract Maven Plugin 执行 `generateTests`、`convert`、`generateStubs`，并关闭增量生成，保证重复运行也会稳定生成测试源和 stubs。

Consumer 侧：

- `OrderCatalogContractStubTest` 使用 `@AutoConfigureStubRunner` 加载 `com.taoking.spring3:catalog-service:+:stubs:18081`。
- 测试覆盖 `SKU-1001` 正常响应、`UNKNOWN` 的 `404 ProblemDetail`、`fail=true` 的 `500 ProblemDetail`。
- Consumer 不依赖真实 `catalog-service` 进程，只依赖 provider 生成并安装到本地 Maven 仓库的 stubs jar。

和现有 MockWebServer 测试的边界：

- MockWebServer 继续用于验证 Feign/RestClient 的请求头、超时和 fallback 细节。
- Spring Cloud Contract 用于把 provider 的 HTTP 响应结构固化为契约，防止 provider 改坏字段后 consumer 测试仍然使用过期手写 mock。

### Native Image / AOT

Native Image / AOT 不新增业务依赖，也不改变默认构建链路。Spring Boot starter parent `3.5.14` 已提供 `native` Maven profile，本项目用 `catalog-service` 完成最小 native 验证路径，并保留本机 GraalVM binary 构建说明。

当前验证结果：

- `./mvnw help:active-profiles -Pnative -pl catalog-service -am` 可以看到 Spring Boot parent 提供的 `native` profile。
- `./mvnw -pl catalog-service -am package -DskipTests` 通过，说明普通 jar 构建不受影响。
- `./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests` 通过，说明 `catalog-service` 可以完成 Spring AOT 处理。
- `./mvnw -Pnative -pl catalog-service native:compile -DskipTests` 已尝试，当前本机失败原因是未安装 GraalVM `native-image`。
- `./mvnw -Pnative -pl catalog-service spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local` 通过，生成 buildpacks native 镜像。
- `docker run -d -p 18081:8081 -e TRACING_SAMPLING_PROBABILITY=0.0 --name spring3-catalog-native-test spring3/catalog-service-native:local` 启动后，`curl -i http://localhost:18081/actuator/health` 返回 `HTTP 200` 和 `UP`。

native 兼容处理：

- `catalog-service` 增加 `CatalogNativeRuntimeHints`，注册 Hibernate Validator / JBoss Logging 动态查找的 `Log_$logger` 和 `Messages_$bundle`。
- 本次失败复盘路径为：先出现 `Invalid logger interface org.hibernate.validator.internal.util.logging.Log`，补 hints 后前进到 `Invalid bundle interface org.hibernate.validator.internal.util.logging.Messages`，继续补 hints 后容器启动通过。
- native 容器日志显示启动耗时约 `0.322s`，native-image 阶段产物规模约 `129.89MB`，peak RSS 约 `5.19GB`。

执行原则：

- 先验证依赖最少的 `catalog-service`，再考虑 `order-service` 和 `gateway-service`。
- 不把 native binary 构建加入默认 CI，避免普通反馈链路变慢。
- 不直接对聚合依赖模块执行 `spring-boot:process-aot`，因为 `common` 和 starter 模块没有 main class。
- SpringDoc、Sentry、OpenFeign、Gateway、Resilience4j、AOP 和 Jackson 的 native 兼容性需要在后续扩展到接口路径、`order-service` 和 `gateway-service` 时逐项验证。

### Kubernetes 部署示例

Kubernetes 示例位于 `deployment/k8s`，用于学习部署对象和面试复盘，不要求真实集群。

当前对象：

- `Namespace`：固定为 `spring3`。
- `ConfigMap`：管理非敏感配置，例如 `APP_ENV`、`JAVA_OPTS`、`DEMO_CLIENTS_CATALOG_BASE_URL`。
- `Secret`：只提供空 `SENTRY_DSN` 示例，不提交真实密钥。
- `Service`：为 `catalog-service` 和 `order-service` 提供 ClusterIP 稳定访问入口。
- `Deployment`：为两个业务服务配置 2 副本、滚动发布、资源 requests/limits、Actuator 探针和 graceful shutdown。

探针策略：

- `startupProbe` 给 JVM 和 Spring 容器启动时间，避免 liveness 过早重启。
- `readinessProbe` 使用 `/actuator/health/readiness`，决定 Pod 是否进入 Service endpoints。
- `livenessProbe` 使用 `/actuator/health/liveness`，只判断进程是否需要重启。

滚动发布与停机配套：

- `maxUnavailable=0` 保持发布过程中旧 Pod 不被过早下线。
- `maxSurge=1` 允许先拉起一个新 Pod。
- `terminationGracePeriodSeconds=35` 大于 Spring `20s` graceful shutdown。
- `preStop sleep 10` 给 endpoints 摘除和连接排空留时间。

Prometheus 当前通过 Service/Pod 注解抓取 `/actuator/prometheus`。如果集群使用 Prometheus Operator，可在真实环境中补 ServiceMonitor CRD，本仓库不直接提交该 CRD 对象，避免 dry-run 依赖集群扩展。

## 运行方式

```bash
./mvnw test
./mvnw -Pintegration-test verify
./mvnw -Pcontract-test -pl catalog-service -am test
./mvnw -Pcontract-test -pl catalog-service -am install
./mvnw -Pcontract-test -pl order-service -am -Dtest=OrderCatalogContractStubTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:build-image -DskipTests -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
kubeconform -strict -summary deployment/k8s/*.yaml
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

- `catalog-service` 覆盖 health/prometheus 公开访问、业务认证、商品查询、404 ProblemDetail、稳定错误码、requestId 回写、OpenAPI 分组、admin 权限。
- `catalog-service` 增加 JWT profile 测试，覆盖无 token、错误 token、普通用户 token、管理员 token，以及 JWT 模式下 Basic 服务凭证仍可用于内部调用。
- `demo-observability-autoconfigure` 使用 `ApplicationContextRunner` 覆盖自动配置默认生效、关闭配置、用户 Bean 覆盖和 AOP 事件上报。
- `order-service` 覆盖 health 公开访问、业务认证、参数校验、稳定错误码、旧接口废弃头、v1/v2 版本路由、OpenAPI 分组、Feign 正常调用、Feign 失败降级、admin 权限、Prometheus endpoint。
- `order-service` 增加 W3C `traceparent` 传播测试，验证 Feign 出站请求携带同一个 traceId。
- `order-service` 增加 RestClient 模式测试，覆盖正常调用、Basic Auth 出站、500 fallback 和读超时 fallback。
- `order-service` 增加 Resilience4j 集成测试，覆盖 Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 触发方式，并验证 Prometheus 暴露对应指标。
- `order-service` 增加 JWT profile 测试，覆盖无 token、错误 token、普通用户 token、管理员 token，并验证服务间调用仍使用 Basic。
- `order-service` 增加 `OrderJsonLoggingProfileTest`，覆盖 `json-logging` profile 输出合法 JSON、生成 `X-Request-Id`、请求日志字段和 `Authorization`/password 脱敏。
- `order-service` 增加 `OrderCatalogContractStubTest`，在 `contract-test` profile 下使用 provider 生成的 stubs 验证正常响应、商品不存在和 catalog 模拟失败 fallback。
- `order-service` 增加 `OrderRabbitMqProfileIT`，在 `rabbitmq` + `integration-test` profile 下用 Testcontainers 启动 `rabbitmq:3.13-management`，验证订单预览事件生产/消费、重复 eventId 幂等跳过和异常 SKU 重试后进入 DLQ。
- `order-service` 增加 `OrderKafkaProfileIT`，在 `kafka` + `integration-test` profile 下用 Testcontainers 启动 `confluentinc/cp-kafka:7.6.1`，验证订单预览事件生产/消费、requestId/traceId 事件字段、重复 eventId 幂等跳过、同 key 顺序消费和异常 SKU 重试后进入 DLT。
- `catalog-service` 增加 Spring Cloud Contract provider 测试，覆盖商品查询成功、`404 ProblemDetail` 和 `500 ProblemDetail` 的响应结构。
- `catalog-service` 已执行 Spring AOT 处理验证；Docker buildpacks native 镜像已启动并通过 health check；本机 native binary 编译已记录缺少 GraalVM `native-image` 的失败原因和后续处理建议。
- `deployment/k8s` 已通过 `kubeconform -strict -summary deployment/k8s/*.yaml` 校验，8 个资源全部有效；当前本机无 Kubernetes API server，`kubectl apply --dry-run=client` 会失败在 API discovery。
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
- 不实现 RocketMQ 代码。
- 不把 RabbitMQ 或 Kafka 作为默认 profile 或核心业务路径的必需依赖。
- 不实现数据库事务消息、outbox、分布式事务消息。
- 不把 native binary 构建加入默认 CI 或默认发布路径。
- 不维护生产级 Kubernetes 集群、Ingress、HPA、ServiceMonitor CRD 或 Helm chart。
- 不把 Nacos 作为默认 profile 的必需依赖。
- 不把 Sentinel 作为默认 profile 的必需依赖。
- 不把 Zipkin 作为业务服务启动的硬依赖。
- 不提交真实 Sentry DSN。
- 不在 INFO 请求日志输出请求体、密码、token 或 `Authorization` 原文。
- 不做前端页面，只使用 REST API、Swagger UI、Prometheus、Grafana 和 Zipkin。
