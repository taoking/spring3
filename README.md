# Spring Boot 3 Learning Lab

[![CI](https://github.com/taoking/spring3/actions/workflows/ci.yml/badge.svg)](https://github.com/taoking/spring3/actions/workflows/ci.yml)

这是一个用于长期学习、复盘和面试准备的 Spring Boot 3 示例项目。

项目不接入数据库和 Redis，重点演示 Spring Boot 3 常用组件、注解、配置、测试、监控和错误上报。

## 模块

- `common`：共享 DTO、AOP 注解等公共类型。
- `demo-observability-autoconfigure`：演示型自动配置模块，提供 `@DemoLog` 切面、属性绑定和默认 Bean。
- `demo-observability-spring-boot-starter`：演示型 starter，只做依赖聚合，供业务服务引入。
- `catalog-service`：商品服务 provider，默认端口 `8081`。
- `order-service`：订单服务 consumer，默认端口 `8080`，默认通过 OpenFeign 调用 `catalog-service`，也可切换为 RestClient。
- `gateway-service`：Spring Cloud Gateway 统一入口，默认端口 `8088`。

## 技术栈

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven 多模块 + Maven Wrapper
- Spring Web MVC、WebFlux Gateway、Validation、Security、OAuth2 Resource Server / JWT、OpenFeign、RestClient、Resilience4j、Caffeine、Actuator、Micrometer Prometheus、Micrometer Tracing、Zipkin、Sentry、SpringDoc OpenAPI、Spring Cloud Contract、RabbitMQ/Kafka 可选 profile、Spring AOT / Native Image buildpacks 验证、自定义 starter / autoconfigure

## 快速启动

```bash
./mvnw test
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl gateway-service spring-boot:run
```

Docker 可用时，可显式运行 Testcontainers 集成测试：

```bash
./mvnw -Pintegration-test verify
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

默认账号：

- 普通用户：`user / user123`
- 管理员：`admin / admin123`

## 常用地址

- Order Swagger UI: `http://localhost:8080/swagger-ui.html`
- Catalog Swagger UI: `http://localhost:8081/swagger-ui.html`
- Gateway health: `http://localhost:8088/actuator/health`
- Gateway Prometheus metrics: `http://localhost:8088/actuator/prometheus`
- Order health: `http://localhost:8080/actuator/health`
- Catalog health: `http://localhost:8081/actuator/health`
- Order Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Catalog Prometheus metrics: `http://localhost:8081/actuator/prometheus`
- Zipkin UI: `http://localhost:9411/zipkin`
- RabbitMQ Management UI: `http://localhost:15672`（`rabbitmq` profile 可选专题）
- Kafka UI: `http://localhost:8089`（`kafka` profile 可选专题）

## 示例调用

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

通过网关调用：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview
```

触发 catalog 降级：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  'http://localhost:8080/api/orders/preview?failCatalog=true'
```

触发 Sentry 验证异常：

```bash
SENTRY_DSN='https://examplePublicKey@o0.ingest.sentry.io/0' ./mvnw -pl order-service spring-boot:run

curl -u admin:admin123 -X POST http://localhost:8080/api/orders/admin/sentry-error
```

没有设置 `SENTRY_DSN` 时，应用仍可正常启动，但不会真实上报事件。

## Prometheus + Grafana + Zipkin

当前仓库提供 Docker Compose 配置：

```bash
docker compose -f observability/docker-compose.yml up
```

启动三个 Spring Boot 服务后访问：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Zipkin: `http://localhost:9411/zipkin`
- Grafana 默认账号：`admin / admin`

Prometheus 在 Docker 中通过 `host.docker.internal:8080`、`host.docker.internal:8081` 和 `host.docker.internal:8088` 抓取本机服务。Zipkin 接收三个服务上报的 trace，日志格式包含 `application/traceId/spanId`。

## 学习路线

### 当前已实现

- REST API 与常用 Web 注解
- Jakarta Validation 参数校验
- `ProblemDetail` 统一错误响应
- Spring Security Basic 与 `@PreAuthorize`
- OAuth2 Resource Server / JWT profile，支持 Bearer token、roles/scope 映射和方法级授权
- OpenFeign 服务间调用，支持配置切换到 RestClient
- Resilience4j Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 与降级
- Spring Cloud Gateway 路由、过滤器、认证透传、限流、fallback
- Micrometer Tracing + Zipkin 链路追踪，日志输出 traceId/spanId
- Caffeine 本地缓存
- AOP 自定义注解切面，已迁移为 Spring Boot 3 自动配置 starter
- `@Async` 异步任务
- `@Scheduled` 定时任务
- Spring Event 事件发布与监听
- Actuator、Micrometer、Prometheus、Grafana
- Sentry 异常上报
- SpringDoc OpenAPI / Swagger UI
- Spring Cloud Contract provider/consumer 契约测试

### 已补充的可选专题

- Nacos 注册中心/配置中心：已补充本地 Docker Compose、版本基线、可选 Maven profile、`application-nacos.yml`、服务注册发现、配置中心接入和面试重点；默认运行路径不依赖 Nacos。
- Gateway 服务发现路由：`gateway-service` 默认使用 localhost 静态路由，`nacos` profile 下切换为 `lb://catalog-service` 和 `lb://order-service`。
- 链路追踪：已补充 Micrometer Tracing、Zipkin Docker Compose、日志关联 ID、Feign trace context 传播测试；默认运行路径不强制要求 Zipkin 已启动。
- HTTP client 选型：已补充 RestClient 调用模式、超时/认证/fallback 复用、测试覆盖和 OpenFeign / RestClient / WebClient / `@HttpExchange` 对比。
- JWT 资源服务器：已补充 `jwt` profile、HS256 本地开发 token、`roles` claim 到 `ROLE_*` 映射、scope 支持和无 token/错 token/权限不足测试；默认 Basic Auth 不变。
- 自定义 starter / autoconfigure：已新增 `demo-observability-autoconfigure` 和 `demo-observability-spring-boot-starter`，用 `@AutoConfiguration` 自动装配 `@DemoLog` AOP，并覆盖条件装配、属性绑定、禁用开关和用户 Bean 覆盖测试。
- Resilience4j 深化：已补充 Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 触发参数、fallback 响应和 Prometheus 指标验证。
- Testcontainers / CI：已新增 GitHub Actions workflow，默认跑 `./mvnw test`，Docker job 跑 `./mvnw -Pintegration-test verify`；Gateway 集成测试使用固定版本 Nginx 容器验证真实下游路由。
- 工程质量与 CI 门禁：已补 [工程质量与 CI 门禁专题](docs/engineering-quality-playbook.md)，覆盖 CI 分层、JaCoCo、静态扫描、依赖安全、SBOM、镜像扫描和 ArchUnit 规则候选。
- Docker 镜像与部署：已新增 `catalog-service`、`order-service` Dockerfile 和 `deployment/docker-compose.yml`，容器网络内使用服务名调用，并提供 Prometheus 服务名抓取配置。
- Java 21 虚拟线程：已新增 `virtual-thread` profile、虚拟线程版 `demoTaskExecutor`、阻塞 I/O 观察接口和 profile 回归测试；默认 profile 仍使用传统线程池。
- JVM、并发和 Java 21 诊断：已补 [JVM、并发和 Java 21 诊断专题](docs/jvm-concurrency-playbook.md)，覆盖线程池、`CompletableFuture`、pinned thread、JFR、jcmd、jstack、jmap、GC log 和故障排查路径。
- Sentinel：已新增独立 `sentinel` Maven/Spring profile、本地内存规则、QPS 限流、热点参数、慢调用熔断探针和 profile 回归测试；默认 profile 不引入 Sentinel 依赖。
- 结构化日志：已新增 `json-logging` profile，使用 Spring Boot 3.5 内建 structured logging 输出 JSON，并补充 requestId、traceId、spanId、status、elapsedMs 和敏感头脱敏测试。
- API 治理：已补充 ProblemDetail 稳定错误码、requestId、timestamp、订单 v1/v2 示例、旧接口废弃头和 OpenAPI 分组。
- Spring Cloud Contract：已补充 `catalog-service` provider 契约、生成 stubs jar、`order-service` consumer Stub Runner 测试，覆盖成功、商品不存在和模拟失败三类下游响应。
- RabbitMQ 消息队列：已新增可选 `rabbitmq` Maven/Spring profile、本地 Compose、订单预览事件发布/消费、eventId 幂等、消费重试和 DLQ，以及 Testcontainers 集成测试；默认运行路径不引入 MQ。
- Kafka 消息队列：已新增可选 `kafka` Maven/Spring profile、本地 Compose、订单预览事件发布/消费、message key 分区顺序、manual ack、eventId 幂等、消费重试和 DLT，以及 Testcontainers 集成测试；默认运行路径不引入 Kafka。
- 消息队列生产语义：已补充 Kafka/RabbitMQ/RocketMQ 的可靠投递、顺序、幂等、重试、DLT/DLQ、lag/堆积排查和事务边界，见 [消息队列生产语义专题](docs/messaging-production-playbook.md)。
- Native Image / AOT：已补充 `catalog-service` AOT、Docker buildpacks native 镜像、容器启动和 health check 闭环；当前本机 `native:compile` 仍阻塞于未安装 GraalVM `native-image`，但 buildpacks 路径已验证通过。
- Kubernetes：已新增 `deployment/k8s` 最小部署示例，覆盖 Namespace、ConfigMap、Secret 示例、Deployment、Service、Actuator readiness/liveness、资源 requests/limits、滚动发布、优雅停机和 Prometheus 抓取注解。
- Kubernetes 生产化：已补 [Kubernetes 生产化专题](docs/kubernetes-production-playbook.md)，覆盖 Ingress、HPA、PDB、ServiceMonitor、Secret 管理、镜像 tag/digest、GitOps、回滚和故障排查。

### 后续计划

当前项目已经覆盖 Spring Boot 3 常用组件。后续面向资深面试准备时，扩展重点放在微服务治理、可观测性、工程化、底层机制和技术取舍，不单纯堆依赖。

#### P0：优先补充

- 资深面试覆盖度检查：已按架构师/面试官视角补充整体评分、追问压力测试和补齐优先级，见 [资深面试覆盖度检查报告](docs/interview-coverage-assessment.md)；可执行任务 prompt 见 [资深面试覆盖补齐计划](docs/task-plans/19-interview-expansion.md)。
- 数据一致性与事务边界：当前项目不接数据库，已补 [数据一致性与事务边界专题](docs/data-consistency-playbook.md)，覆盖事务传播、隔离级别、幂等、outbox/inbox、补偿和对账的设计型内容。
- Redis 与缓存治理：当前仅有 Caffeine，已补 [Redis 与缓存治理专题](docs/redis-cache-playbook.md)，覆盖缓存一致性、穿透/击穿/雪崩、分布式锁、热点 key 和限流计数设计内容。
- Nacos 深化：已完成 `nacos` profile、服务注册发现、启动期配置中心读取；后续补动态刷新、namespace/group 多环境隔离和 Testcontainers 集成测试。
- Spring Cloud Gateway 深化：已完成 `gateway-service`、静态/Nacos 路由、过滤器、鉴权透传、限流、fallback、CORS 和 `X-Canary` 灰度路由；已补 [Gateway 生产能力专题](docs/gateway-production-playbook.md)。
- 链路追踪 / 可观测性生产化：已完成 Micrometer Tracing + Zipkin 基线；已补 [可观测性生产化专题](docs/observability-production-playbook.md)、PromQL 查询、Prometheus 告警规则草案和故障排查 runbook。
- RestClient / `@HttpExchange`：已补充 RestClient 调用模式、统一 fallback、超时配置和选型对比；后续可补 `@HttpExchange` 声明式接口示例。
- OAuth2 Resource Server / JWT：已完成 JWT profile、Bearer token 验证、角色映射和测试；已补 [OAuth2 / JWT 生产化专题](docs/security-oauth2-playbook.md)，预留真实 IdP 的 `issuer-uri` / `jwk-set-uri` 配置入口，并沉淀 JWK rotation 和 `client_credentials` 服务间 token 设计。
- 自动配置原理：已完成演示型 observability starter；后续可补源码阅读笔记和更多条件装配案例。
- Resilience4j 深化：已完成 Retry、RateLimiter、Bulkhead、TimeLimiter、CircuitBreaker 治理矩阵；后续可补更贴近生产的异常分类、舱壁线程池和告警规则。
- Testcontainers / 集成测试：已完成 Gateway 下游容器集成测试和 GitHub Actions；后续可扩展到 Nacos 或追踪后端。

#### P1：建议补充

- Docker 镜像与部署：已完成两个业务服务镜像、应用 Compose、readiness/liveness、优雅停机、JVM 参数和容器网络 Prometheus；后续可补镜像 SBOM 和 registry 发布流程。
- Java 21 虚拟线程 / JVM 诊断：已完成 `virtual-thread` profile、请求线程和 `@Async` 线程观察接口；已补 [JVM、并发和 Java 21 诊断专题](docs/jvm-concurrency-playbook.md)，覆盖 pinned thread、线程池、JFR、jcmd、jstack、jmap 和 GC 排障。
- Sentinel：已完成可选 `sentinel` profile、QPS 限流、热点参数、慢调用熔断探针和 Resilience4j 对比；后续可补 Dashboard/Nacos 动态规则和集群限流。
- 结构化日志：已完成 `json-logging` profile、Servlet 请求日志过滤器、Gateway 结构化审计字段和敏感头脱敏；后续可补 Loki/ELK 查询样例和统一错误码字段。
- API 治理：已完成稳定错误码、版本路由、废弃响应头、OpenAPI 分组和兼容策略说明；后续可补接口变更 changelog 模板。
- Spring Cloud Contract：已完成 catalog/order 契约测试基线；后续可补契约发布到制品库、CI consumer matrix 和 breaking change 演示。
- 工程质量与 CI 门禁：已补 [工程质量与 CI 门禁专题](docs/engineering-quality-playbook.md)，后续可按需实装 ArchUnit、JaCoCo 报告、SBOM 和依赖安全扫描。

#### P2：路线保留

- Kafka、RabbitMQ、RocketMQ：RabbitMQ 和 Kafka 基线已完成；已补 [消息队列生产语义专题](docs/messaging-production-playbook.md)，覆盖 Kafka producer transaction 边界、retry topic、consumer lag、RabbitMQ publisher confirm/manual ack/prefetch 和 RocketMQ tag/顺序/事务消息设计。
- Native Image / AOT：已补充专题文档、`catalog-service` RuntimeHints 示例和 buildpacks native 镜像验证；后续可在安装 GraalVM 后补本机 binary，并逐步扩展到 `order-service` / `gateway-service`。
- Kubernetes：已补最小 YAML 和使用说明；已补 [Kubernetes 生产化专题](docs/kubernetes-production-playbook.md)，覆盖 Ingress、HPA、PDB、ServiceMonitor CRD、镜像 registry 发布和 GitOps 流程。

详见：

- [使用说明](docs/USAGE.md)
- [实施文档](docs/IMPLEMENTATION.md)
- [Nacos 补充专题](docs/nacos-playbook.md)
- [Native Image / AOT 专题](docs/native-aot.md)
- [Native Image 完整验证专题](docs/native-image-verification-playbook.md)
- [Kubernetes 部署示例](docs/kubernetes.md)
- [Kubernetes 生产化专题](docs/kubernetes-production-playbook.md)
- [Spring Boot 3 面试补充路线](docs/interview-roadmap.md)
- [资深面试覆盖度检查报告](docs/interview-coverage-assessment.md)
- [后续任务计划 Prompt 索引](docs/task-plans/README.md)
- [资深面试覆盖补齐计划](docs/task-plans/19-interview-expansion.md)
- [数据一致性与事务边界专题](docs/data-consistency-playbook.md)
- [Redis 与缓存治理专题](docs/redis-cache-playbook.md)
- [OAuth2 / JWT 生产化专题](docs/security-oauth2-playbook.md)
- [可观测性生产化专题](docs/observability-production-playbook.md)
- [Gateway 生产能力专题](docs/gateway-production-playbook.md)
- [JVM、并发和 Java 21 诊断专题](docs/jvm-concurrency-playbook.md)
- [工程质量与 CI 门禁专题](docs/engineering-quality-playbook.md)
- [消息队列后续计划](docs/messaging-roadmap.md)
- [消息队列生产语义专题](docs/messaging-production-playbook.md)
- [Kafka 使用与面试专题](docs/kafka-playbook.md)
- [Kafka 专题计划](docs/task-plans/18-kafka.md)
- [消息队列生产语义计划](docs/task-plans/24-messaging-production.md)
- [JVM、并发和 Java 21 诊断计划](docs/task-plans/25-jvm-concurrency.md)
- [工程质量与 CI 门禁计划](docs/task-plans/26-engineering-quality.md)
- [Kubernetes 生产化计划](docs/task-plans/27-kubernetes-production.md)
- [Native Image 完整验证计划](docs/task-plans/28-native-image-verification.md)
