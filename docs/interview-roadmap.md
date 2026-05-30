# Spring Boot 3 面试补充路线

## 已覆盖内容

当前项目已经覆盖 Web MVC、Validation、ProblemDetail、Security、OpenFeign、Resilience4j、Caffeine、AOP、Async、Scheduled、Spring Event、Actuator、Micrometer、Prometheus、Grafana、Micrometer Tracing、Zipkin、Sentry、OpenAPI、Spring Cloud Contract、RabbitMQ 可选示例和测试、Native Image / AOT buildpacks 验证、`catalog-service` RuntimeHints 示例，以及 Kubernetes 最小部署 YAML。

## 建议优先补充

每个专题的可执行计划和后续任务 prompt 见：[后续任务计划 Prompt 索引](task-plans/README.md)。

2026-05-15 已按资深架构师 / 面试官视角补充整体评估和执行计划，见：[资深面试覆盖度检查报告](interview-coverage-assessment.md) 和 [资深面试覆盖补齐计划](task-plans/19-interview-expansion.md)。检查报告负责评分、追问压力测试和缺口分级，任务计划负责后续可执行 prompt。

| 优先级 | 专题 | 原因 | 当前处理 |
| --- | --- | --- | --- |
| P0 | 数据一致性与事务边界 | 资深 Java 面试硬缺口，常从 MQ 幂等、outbox/inbox、事务传播和隔离级别追问 | 已补 [数据一致性与事务边界专题](data-consistency-playbook.md) 和 [任务计划](task-plans/20-data-consistency.md)，不接数据库，仅沉淀设计、故障矩阵和面试追问 |
| P0 | Redis 与缓存治理 | Redis 高频追问覆盖缓存一致性、分布式锁、热点 key 和限流计数 | 已补 [Redis 与缓存治理专题](redis-cache-playbook.md) 和 [任务计划](task-plans/21-redis-cache.md)，不接 Redis，仅沉淀设计、故障矩阵和面试追问 |
| P0 | Nacos 注册中心/配置中心 | 微服务面试高频，常和 Spring Cloud Alibaba、OpenFeign、配置刷新一起问 | 已完成可选 `nacos` profile、服务注册发现、启动期配置中心读取；后续深化动态刷新和环境隔离 |
| P0 | Spring Cloud Gateway | 网关路由、过滤器、鉴权透传、限流、fallback 常在微服务架构题出现 | 已完成独立 `gateway-service`，覆盖静态/Nacos 路由、请求审计、认证透传、本地限流、fallback、CORS 和 `X-Canary` 灰度路由；已补 [Gateway 生产能力专题](gateway-production-playbook.md) |
| P0 | 链路追踪 / 可观测性生产化 | 资深面试会问 metrics/logs/traces 三件套，不只问 Prometheus | 已完成 Micrometer Tracing + Zipkin 基线、日志 traceId/spanId、Feign W3C trace context 传播；已补 [可观测性生产化专题](observability-production-playbook.md)、PromQL、告警规则和 runbook |
| P0 | RestClient / `@HttpExchange` | Spring 原生 HTTP client 是 OpenFeign 的重要对比项 | 已完成 RestClient 调用模式、超时/认证/fallback 复用、自动化测试和选型对比；后续可补 `@HttpExchange` 接口式示例 |
| P0 | OAuth2 Resource Server / JWT | Security 面试通常会从 Basic 延伸到 JWT、OAuth2、鉴权边界 | 已完成 `jwt` profile、Bearer token 校验、roles/scope 映射、权限测试和服务间 Basic 取舍说明；已补 [OAuth2 / JWT 生产化专题](security-oauth2-playbook.md)，预留 `issuer-uri`/`jwk-set-uri` 配置入口 |
| P0 | Spring Boot 自动配置原理 | `@SpringBootApplication`、条件装配、starter、配置绑定几乎必问 | 已通过 observability autoconfigure/starter 落地，后续可补源码阅读笔记 |
| P0 | 自定义 starter / autoconfigure | 能把自动配置原理从口头解释落到代码 | 已完成 `demo-observability-autoconfigure` 与 `demo-observability-spring-boot-starter`，覆盖自动配置注册、条件装配、属性绑定和用户 Bean 覆盖 |
| P0 | AOP 与事务边界 | 代理类型、自调用失效、注解生效条件是高频陷阱 | 当前无数据库事务，可用 AOP 示例延伸说明 |
| P0 | Spring MVC 请求链路 | DispatcherServlet、Filter、Interceptor、ControllerAdvice、MessageConverter | 当前代码已覆盖核心入口 |
| P0 | Resilience4j 深化 | Retry、RateLimiter、Bulkhead、TimeLimiter 和 CircuitBreaker 边界常被追问 | 已完成完整治理矩阵、触发参数、fallback 标识和 Prometheus 指标验证；后续可补异常分类和告警规则 |
| P1 | Sentinel | 阿里系技术栈高频，和 Resilience4j 对比限流、熔断、热点参数 | 已完成可选 `sentinel` profile、本地规则、QPS 限流、热点参数、慢调用熔断探针和测试 |
| P1 | 容器化与探针 | Docker、镜像分层、readiness/liveness、优雅停机 | 已完成两个业务服务镜像、应用 Compose、readiness/liveness、优雅停机和 Prometheus 服务名抓取 |
| P1 | Java 21 与虚拟线程 / JVM 诊断 | Spring Boot 3 常被追问 Java 17/21 升级收益、线程池和线上排障 | 已完成 `virtual-thread` profile、请求线程和 `@Async` 线程观察接口；已补 [JVM、并发和 Java 21 诊断专题](jvm-concurrency-playbook.md)，覆盖 pinned thread、线程池、JFR、jcmd、jstack、jmap 和 GC 排障 |
| P1 | 结构化日志 | JSON log、MDC、traceId、错误码、日志脱敏是线上排障基本功 | 已完成 `json-logging` profile、请求日志过滤器、requestId 响应头、敏感头脱敏测试；后续补日志平台查询样例 |
| P1 | API 治理 | API versioning、统一错误码、OpenAPI 分组、接口兼容性体现长期维护能力 | 已完成 ProblemDetail 错误码、订单 v1/v2、旧接口废弃头、OpenAPI 分组和测试 |
| P1 | Spring Cloud Contract | 微服务 provider/consumer 变更风险控制 | 已完成 catalog provider 契约、stubs jar、本地 Stub Runner consumer 测试，覆盖成功、404、500 三类响应 |
| P1 | 工程质量与 CI 门禁 | 资深工程负责人会被追问质量、依赖安全、架构规则和发布门禁 | 已补 [工程质量与 CI 门禁专题](engineering-quality-playbook.md)，覆盖 CI 分层、JaCoCo、静态扫描、依赖安全、SBOM、镜像扫描和 ArchUnit 规则候选 |
| P2 | 消息队列 | Kafka/RabbitMQ/RocketMQ 的投递语义、幂等、重试、顺序消息 | 已完成 RabbitMQ/Kafka 可选 profile 和 Testcontainers；已补 [消息队列生产语义专题](messaging-production-playbook.md)，覆盖 Kafka lag/rebalance/retry topic/transaction 边界、RabbitMQ confirm/manual ack/prefetch 和 RocketMQ 设计对比 |
| P2 | Testcontainers | 集成测试、外部依赖隔离、CI 可重复性 | 已完成 Gateway 下游容器集成测试和 GitHub Actions；后续可扩展到 Nacos |
| P2 | Native Image/AOT | Spring Boot 3 亮点之一，适合了解限制和收益 | 已补专题文档和 [Native Image 完整验证专题](native-image-verification-playbook.md)，`catalog-service` buildpacks native 镜像、容器启动和 health check 已通过；本机 `native:compile` 仍阻塞于缺少 GraalVM `native-image` |
| P2 | Kubernetes | 部署、探针、滚动发布、配置注入会被问，但本项目不急于维护完整平台 | 已补最小 YAML；已补 [Kubernetes 生产化专题](kubernetes-production-playbook.md)，覆盖 Ingress、HPA、PDB、ServiceMonitor、Secret 管理、镜像 tag/digest、GitOps、回滚和故障排查 |

## 面试复盘清单

- 能说清一个请求从 HTTP 入口到 Controller、Service、Feign、fallback、统一异常、指标上报的完整链路。
- 能解释 starter 为什么能自动生效，以及 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 的作用。
- 能解释 `@ConfigurationProperties` 和 `@Value` 的差异，什么时候需要校验和动态刷新。
- 能解释 `@Transactional` 失效原因、事务传播、隔离级别，以及为什么当前项目不宣称业务 exactly-once。
- 能说明 Spring Security filter chain、认证、授权、CSRF/CORS、方法级权限的边界。
- 能说明 `issuer-uri`、`jwk-set-uri`、JWK rotation、audience、JWT 吊销和 `client_credentials` 服务间调用。
- 能说明 Feign 超时、重试、熔断、fallback、负载均衡分别在哪一层处理。
- 能说明 Resilience4j Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead 分别解决什么问题，以及彼此不能替代的边界。
- 能比较 Sentinel 和 Resilience4j：Sentinel 更偏流量治理、热点参数和控制台规则，Resilience4j 更偏应用内轻量治理库。
- 能说明 Caffeine 与 Redis 的定位差异、缓存穿透/击穿/雪崩、分布式锁和热点 key 治理。
- 能比较 OpenFeign、RestClient、WebClient、`@HttpExchange` 的适用场景。
- 能说明 Prometheus pull 模型、指标命名、label 基数、Grafana dashboard 的关系。
- 能说明 traceId/spanId、日志、指标、链路追踪如何一起用于排障。
- 能说明结构化日志字段设计、MDC、requestId、traceId/spanId 和敏感字段脱敏策略。
- 能说明错误码稳定性、API 版本兼容、废弃接口 Sunset 策略和 OpenAPI 分组方式。
- 能说明 Spring Cloud Contract 和 MockWebServer 的区别，以及 provider stubs 如何被 consumer CI 使用。
- 能说明 RabbitMQ exchange/queue/binding、ack/nack、listener retry、DLQ、publisher confirm 和消费幂等的边界，并能和 Kafka/RocketMQ 对比。
- 能说明 outbox/inbox、幂等表、DLQ/DLT 重放、对账和补偿如何共同保证最终一致性。
- 能说明 AOT 和 Native Image 的区别、收益、代价，以及 Hibernate Validator RuntimeHints、反射、动态代理、SpringDoc、Sentry、Feign 在 native 场景下为什么要单独验证。
- 能说明服务注册和配置中心在故障场景下的降级策略。
- 能说明 Gateway 在认证、鉴权、路由、限流、跨域、灰度上的职责边界。
- 能说明 JWT/OAuth2 Resource Server 的认证与授权流程，以及和 session 登录的差异。
- 能说明 Kubernetes Deployment、Service、ConfigMap、Secret、readiness/liveness/startup probe、滚动发布和 graceful shutdown 如何配合。
- 能解释 Spring Boot starter 自动配置的触发条件和覆盖方式。
- 能说明测试分层：单元测试、MVC slice、集成测试、MockWebServer/Testcontainers。
- 能说明 CPU 高、线程阻塞、内存上涨、接口超时四类 JVM/并发排查路径。
- 能说明质量门禁分层、覆盖率局限、ArchUnit 架构规则、依赖漏洞和 SBOM 的作用。
