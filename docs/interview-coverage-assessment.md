# 资深面试覆盖度检查报告

## 检查目标

以资深 Java 架构师、技术面试官和工程教练视角，检查当前 Spring Boot 3 学习项目的知识覆盖度、使用深度、面试追问承压能力，并给出后续补齐计划。

本报告不改变项目边界：默认 profile 仍保持轻量，不强制依赖数据库、Redis、Nacos、Sentinel、Kafka、RabbitMQ、Zipkin 或 Kubernetes。数据库和 Redis 相关内容作为面试设计专题或后续可选 profile 处理。

## 总体判断

当前项目已经不是简单 demo，而是一个较完整的 Spring Boot 3 面试训练仓库。它的强项是组件覆盖广、profile 隔离清楚、很多专题有自动化测试和使用说明。当前主要短板不在“缺组件”，而在以下几个方向：

- 底层机制还需要更系统：Spring Bean 生命周期、自动配置加载顺序、AOP 代理、MVC 请求链路、Security Filter Chain、JVM 诊断。
- 生产追问还需要更深：Gateway 灰度和分布式限流、Nacos 动态刷新和故障降级、Kafka lag/rebalance/retry topic、RabbitMQ confirm/manual ack/prefetch。
- 工程负责人视角还可以增强：质量门禁、架构规则、依赖安全、SBOM、镜像扫描、发布回滚和故障复盘模板。
- 部分高频面试项目前是设计型覆盖：数据一致性、Redis 仍不接入外部依赖；Native Image 已完成 `catalog-service` buildpacks 镜像和 health check，但 `order-service`、`gateway-service` 仍需后续逐项验证。

## 评分概览

| 维度 | 评分 | 判断 |
| --- | --- | --- |
| Spring Boot 3 组件广度 | 4.5 / 5 | 覆盖 Web、Security、Feign、Gateway、Actuator、AOP、starter、OpenAPI、Sentry、MQ、Nacos、Sentinel、AOT 等 |
| 可运行示例深度 | 4 / 5 | 多数能力有代码、profile、测试和使用说明，少数生产化能力仍是文档设计 |
| 资深面试追问承压 | 3.5 / 5 | 能覆盖常规追问，底层机制、故障复盘和生产取舍还可继续加厚 |
| 生产化表达能力 | 3.5 / 5 | 已有观测、告警、K8s、CI 基线，但分布式限流、动态配置、发布治理、质量门禁还需补齐 |
| Java 基础和 JVM 诊断 | 2.5 / 5 | 有 Java 21 虚拟线程入口，但线程池、JFR、GC、内存和锁诊断还不够系统 |
| 数据和缓存专题 | 3 / 5 | 数据一致性和 Redis 已有设计文档，但没有真实 DB/Redis 实战 profile |

## 覆盖度明细

| 领域 | 当前资产 | 使用深度 | 面试追问风险 | 补齐建议 |
| --- | --- | --- | --- | --- |
| Spring Boot 核心 | 多模块、配置绑定、starter、autoconfigure | 较深 | 自动配置顺序、条件装配冲突、Bean 覆盖、失败分析 | 补 Spring Boot 启动链路和 Bean 生命周期专题 |
| Web MVC / API | Controller、Validation、ProblemDetail、OpenAPI、版本接口 | 较深 | Filter、Interceptor、ArgumentResolver、MessageConverter 顺序 | 补一张请求链路图和关键扩展点说明 |
| AOP | `@DemoLog` 自定义 starter | 中等偏深 | JDK/CGLIB、self-invocation、切面顺序、事务代理 | 补 AOP + `@Transactional` 失效场景专题 |
| Security / OAuth2 | Basic、JWT profile、roles/scope、JWK 配置入口 | 中等偏深 | token 吊销、JWK rotation、audience、服务间 token | 增加 Mock JWK/IdP 示例或验收说明 |
| 服务调用 | OpenFeign、RestClient、Contract、MockWebServer | 较深 | 超时叠加、连接池、重试位置、负载均衡、幂等重试 | 补 `@HttpExchange` 和连接池参数说明 |
| Resilience4j | Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead | 较深 | 异常分类、熔断恢复、线程池隔离、指标告警 | 补异常分类表和告警阈值 |
| Gateway | 路由、过滤器、认证透传、限流、fallback、CORS、灰度路由 | 中等偏深 | 动态路由、分布式限流、权重灰度、职责边界 | 收口测试，补生产流量治理专题 |
| Nacos | 注册发现、配置中心启动期读取 | 中等 | 动态刷新、namespace/group、配置优先级、Nacos 故障 | 补动态刷新和多环境隔离专题 |
| Sentinel | QPS、热点参数、慢调用熔断 | 中等 | Dashboard、规则持久化、集群限流、和 Resilience4j 边界 | 补规则持久化和生产配置说明 |
| Kafka | producer/consumer、manual ack、eventId 幂等、顺序、DLT、Testcontainers | 中等偏深 | lag、rebalance、retry topic、producer transaction、EOS 边界 | 建立 Kafka 深化专题模块 |
| RabbitMQ | exchange/queue/binding、listener retry、DLQ、幂等、Testcontainers | 中等 | publisher confirm、return callback、manual ack、prefetch、堆积 | 建立 RabbitMQ 生产语义补充 |
| Observability | Actuator、Prometheus、Grafana、Zipkin、JSON log、Sentry、alert rules | 中等偏深 | OTel Collector、采样、SLO、label 基数、日志查询 | 补排障演练和 dashboard/alert 验收 |
| Docker / K8s | Dockerfile、Compose、K8s YAML、probe、资源限制 | 中等 | Ingress、HPA、PDB、ServiceMonitor、GitOps、回滚 | 补生产化 K8s 目录或专题 |
| Test / CI | 单元测试、MVC 测试、Contract、Testcontainers、GitHub Actions | 中等偏深 | 覆盖率、静态扫描、依赖漏洞、架构规则 | 补工程质量门禁专题 |
| Java 21 / 并发 | virtual-thread profile、线程观察接口 | 入门到中等 | pinned thread、线程池隔离、JFR、jstack、GC、MDC 传播 | 建立 JVM 并发诊断专题 |
| Native / AOT | `catalog-service` AOT、buildpacks native 镜像、RuntimeHints、health check | 中级 | `order-service`/`gateway-service` native、SpringDoc/Sentry/Feign 深度验证 | 后续扩展 consumer 和 gateway |
| 数据一致性 | 设计文档、outbox/inbox、幂等、补偿 | 设计型覆盖 | 无真实事务代码时容易被追问“是否落地过” | 保持设计专题，后续如改变边界再加 DB profile |
| Redis / 缓存 | Caffeine 代码、Redis 设计专题 | 设计型覆盖 | 分布式锁、缓存一致性、热点 key 无实战 profile | 保持设计专题，后续如改变边界再加 Redis profile |

## 面试追问压力测试

| 专题 | 一问 | 二问 / 三问 | 当前项目能否支撑 | 风险 |
| --- | --- | --- | --- | --- |
| Spring Boot 自动配置 | starter 为什么能自动生效 | `AutoConfiguration.imports`、条件装配、用户 Bean 覆盖、加载顺序 | 能支撑，有 starter 代码和测试 | 需要补启动链路和源码阅读图 |
| MVC 请求链路 | 一个请求如何到 Controller | Filter、Interceptor、ArgumentResolver、MessageConverter、异常处理顺序 | 能支撑大部分 | 请求链路图还不够显性 |
| AOP | `@DemoLog` 为什么生效 | 代理类型、自调用失效、切面顺序、事务注解失效 | 能讲 AOP，事务只能设计型回答 | 缺事务实战代码 |
| Security | JWT 如何校验 | JWK rotation、issuer/audience、token 吊销、网关和服务是否都校验 | 能支撑资源服务器基线 | Mock IdP/JWK 实测不足 |
| Feign / RestClient | 超时和 fallback 怎么配 | retry 在哪层、连接池、负载均衡、trace header 传播 | 支撑较好 | `@HttpExchange` 还没落地 |
| Resilience4j | 熔断和重试区别 | 两者顺序、异常分类、half-open、TimeLimiter 线程上下文 | 支撑较好 | 告警阈值和生产参数需加厚 |
| Gateway | 为什么鉴权不只放网关 | CORS、灰度、限流维度、动态路由、fallback 边界 | 支撑中等偏深 | 分布式限流和动态路由仍是设计型 |
| Nacos | 注册中心故障怎么办 | 本地缓存、健康检查、配置刷新失败、多环境隔离 | 支撑基础 | 动态刷新和 namespace/group 需补 |
| Kafka | 如何保证消息不丢不重 | producer ack、幂等生产、事务、offset、DLT、rebalance、lag | 支撑中等偏深 | retry topic、transaction、lag 面板需补 |
| RabbitMQ | ack、nack、DLQ 怎么工作 | confirm、return callback、manual ack、prefetch、堆积排查 | 支撑基础到中等 | confirm/manual ack 需补 |
| Observability | 接口变慢如何定位 | RED 指标、trace、日志、Sentry、采样、label 基数 | 能支撑中等 | 缺完整故障演练记录 |
| K8s | readiness 和 liveness 区别 | startupProbe、滚动发布、优雅停机、PDB、HPA | 支撑基础 | Ingress/HPA/PDB/ServiceMonitor 需补 |
| JVM | CPU 高怎么排查 | jstack、JFR、jcmd、GC log、锁竞争、线程池队列 | 支撑不足 | 需要单独专题 |
| Redis | 缓存一致性怎么做 | 穿透/击穿/雪崩、分布式锁、hot key、大 key | 设计型支撑 | 没有实战 profile |
| 数据一致性 | 下单成功但发消息失败怎么办 | outbox、inbox、补偿、对账、事务消息边界 | 设计型支撑 | 没有真实 DB profile |

## 补齐计划

### P0：先补面试硬短板

| 顺序 | 任务 | 目标 | 示例内容 | 验收标准 |
| --- | --- | --- | --- | --- |
| 0 | 收口当前基线 | 当前工作区存在未提交的 Gateway、OAuth2、观测性和专题文档改动，先保证可编译、可测试、可回滚 | 跑 Gateway 测试、核心 Maven 测试、Prometheus compose config，补齐使用说明 | `git status` 清晰，关键测试通过，文档和代码一致 |
| 1 | Kafka 深化专题 | 从“会用 Spring Kafka”提升到“能解释生产语义和故障排查” | retry topic、consumer lag、rebalance、producer transaction、partition skew、Schema Registry 设计 | 至少 1 个可跑测试或脚本，至少 15 个 Kafka 追问和回答要点 |
| 2 | RabbitMQ 生产语义 | 补齐业务 MQ 高频追问 | publisher confirm、return callback、manual ack/nack、prefetch、延迟队列、堆积排查 | 文档能区分 confirm 和 consumer ack，最好有 Testcontainers 验证 |
| 3 | Spring 内核链路 | 把 starter、AOP、MVC、Security 串成底层机制图 | 启动流程、Bean 生命周期、自动配置、请求链路、Filter Chain、AOP 代理 | 每个链路有图或步骤说明，能回答至少 20 个 Spring 追问 |
| 4 | JVM / 并发诊断 | 弥补资深 Java 基础和线上排障能力 | 线程池、CompletableFuture、虚拟线程 pinned、MDC 传播、JFR、jstack、jcmd、GC log | 给出 CPU 高、线程阻塞、内存上涨、接口超时四类排查路径 |
| 5 | Nacos 深化 | 从“能注册发现”提升到“能讲配置治理和故障降级” | namespace/group、动态刷新、配置优先级、Nacos 不可用降级、灰度配置 | 有 profile 使用说明、故障矩阵和追问清单 |

### P1：补生产化和工程负责人视角

| 顺序 | 任务 | 目标 | 示例内容 | 验收标准 |
| --- | --- | --- | --- | --- |
| 6 | Gateway 生产治理 | 网关专题从功能路由升级到生产流量治理 | 权重灰度、Header 灰度、CORS、分布式限流、动态路由风险、限流指标 | Gateway 测试通过，文档明确网关和服务职责边界 |
| 7 | Observability 演练 | 把指标、日志、链路、Sentry 组合成故障闭环 | p95 升高、下游 500、Gateway 429、Kafka lag、trace sampling、SLO | 至少 5 条 PromQL、3 条告警、4 个 runbook |
| 8 | 工程质量门禁 | 展示技术负责人对质量和风险的控制能力 | JaCoCo、ArchUnit、SpotBugs/Checkstyle、Dependency-Check、SBOM、镜像扫描 | 区分默认 CI 和手动深度检查，至少 5 条架构规则候选 |
| 9 | OAuth2 真实 IdP 验证 | 把本地 JWT 学习模式扩展到真实授权服务器模型 | Mock JWK Set、issuer-uri、audience、client_credentials、token relay | 不提交真实密钥，至少覆盖无 token、错 token、权限不足、JWK 配置 |
| 10 | K8s 生产化 | 从最小 YAML 升级到生产部署语义 | Ingress、HPA、PDB、ServiceMonitor、Helm/Kustomize、滚动回滚 | 不要求真实集群，但要有 `kubectl` 验证命令和限制说明 |

### P2：按面试方向选择性补充

| 顺序 | 任务 | 目标 | 示例内容 | 验收标准 |
| --- | --- | --- | --- | --- |
| 11 | Redis 可选实战 profile | 如果后续愿意改变边界，补 Redis 实战 | cache-aside、分布式锁、限流计数、hot key 保护 | 默认 profile 不依赖 Redis，Redis profile 可独立启动和测试 |
| 12 | 数据库可选实战 profile | 如果后续愿意改变边界，补事务和 outbox 实战 | H2/PostgreSQL、事务传播、幂等表、outbox/inbox、补偿任务 | 默认 profile 不依赖 DB，DB profile 能跑完整一致性测试 |
| 13 | Native Image 完整验证 | 完成 Spring Boot 3 AOT 到 native binary 的闭环 | GraalVM native-image、buildpacks、启动耗时、内存对比、RuntimeHints | 已完成 `catalog-service` buildpacks native 镜像和 health check，后续扩展到 consumer/gateway |
| 14 | 性能压测专题 | 把“功能可用”升级为“容量和瓶颈可解释” | wrk/k6/Gatling、p95/p99、连接池、线程池、限流、JVM 指标 | 有压测脚本、基线结果、瓶颈分析和调参记录 |
| 15 | RocketMQ 设计专题 | 覆盖国内中间件面试常见点 | tag、顺序消息、延迟消息、事务半消息、消费重试、DLQ | 可先文档化，不强制接入运行依赖 |

## 建议执行顺序

1. 收口当前未提交基线，保证 Gateway、OAuth2、观测性和新增文档不破坏主干。
2. 做 Kafka 深化，因为当前用户学习目标已经明确，并且 Kafka 是资深面试高频。
3. 做 Spring 内核链路和 JVM 诊断，补齐资深 Java 基础。
4. 做 RabbitMQ、Nacos、Gateway、Observability 的生产追问增强。
5. 做工程质量门禁和 K8s 生产化，补技术负责人视角。
6. 根据是否继续坚持“不接 DB/Redis”，决定是否只保留设计专题，还是新增可选实战 profile。

## 回答策略

面试时需要主动区分三类能力：

- 已实现可演示：可以指向代码、profile、测试和命令，例如 Feign/RestClient、Resilience4j、Gateway、Kafka/RabbitMQ 基线、Contract、Testcontainers、Prometheus。
- 已设计可解释：当前不接 DB/Redis，但数据一致性、Redis 缓存治理已有设计文档和故障矩阵。
- 受工具链限制：本机 Native binary 当前仍需要 GraalVM `native-image`；buildpacks 已完成 `catalog-service` 可运行验证，但不要宣称所有服务 native 已生产可用。

## 不建议继续做的事

- 不建议为了“组件更多”盲目加入 Elasticsearch、Seata、ShardingSphere、XXL-Job、Dubbo 等新依赖，除非后续面试方向明确。
- 不建议在默认 profile 接入 DB、Redis 或 MQ，这会破坏学习项目的轻量启动体验。
- 不建议只写组件介绍不写失败场景，资深面试真正追问的是边界、取舍和排障。
- 不建议把所有内容一次性堆到 README，README 应保持导航，细节放到专题文档。
