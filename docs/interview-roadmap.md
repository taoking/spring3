# Spring Boot 3 面试补充路线

## 已覆盖内容

当前项目已经覆盖 Web MVC、Validation、ProblemDetail、Security、OpenFeign、Resilience4j、Caffeine、AOP、Async、Scheduled、Spring Event、Actuator、Micrometer、Prometheus、Grafana、Sentry、OpenAPI 和测试。

## 建议优先补充

| 优先级 | 专题 | 原因 | 当前处理 |
| --- | --- | --- | --- |
| P0 | Nacos 注册中心/配置中心 | 微服务面试高频，常和 Spring Cloud Alibaba、OpenFeign、配置刷新一起问 | 已新增专题手册和本地 Docker 配置，后续做可选 profile |
| P0 | Spring Boot 自动配置原理 | `@SpringBootApplication`、条件装配、starter、配置绑定几乎必问 | 代码已使用，建议补一篇源码阅读笔记 |
| P0 | AOP 与事务边界 | 代理类型、自调用失效、注解生效条件是高频陷阱 | 当前无数据库事务，可用 AOP 示例延伸说明 |
| P0 | Spring MVC 请求链路 | DispatcherServlet、Filter、Interceptor、ControllerAdvice、MessageConverter | 当前代码已覆盖核心入口 |
| P1 | Spring Cloud Gateway | 网关路由、过滤器、鉴权、限流、跨域常在微服务岗位出现 | 后续可加独立 gateway-service |
| P1 | Sentinel | 阿里系技术栈高频，和 Resilience4j 对比限流、熔断、热点参数 | 后续作为可选专题，不影响默认运行 |
| P1 | 链路追踪 | Micrometer Tracing、OpenTelemetry、TraceId/MDC、日志关联 | 当前只有指标，后续可补 tracing compose |
| P1 | 容器化与探针 | Docker、镜像分层、readiness/liveness、优雅停机 | 当前已有 Actuator probes 和 Docker Compose |
| P1 | Java 21 与虚拟线程 | Spring Boot 3 常被追问 Java 17/21 升级收益 | 后续可加一个虚拟线程配置示例 |
| P2 | 消息队列 | Kafka/RabbitMQ/RocketMQ 的投递语义、幂等、重试、顺序消息 | 只保留路线，不引入运行依赖 |
| P2 | Testcontainers | 集成测试、外部依赖隔离、CI 可重复性 | 后续可用于 Nacos/Gateway 等可选专题 |
| P2 | Native Image/AOT | Spring Boot 3 亮点之一，适合了解限制和收益 | 后续可加文档，不必默认构建 |

## 面试复盘清单

- 能说清一个请求从 HTTP 入口到 Controller、Service、Feign、fallback、统一异常、指标上报的完整链路。
- 能解释 starter 为什么能自动生效，以及 `@ConditionalOnClass`、`@ConditionalOnMissingBean` 的作用。
- 能解释 `@ConfigurationProperties` 和 `@Value` 的差异，什么时候需要校验和动态刷新。
- 能说明 Spring Security filter chain、认证、授权、CSRF/CORS、方法级权限的边界。
- 能说明 Feign 超时、重试、熔断、fallback、负载均衡分别在哪一层处理。
- 能说明 Prometheus pull 模型、指标命名、label 基数、Grafana dashboard 的关系。
- 能说明服务注册和配置中心在故障场景下的降级策略。
- 能说明测试分层：单元测试、MVC slice、集成测试、MockWebServer/Testcontainers。
