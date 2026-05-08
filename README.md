# Spring Boot 3 Learning Lab

这是一个用于长期学习、复盘和面试准备的 Spring Boot 3 示例项目。

项目不接入数据库和 Redis，重点演示 Spring Boot 3 常用组件、注解、配置、测试、监控和错误上报。

## 模块

- `common`：共享 DTO、AOP 注解等公共类型。
- `catalog-service`：商品服务 provider，默认端口 `8081`。
- `order-service`：订单服务 consumer，默认端口 `8080`，通过 OpenFeign 调用 `catalog-service`。
- `gateway-service`：Spring Cloud Gateway 统一入口，默认端口 `8088`。

## 技术栈

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven 多模块 + Maven Wrapper
- Spring Web MVC、WebFlux Gateway、Validation、Security、OpenFeign、Resilience4j、Caffeine、Actuator、Micrometer Prometheus、Sentry、SpringDoc OpenAPI

## 快速启动

```bash
./mvnw test
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl gateway-service spring-boot:run
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

触发 Feign 降级：

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

## Prometheus + Grafana

当前仓库提供 Docker Compose 配置：

```bash
docker compose -f observability/docker-compose.yml up
```

启动两个 Spring Boot 服务后访问：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Grafana 默认账号：`admin / admin`

Prometheus 在 Docker 中通过 `host.docker.internal:8080` 和 `host.docker.internal:8081` 抓取本机服务。

## 学习路线

### 当前已实现

- REST API 与常用 Web 注解
- Jakarta Validation 参数校验
- `ProblemDetail` 统一错误响应
- Spring Security Basic 与 `@PreAuthorize`
- OpenFeign 服务间调用
- Resilience4j 熔断降级
- Spring Cloud Gateway 路由、过滤器、认证透传、限流、fallback
- Caffeine 本地缓存
- AOP 自定义注解切面
- `@Async` 异步任务
- `@Scheduled` 定时任务
- Spring Event 事件发布与监听
- Actuator、Micrometer、Prometheus、Grafana
- Sentry 异常上报
- SpringDoc OpenAPI / Swagger UI

### 已补充的可选专题

- Nacos 注册中心/配置中心：已补充本地 Docker Compose、版本基线、可选 Maven profile、`application-nacos.yml`、服务注册发现、配置中心接入和面试重点；默认运行路径不依赖 Nacos。
- Gateway 服务发现路由：`gateway-service` 默认使用 localhost 静态路由，`nacos` profile 下切换为 `lb://catalog-service` 和 `lb://order-service`。

### 后续计划

当前项目已经覆盖 Spring Boot 3 常用组件。后续面向资深面试准备时，扩展重点放在微服务治理、可观测性、工程化、底层机制和技术取舍，不单纯堆依赖。

#### P0：优先补充

- Nacos 深化：已完成 `nacos` profile、服务注册发现、启动期配置中心读取；后续补动态刷新、namespace/group 多环境隔离和 Testcontainers 集成测试。
- Spring Cloud Gateway 深化：已完成 `gateway-service`、静态/Nacos 路由、过滤器、鉴权透传、限流和 fallback；后续可补灰度路由、跨域和更贴近生产的分布式限流。
- 链路追踪：补充 Micrometer Tracing + OpenTelemetry/Zipkin/Tempo，并在日志中输出 traceId/spanId。
- RestClient / `@HttpExchange`：补充 Spring 原生 HTTP client 示例，用于和 OpenFeign 做选型对比。
- OAuth2 Resource Server / JWT：在 Basic Auth 之外补充 JWT 资源服务器示例，提升 Security 面试覆盖面。
- 自动配置原理：新增 `demo-spring-boot-starter` 或 `demo-autoconfigure` 模块，演示 starter、条件装配和配置绑定。
- Resilience4j 深化：补充 Retry、RateLimiter、Bulkhead、TimeLimiter，说明不同治理策略边界。
- Testcontainers / 集成测试：为 Nacos、Gateway 或外部组件补充可重复集成测试。

#### P1：建议补充

- Docker 镜像与部署：补充 Dockerfile 或 Spring Boot build image、优雅停机、readiness/liveness 探针。
- Java 21 虚拟线程：增加 `virtual-thread` profile，对比传统线程池和 `@Async`。
- Sentinel：作为阿里系专题补充，和 Resilience4j 对比限流、熔断、热点参数。
- 结构化日志：补充 JSON log、MDC、traceId、错误码和请求日志脱敏。
- API 治理：补充 API versioning、统一错误码、OpenAPI 分组和接口兼容策略。
- Spring Cloud Contract：补充服务间契约测试，降低 provider/consumer 变更风险。

#### P2：路线保留

- Kafka、RabbitMQ、RocketMQ：先保留学习路线，重点理解投递语义、幂等、重试、顺序消息和死信队列；当前不引入运行依赖。
- Native Image / AOT：作为 Spring Boot 3 亮点补充文档，暂不作为默认构建链路。
- Kubernetes：先补部署和探针说明，不急于维护完整 K8s YAML。

详见：

- [使用说明](docs/USAGE.md)
- [实施文档](docs/IMPLEMENTATION.md)
- [Nacos 补充专题](docs/nacos-playbook.md)
- [Spring Boot 3 面试补充路线](docs/interview-roadmap.md)
- [后续任务计划 Prompt 索引](docs/task-plans/README.md)
- [消息队列后续计划](docs/messaging-roadmap.md)
