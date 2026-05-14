# Spring Boot 3 面试补充路线

## 已覆盖内容

当前项目已经覆盖 Web MVC、Validation、ProblemDetail、Security、OpenFeign、Resilience4j、Caffeine、AOP、Async、Scheduled、Spring Event、Actuator、Micrometer、Prometheus、Grafana、Micrometer Tracing、Zipkin、Sentry、OpenAPI、Spring Cloud Contract、RabbitMQ 可选示例和测试、Native Image / AOT 专题文档和 `catalog-service` AOT 基线，以及 Kubernetes 最小部署 YAML。

## 建议优先补充

每个专题的可执行计划和后续任务 prompt 见：[后续任务计划 Prompt 索引](task-plans/README.md)。

2026-05-15 已按资深架构师 / 面试官视角补充整体评估和执行计划，见：[资深面试覆盖补齐计划](task-plans/19-interview-expansion.md)。该计划把当前项目从“组件覆盖”进一步拆到生产边界、故障推演、设计取舍、底层机制和工程门禁。

| 优先级 | 专题 | 原因 | 当前处理 |
| --- | --- | --- | --- |
| P0 | 数据一致性与事务边界 | 资深 Java 面试硬缺口，常从 MQ 幂等、outbox/inbox、事务传播和隔离级别追问 | 当前项目不接数据库，先补设计型 playbook、故障矩阵和面试追问 |
| P0 | Redis 与缓存治理 | Redis 高频追问覆盖缓存一致性、分布式锁、热点 key 和限流计数 | 当前仅有 Caffeine，本阶段先补 Redis 设计专题，不污染默认运行路径 |
| P0 | Nacos 注册中心/配置中心 | 微服务面试高频，常和 Spring Cloud Alibaba、OpenFeign、配置刷新一起问 | 已完成可选 `nacos` profile、服务注册发现、启动期配置中心读取；后续深化动态刷新和环境隔离 |
| P0 | Spring Cloud Gateway | 网关路由、过滤器、鉴权透传、限流、fallback 常在微服务架构题出现 | 已完成独立 `gateway-service`，覆盖静态/Nacos 路由、请求审计、认证透传、本地限流和 fallback |
| P0 | 链路追踪 | 资深面试会问 metrics/logs/traces 三件套，不只问 Prometheus | 已完成 Micrometer Tracing + Zipkin 基线、日志 traceId/spanId、Feign W3C trace context 传播；后续可补 Tempo / OpenTelemetry Collector 和采样策略 |
| P0 | RestClient / `@HttpExchange` | Spring 原生 HTTP client 是 OpenFeign 的重要对比项 | 已完成 RestClient 调用模式、超时/认证/fallback 复用、自动化测试和选型对比；后续可补 `@HttpExchange` 接口式示例 |
| P0 | OAuth2 Resource Server / JWT | Security 面试通常会从 Basic 延伸到 JWT、OAuth2、鉴权边界 | 已完成 `jwt` profile、Bearer token 校验、roles/scope 映射、权限测试和服务间 Basic 取舍说明 |
| P0 | Spring Boot 自动配置原理 | `@SpringBootApplication`、条件装配、starter、配置绑定几乎必问 | 已通过 observability autoconfigure/starter 落地，后续可补源码阅读笔记 |
| P0 | 自定义 starter / autoconfigure | 能把自动配置原理从口头解释落到代码 | 已完成 `demo-observability-autoconfigure` 与 `demo-observability-spring-boot-starter`，覆盖自动配置注册、条件装配、属性绑定和用户 Bean 覆盖 |
| P0 | AOP 与事务边界 | 代理类型、自调用失效、注解生效条件是高频陷阱 | 当前无数据库事务，可用 AOP 示例延伸说明 |
| P0 | Spring MVC 请求链路 | DispatcherServlet、Filter、Interceptor、ControllerAdvice、MessageConverter | 当前代码已覆盖核心入口 |
| P0 | Resilience4j 深化 | Retry、RateLimiter、Bulkhead、TimeLimiter 和 CircuitBreaker 边界常被追问 | 已完成完整治理矩阵、触发参数、fallback 标识和 Prometheus 指标验证；后续可补异常分类和告警规则 |
| P1 | Sentinel | 阿里系技术栈高频，和 Resilience4j 对比限流、熔断、热点参数 | 已完成可选 `sentinel` profile、本地规则、QPS 限流、热点参数、慢调用熔断探针和测试 |
| P1 | 容器化与探针 | Docker、镜像分层、readiness/liveness、优雅停机 | 已完成两个业务服务镜像、应用 Compose、readiness/liveness、优雅停机和 Prometheus 服务名抓取 |
| P1 | Java 21 与虚拟线程 | Spring Boot 3 常被追问 Java 17/21 升级收益 | 已完成 `virtual-thread` profile、请求线程和 `@Async` 线程观察接口；后续可补 pinned thread 诊断 |
| P1 | 结构化日志 | JSON log、MDC、traceId、错误码、日志脱敏是线上排障基本功 | 已完成 `json-logging` profile、请求日志过滤器、requestId 响应头、敏感头脱敏测试；后续补日志平台查询样例 |
| P1 | API 治理 | API versioning、统一错误码、OpenAPI 分组、接口兼容性体现长期维护能力 | 已完成 ProblemDetail 错误码、订单 v1/v2、旧接口废弃头、OpenAPI 分组和测试 |
| P1 | Spring Cloud Contract | 微服务 provider/consumer 变更风险控制 | 已完成 catalog provider 契约、stubs jar、本地 Stub Runner consumer 测试，覆盖成功、404、500 三类响应 |
| P2 | 消息队列 | Kafka/RabbitMQ/RocketMQ 的投递语义、幂等、重试、顺序消息 | 已完成 RabbitMQ 可选 profile、订单事件生产/消费、eventId 幂等、重试和 DLQ；Kafka/RocketMQ 保留路线对比 |
| P2 | Testcontainers | 集成测试、外部依赖隔离、CI 可重复性 | 已完成 Gateway 下游容器集成测试和 GitHub Actions；后续可扩展到 Nacos |
| P2 | Native Image/AOT | Spring Boot 3 亮点之一，适合了解限制和收益 | 已补专题文档，`catalog-service` `process-aot` 通过；本机 `native:compile` 阻塞于缺少 GraalVM `native-image` |
| P2 | Kubernetes | 部署、探针、滚动发布、配置注入会被问，但本项目不急于维护完整 YAML | 已补最小 YAML，覆盖 Deployment、Service、ConfigMap、Secret 示例、Actuator probes、资源限制、滚动发布和优雅停机 |

## 面试复盘清单

- 能说清一个请求从 HTTP 入口到 Controller、Service、Feign、fallback、统一异常、指标上报的完整链路。
- 能解释 starter 为什么能自动生效，以及 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 的作用。
- 能解释 `@ConfigurationProperties` 和 `@Value` 的差异，什么时候需要校验和动态刷新。
- 能说明 Spring Security filter chain、认证、授权、CSRF/CORS、方法级权限的边界。
- 能说明 Feign 超时、重试、熔断、fallback、负载均衡分别在哪一层处理。
- 能说明 Resilience4j Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 分别解决什么问题，以及彼此不能替代的边界。
- 能比较 Sentinel 和 Resilience4j：Sentinel 更偏流量治理、热点参数和控制台规则，Resilience4j 更偏应用内轻量治理库。
- 能比较 OpenFeign、RestClient、WebClient、`@HttpExchange` 的适用场景。
- 能说明 Prometheus pull 模型、指标命名、label 基数、Grafana dashboard 的关系。
- 能说明 traceId/spanId、日志、指标、链路追踪如何一起用于排障。
- 能说明结构化日志字段设计、MDC、requestId、traceId/spanId 和敏感字段脱敏策略。
- 能说明错误码稳定性、API 版本兼容、废弃接口 Sunset 策略和 OpenAPI 分组方式。
- 能说明 Spring Cloud Contract 和 MockWebServer 的区别，以及 provider stubs 如何被 consumer CI 使用。
- 能说明 RabbitMQ exchange/queue/binding、ack/nack、listener retry、DLQ、publisher confirm 和消费幂等的边界，并能和 Kafka/RocketMQ 对比。
- 能说明 AOT 和 Native Image 的区别、收益、代价，以及反射、动态代理、SpringDoc、Sentry、Feign 在 native 场景下为什么要单独验证。
- 能说明服务注册和配置中心在故障场景下的降级策略。
- 能说明 Gateway 在认证、鉴权、路由、限流、跨域、灰度上的职责边界。
- 能说明 JWT/OAuth2 Resource Server 的认证与授权流程，以及和 session 登录的差异。
- 能说明 Kubernetes Deployment、Service、ConfigMap、Secret、readiness/liveness/startup probe、滚动发布和 graceful shutdown 如何配合。
- 能解释 Spring Boot starter 自动配置的触发条件和覆盖方式。
- 能说明测试分层：单元测试、MVC slice、集成测试、MockWebServer/Testcontainers。
