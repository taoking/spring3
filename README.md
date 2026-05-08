# Spring Boot 3 Learning Lab

这是一个用于长期学习、复盘和面试准备的 Spring Boot 3 示例项目。

项目不接入数据库和 Redis，重点演示 Spring Boot 3 常用组件、注解、配置、测试、监控和错误上报。

## 模块

- `common`：共享 DTO、AOP 注解等公共类型。
- `catalog-service`：商品服务 provider，默认端口 `8081`。
- `order-service`：订单服务 consumer，默认端口 `8080`，通过 OpenFeign 调用 `catalog-service`。

## 技术栈

- Java 21
- Spring Boot 3.5.14
- Spring Cloud 2025.0.2
- Maven 多模块 + Maven Wrapper
- Spring Web MVC、Validation、Security、OpenFeign、Resilience4j、Caffeine、Actuator、Micrometer Prometheus、Sentry、SpringDoc OpenAPI

## 快速启动

```bash
./mvnw test
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
```

默认账号：

- 普通用户：`user / user123`
- 管理员：`admin / admin123`

## 常用地址

- Order Swagger UI: `http://localhost:8080/swagger-ui.html`
- Catalog Swagger UI: `http://localhost:8081/swagger-ui.html`
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
- Caffeine 本地缓存
- AOP 自定义注解切面
- `@Async` 异步任务
- `@Scheduled` 定时任务
- Spring Event 事件发布与监听
- Actuator、Micrometer、Prometheus、Grafana
- Sentry 异常上报
- SpringDoc OpenAPI / Swagger UI

### 已补充的可选专题

- Nacos 注册中心/配置中心：已补充本地 Docker Compose、版本基线、接入计划和面试重点；默认运行路径不依赖 Nacos。

### 后续计划

Nacos、Kafka、RabbitMQ、RocketMQ 会作为后续专题补充。Nacos 已先补充本地环境和接入方案；消息队列目前只在文档中保留学习路线，不引入运行依赖。

详见：

- [使用说明](docs/USAGE.md)
- [实施文档](docs/IMPLEMENTATION.md)
- [Nacos 补充专题](docs/nacos-playbook.md)
- [Spring Boot 3 面试补充路线](docs/interview-roadmap.md)
- [消息队列后续计划](docs/messaging-roadmap.md)
