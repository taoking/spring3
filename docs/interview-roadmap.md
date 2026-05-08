# Spring Boot 3 面试补充路线

## 已覆盖内容

当前项目已经覆盖 Web MVC、Validation、ProblemDetail、Security、OpenFeign、Resilience4j、Caffeine、AOP、Async、Scheduled、Spring Event、Actuator、Micrometer、Prometheus、Grafana、Sentry、OpenAPI 和测试。

## 建议优先补充

每个专题的可执行计划和后续任务 prompt 见：[后续任务计划 Prompt 索引](task-plans/README.md)。

| 优先级 | 专题 | 原因 | 当前处理 |
| --- | --- | --- | --- |
| P0 | Nacos 注册中心/配置中心 | 微服务面试高频，常和 Spring Cloud Alibaba、OpenFeign、配置刷新一起问 | 已完成可选 `nacos` profile、服务注册发现、启动期配置中心读取；后续深化动态刷新和环境隔离 |
| P0 | Spring Cloud Gateway | 网关路由、过滤器、鉴权透传、限流、fallback 常在微服务架构题出现 | 已完成独立 `gateway-service`，覆盖静态/Nacos 路由、请求审计、认证透传、本地限流和 fallback |
| P0 | 链路追踪 | 资深面试会问 metrics/logs/traces 三件套，不只问 Prometheus | 当前只有指标，后续补 Micrometer Tracing + OpenTelemetry/Zipkin/Tempo |
| P0 | RestClient / `@HttpExchange` | Spring 原生 HTTP client 是 OpenFeign 的重要对比项 | 后续补一套调用示例并沉淀选型说明 |
| P0 | OAuth2 Resource Server / JWT | Security 面试通常会从 Basic 延伸到 JWT、OAuth2、鉴权边界 | 当前只有 Basic Auth，后续补 JWT resource server |
| P0 | Spring Boot 自动配置原理 | `@SpringBootApplication`、条件装配、starter、配置绑定几乎必问 | 代码已使用，建议补一篇源码阅读笔记 |
| P0 | 自定义 starter / autoconfigure | 能把自动配置原理从口头解释落到代码 | 后续新增 `demo-spring-boot-starter` 或 `demo-autoconfigure` 模块 |
| P0 | AOP 与事务边界 | 代理类型、自调用失效、注解生效条件是高频陷阱 | 当前无数据库事务，可用 AOP 示例延伸说明 |
| P0 | Spring MVC 请求链路 | DispatcherServlet、Filter、Interceptor、ControllerAdvice、MessageConverter | 当前代码已覆盖核心入口 |
| P0 | Resilience4j 深化 | Retry、RateLimiter、Bulkhead、TimeLimiter 和 CircuitBreaker 边界常被追问 | 当前只有熔断/fallback，后续补完整治理矩阵 |
| P1 | Sentinel | 阿里系技术栈高频，和 Resilience4j 对比限流、熔断、热点参数 | 后续作为可选专题，不影响默认运行 |
| P1 | 容器化与探针 | Docker、镜像分层、readiness/liveness、优雅停机 | 当前已有 Actuator probes 和 Docker Compose |
| P1 | Java 21 与虚拟线程 | Spring Boot 3 常被追问 Java 17/21 升级收益 | 后续可加一个虚拟线程配置示例 |
| P1 | 结构化日志 | JSON log、MDC、traceId、错误码、日志脱敏是线上排障基本功 | 后续补 logback JSON profile 和请求日志规范 |
| P1 | API 治理 | API versioning、统一错误码、OpenAPI 分组、接口兼容性体现长期维护能力 | 后续补文档和少量代码示例 |
| P1 | Spring Cloud Contract | 微服务 provider/consumer 变更风险控制 | 后续在 order/catalog 间补契约测试 |
| P2 | 消息队列 | Kafka/RabbitMQ/RocketMQ 的投递语义、幂等、重试、顺序消息 | 只保留路线，不引入运行依赖 |
| P2 | Testcontainers | 集成测试、外部依赖隔离、CI 可重复性 | 后续可用于 Nacos/Gateway 等可选专题 |
| P2 | Native Image/AOT | Spring Boot 3 亮点之一，适合了解限制和收益 | 后续可加文档，不必默认构建 |
| P2 | Kubernetes | 部署、探针、滚动发布、配置注入会被问，但本项目不急于维护完整 YAML | 先补部署说明，后续视需要加示例 |

## 面试复盘清单

- 能说清一个请求从 HTTP 入口到 Controller、Service、Feign、fallback、统一异常、指标上报的完整链路。
- 能解释 starter 为什么能自动生效，以及 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 的作用。
- 能解释 `@ConfigurationProperties` 和 `@Value` 的差异，什么时候需要校验和动态刷新。
- 能说明 Spring Security filter chain、认证、授权、CSRF/CORS、方法级权限的边界。
- 能说明 Feign 超时、重试、熔断、fallback、负载均衡分别在哪一层处理。
- 能比较 OpenFeign、RestClient、WebClient、`@HttpExchange` 的适用场景。
- 能说明 Prometheus pull 模型、指标命名、label 基数、Grafana dashboard 的关系。
- 能说明 traceId/spanId、日志、指标、链路追踪如何一起用于排障。
- 能说明服务注册和配置中心在故障场景下的降级策略。
- 能说明 Gateway 在认证、鉴权、路由、限流、跨域、灰度上的职责边界。
- 能说明 JWT/OAuth2 Resource Server 的认证与授权流程，以及和 session 登录的差异。
- 能解释 Spring Boot starter 自动配置的触发条件和覆盖方式。
- 能说明测试分层：单元测试、MVC slice、集成测试、MockWebServer/Testcontainers。
