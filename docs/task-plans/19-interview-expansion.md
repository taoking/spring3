# 19 资深面试覆盖补齐计划

## 目标

以资深 Java 架构师、技术面试官和工程教练视角，评估当前 Spring Boot 3 学习项目的知识覆盖度、使用深度和面试追问承压能力，并制定后续补齐计划。

本计划不直接改变业务运行路径。默认约束仍然是：默认 profile 保持轻量，不接入数据库和 Redis，除非后续单独明确变更项目边界。对数据库、Redis 这类资深面试高频但当前项目不接入的内容，先以设计文档、故障矩阵、面试题库和可选 future profile 方式补齐。

## 当前结论

当前项目已经达到“组件覆盖较全面、可演示、可测试”的阶段。下一步的重点不是继续堆新依赖，而是补足资深面试更看重的内容：

- 生产边界：组件在真实生产中如何配置、观测、降级、限流和发布。
- 故障推演：失败发生在哪一层，如何发现、隔离、恢复和复盘。
- 设计取舍：为什么选择这个组件，什么时候不用它，有哪些替代方案。
- 底层机制：Spring Boot 自动配置、代理、线程、GC、消息投递语义、OAuth2 流程等。
- 工程门禁：测试分层、质量扫描、依赖安全、架构规则和 CI 可重复性。

## 覆盖度评估

| 领域 | 当前覆盖 | 深度判断 | 主要追问风险 |
| --- | --- | --- | --- |
| Spring Boot 核心 | Web、Validation、配置绑定、starter、autoconfigure | 较深 | 自动配置顺序、条件装配冲突、配置优先级、失败分析 |
| Spring MVC 请求链路 | Controller、ProblemDetail、ControllerAdvice、OpenAPI | 较深 | Filter/Interceptor/ArgumentResolver/MessageConverter 的链路细节 |
| AOP | `@DemoLog` starter | 中等偏深 | JDK/CGLIB、self-invocation、切面顺序、事务边界 |
| Security / JWT | Basic、Resource Server、roles/scope | 中等 | JWK、issuer、token 吊销、服务间 `client_credentials` |
| 服务调用 | OpenFeign、RestClient、MockWebServer、Contract | 较深 | 负载均衡、重试位置、超时叠加、连接池、WebClient 对比 |
| Resilience4j | Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead | 较深 | 异常分类、告警阈值、线程池隔离、熔断恢复策略 |
| Gateway | 路由、过滤器、鉴权透传、本地限流、fallback | 中等 | CORS、灰度、动态路由、分布式限流、网关与服务鉴权边界 |
| Nacos | 注册发现、配置中心启动期读取 | 中等 | 动态刷新、namespace/group、多环境、配置优先级、Nacos 故障降级 |
| Sentinel | 流控、热点参数、慢调用熔断 | 中等 | Dashboard、规则持久化、集群限流、与 Resilience4j 的边界 |
| Observability | Actuator、Prometheus、Grafana、Zipkin、JSON log、Sentry | 中等 | OTel Collector、采样、日志检索、告警、SLO、label 基数 |
| Kafka | profile、producer/consumer、manual ack、幂等、顺序、DLT | 中等偏深 | lag、rebalance、retry topic、producer transaction、EOS 边界 |
| RabbitMQ | exchange/queue、listener retry、DLQ、幂等 | 中等 | publisher confirm、return callback、manual ack、prefetch、堆积排查 |
| Docker / K8s | Dockerfile、Compose、最小 YAML、探针、资源限制 | 中等 | Ingress、HPA、Helm/Kustomize、GitOps、发布回滚 |
| Native / AOT | `catalog-service` AOT、文档 | 入门 | native binary、RuntimeHints、动态代理、第三方库兼容 |
| Java 21 / 并发 | 虚拟线程 profile、线程观察接口 | 入门到中等 | pinned thread、JFR、线程池参数、MDC 传播、锁竞争 |
| 数据一致性 | 当前不接数据库 | 明显缺口 | 事务、隔离级别、幂等表、outbox/inbox、分布式事务 |
| Redis / 分布式缓存 | 当前不接 Redis | 明显缺口 | 缓存击穿/穿透/雪崩、双写一致性、分布式锁、热点 key |
| 工程质量门禁 | Maven test、IT、GitHub Actions | 中等 | 覆盖率、静态扫描、依赖漏洞、SBOM、架构规则 |

## 执行原则

- 保持当前项目定位：Spring Boot 3 学习、复盘和面试准备，不把默认运行路径变成复杂生产平台。
- 对新增外部组件继续使用独立 Maven profile、Spring profile 或独立 Compose。
- 对数据库和 Redis 先做“面试设计专题”，不直接改变“不接数据库和 Redis”的项目边界。
- 每个后续专题必须包含：目标、场景、核心知识点、失败情况、验收标准、面试追问。
- 每个可执行专题至少保留一条自动化验证命令；纯文档专题要保留自检清单。

## P0 补齐计划

### 1. 数据一致性与事务边界

定位：资深 Java 面试硬缺口。当前不接数据库，但必须能讲清事务、幂等和消息一致性。

建议产物：

- `docs/data-consistency-playbook.md`
- `docs/task-plans/20-data-consistency.md`

内容范围：

- 本地事务、声明式事务、传播行为、隔离级别、锁和 MVCC。
- 接口幂等：幂等键、业务唯一键、状态机版本号、重复提交。
- 消息一致性：本地事务 + outbox、inbox、最终一致性、补偿任务。
- 分布式事务：2PC/TCC/Saga/事务消息的适用边界。
- 当前项目无 DB/Redis 时，Kafka/RabbitMQ 幂等为什么只能是演示级。

场景设计：

- 订单预览接口同步返回成功，但消息发送失败。
- 消息发送成功，消费者处理成功，但 offset/ack 提交失败。
- DLT/DLQ 修复后重放，如何避免重复副作用。
- 服务重启导致内存幂等失效，生产如何补齐。

验收标准：

- 文档能解释“业务 exactly-once”和“Kafka exactly-once”的区别。
- 能给出 outbox/inbox 表结构草图，即使当前不落库。
- 能画出一次 HTTP 请求、事务提交、事件落库、异步投递、消费幂等的时序。
- 至少列出 10 个面试追问及回答要点。

核心追问：

- `@Transactional` 为什么有时不生效？
- 事务提交后消息发送失败怎么办？
- 消费者重复消费如何保证不重复扣款或发券？
- Kafka 事务能不能保证数据库也 exactly-once？
- 你如何设计对账和补偿？

### 2. Redis 与缓存治理面试专题

定位：项目当前只有 Caffeine，本地缓存已覆盖，但 Redis 是资深 Java 高频。

建议产物：

- `docs/redis-cache-playbook.md`
- `docs/task-plans/21-redis-cache.md`

内容范围：

- Caffeine 与 Redis 的定位差异。
- 缓存穿透、击穿、雪崩、预热、过期抖动。
- 双写一致性：Cache Aside、延迟双删、订阅 binlog、最终一致性。
- 分布式锁：SET NX PX、Lua 释放、Redisson watchdog、锁续期风险。
- 热点 key、大 key、慢查询、内存淘汰、集群 hash slot。

场景设计：

- 商品详情缓存：本地缓存命中、Redis 命中、下游 catalog 回源。
- 热点 SKU 高并发查询，如何防击穿。
- 商品价格更新后缓存短期不一致如何接受和修复。
- 网关限流如果从本地限流升级为 Redis 分布式限流，需要哪些风险控制。

验收标准：

- 文档能解释本项目为什么当前不接 Redis，以及如果生产接入会放在哪些边界。
- 能对比 Caffeine、Redis、本地 + 分布式二级缓存。
- 能回答分布式锁不是银弹的原因。

核心追问：

- 缓存和数据库不一致怎么处理？
- 布隆过滤器解决什么问题，会带来什么误判？
- Redis 分布式锁什么情况下会失效？
- 大 key 和 hot key 如何定位和治理？

### 3. OAuth2 / JWT 生产化

定位：当前 JWT profile 是本地 HS256 学习模式，资深面试会追问真实 IdP 和服务间调用。

建议产物：

- 深化 `docs/task-plans/05-oauth2-jwt.md`
- 新增 `docs/security-oauth2-playbook.md`

内容范围：

- `issuer-uri`、`jwk-set-uri`、JWK rotation。
- `client_credentials` 服务间 token。
- 网关鉴权与服务侧鉴权的职责边界。
- token 吊销、短 token + refresh token、scope 与 role。
- JWT、Opaque Token、Session 的取舍。

验收标准：

- 增加本地 JWK Set 或 Mock IdP 说明，不能提交真实密钥。
- 文档明确 JWT profile 下 Basic 内部调用只是学习取舍。
- 至少补充无 token、过期 token、权限不足、服务间 token 四类追问。

核心追问：

- JWT 如何吊销？
- 网关校验过 token，服务还要校验吗？
- role 和 scope 有什么差异？
- 服务间调用应该使用用户 token 还是 client token？

### 4. 可观测性生产化

定位：当前有 metrics/logs/traces 基线，但生产追问会落到采样、告警、查询和排障闭环。

建议产物：

- `docs/observability-production-playbook.md`
- 可选 `observability/otel-collector/` Compose

内容范围：

- OpenTelemetry Collector 接入位置。
- Zipkin、Tempo、Jaeger 的定位差异。
- Loki/ELK 查询样例，日志字段规范。
- Prometheus alert rules、SLO、错误率、延迟分位数。
- label 基数控制、trace sampling、Sentry 与日志/trace 关联。

场景设计：

- 订单预览接口 p95 突增。
- catalog 500 增加但 order 有 fallback。
- Kafka consumer lag 上涨。
- 网关 429 增加。

验收标准：

- 至少给出 5 条 PromQL 查询和 3 条告警规则草案。
- 文档能说明 metrics/logs/traces 的排查顺序。
- 文档能解释 high cardinality 的风险。

核心追问：

- 接口慢，怎么从指标定位到具体下游？
- trace 采样 10% 后还能排查错误吗？
- Prometheus pull 模型有什么限制？
- 日志里哪些字段必须结构化？

### 5. Gateway 生产能力深化

定位：网关已有基线，后续重点是生产职责边界和流量治理。

建议产物：

- 深化 `docs/task-plans/02-gateway.md`
- `docs/gateway-production-playbook.md`

内容范围：

- CORS 配置与预检请求。
- 灰度路由：Header、权重、用户分组。
- 网关统一认证、服务侧最小授权。
- 分布式限流设计：Redis、令牌桶、漏桶、滑动窗口。
- 动态路由与配置中心的边界。

验收标准：

- 增加至少一个灰度路由示例或设计说明。
- 增加 CORS 和预检请求验收。
- 文档明确本地限流和生产分布式限流的差异。

核心追问：

- 鉴权放网关还是服务？
- 如何按用户维度限流？
- 灰度发布如何快速回滚？
- Gateway fallback 和业务 fallback 有什么区别？

## P1 补齐计划

### 6. 消息队列深化

定位：RabbitMQ 和 Kafka 已有基线，下一步补生产细节和排障。

建议内容：

- Kafka retry topic 替代 blocking retry 的示例或设计说明。
- Kafka consumer lag 查询、Grafana 面板和 rebalance 排查。
- Kafka producer transaction 的 read-process-write 边界说明。
- RabbitMQ publisher confirm、return callback、manual ack、prefetch。
- RocketMQ 设计专题：tag、顺序消息、延迟消息、事务半消息。

验收标准：

- 文档能比较 RabbitMQ/Kafka/RocketMQ 在业务消息场景的取舍。
- Kafka lag 排查至少覆盖生产速率、消费耗时、partition skew、rebalance、broker 磁盘/网络。
- RabbitMQ confirm 与 consumer ack 的区别必须有清晰示例。

### 7. JVM、并发和 Java 21 诊断

定位：当前虚拟线程只有入门示例，资深面试会追问诊断工具和线程模型。

建议产物：

- `docs/jvm-concurrency-playbook.md`
- `docs/task-plans/22-jvm-concurrency.md`

内容范围：

- 线程池参数、队列、拒绝策略、隔离池。
- `CompletableFuture` 编排、异常处理和超时。
- MDC / trace context 在线程切换中的传播。
- 虚拟线程适用场景和 pinned thread。
- JFR、jcmd、jstack、jmap、GC log 的基本排查流程。

验收标准：

- 补一个 pinned thread 或锁竞争的可复现说明。
- 给出 CPU 高、线程阻塞、内存上涨、接口超时四类排查路径。

### 8. 工程质量与 CI 门禁

定位：目前 CI 能跑测试，但资深工程能力还要体现质量门禁。

建议产物：

- `docs/engineering-quality-playbook.md`
- `docs/task-plans/23-engineering-quality.md`

内容范围：

- JaCoCo 覆盖率门禁。
- Checkstyle / SpotBugs / PMD 的取舍。
- OWASP Dependency-Check 或 Maven dependency audit。
- SBOM、镜像扫描、依赖升级策略。
- ArchUnit 架构规则：controller 不直接访问 client、可选 profile 不能污染默认路径。

验收标准：

- CI 文档说明哪些检查默认执行，哪些手动执行。
- 至少给出 5 条 ArchUnit 规则候选。
- 文档能解释覆盖率不能替代有效测试的原因。

## P2 补齐计划

### 9. Kubernetes 生产化

建议内容：

- Ingress、HPA、PDB、ServiceMonitor、startupProbe。
- Helm 或 Kustomize 目录结构。
- GitOps 发布流程、回滚和镜像 tag 策略。
- ConfigMap/Secret 与外部 Secret 系统。

验收标准：

- 最小 YAML 保持可读，生产化内容可以独立目录或文档说明。
- 不要求本地真实集群，但要给出 `kubectl` 验证命令和限制说明。

### 10. Native Image 完整验证

建议内容：

- 安装 GraalVM 或使用 buildpacks 完成 `catalog-service` native binary。
- 记录启动耗时、内存占用、镜像大小对比。
- 梳理 SpringDoc、Sentry、Feign、Gateway 的 native 风险。

验收标准：

- 至少完成一个服务 native binary 成功启动和 health check。
- 失败时必须记录具体工具链、命令和错误。

## 建议执行顺序

| 顺序 | 专题 | 优先级 | 推荐原因 |
| --- | --- | --- | --- |
| 1 | 数据一致性与事务边界 | P0 | 资深 Java 面试硬缺口，且能解释当前 MQ 幂等边界 |
| 2 | Redis 与缓存治理 | P0 | 当前只有 Caffeine，Redis 高频但不宜直接污染项目 |
| 3 | OAuth2 / JWT 生产化 | P0 | 当前 JWT 是学习模式，容易被追问真实授权服务器 |
| 4 | 可观测性生产化 | P0 | 已有基线，补告警和排障后面试表达会完整 |
| 5 | Gateway 生产能力 | P0 | 微服务架构题常以网关为入口深挖 |
| 6 | 消息队列深化 | P1 | Kafka/RabbitMQ 已有代码，继续深化投入产出高 |
| 7 | JVM、并发和 Java 21 诊断 | P1 | 弥补 Java 基础和线上排障能力 |
| 8 | 工程质量与 CI 门禁 | P1 | 展示工程负责人视角 |
| 9 | Kubernetes 生产化 | P2 | 当前已有最小 YAML，按需推进 |
| 10 | Native Image 完整验证 | P2 | 依赖本机工具链，适合后置 |

## 通用任务 Prompt 模板

后续执行任一专题时，可以使用下面模板：

```text
基于当前 Spring Boot 3 学习项目，补充【专题名称】专题。请先阅读：

- README.md
- docs/IMPLEMENTATION.md
- docs/USAGE.md
- docs/interview-roadmap.md
- docs/task-plans/19-interview-expansion.md

目标：
1. 保持默认 profile 轻量，不引入不必要的运行依赖。
2. 明确该专题的学习目标、实战场景、失败情况、生产边界和面试追问。
3. 如需要代码，优先使用独立 profile、独立目录或可控配置开关。
4. 如不适合接入真实组件，先用设计文档、故障矩阵、示意配置和验收清单补齐。
5. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或对应 task plan。
6. 记录实施过程到本地日志文件。
7. 完成后运行与变更匹配的验证命令，并提交 git。

验收：
1. 文档能支撑资深面试追问，不只列组件名。
2. 每个场景都有失败路径和处理策略。
3. 每个生产化建议都有边界说明，不夸大能力。
4. 当前项目默认测试和运行路径不被破坏。
```

## 专题 Prompt 清单

### 数据一致性专题 Prompt

```text
为当前项目补充数据一致性与事务边界专题。项目默认仍不接数据库和 Redis。请用文档和必要的轻量示例说明：本地事务、事务传播、隔离级别、幂等、outbox/inbox、消息最终一致性、补偿和对账。结合现有 Kafka/RabbitMQ 订单预览事件，列出 HTTP 成功但消息失败、消息成功但消费失败、消费成功但 ack/offset 失败、DLT/DLQ 重放等场景。输出设计图、故障矩阵、面试追问和验收清单。
```

### Redis 缓存专题 Prompt

```text
为当前项目补充 Redis 与缓存治理专题。项目默认仍不接 Redis。请基于当前 Caffeine 示例，补充 Caffeine 与 Redis 的边界、缓存穿透/击穿/雪崩、双写一致性、分布式锁、热点 key、大 key、限流计数器等面试高频内容。输出设计文档、与当前项目结合点、失败场景、生产化注意事项和追问清单。
```

### OAuth2 生产化 Prompt

```text
深化当前 JWT Resource Server 专题。补充真实授权服务器场景下的 issuer-uri、jwk-set-uri、JWK rotation、client_credentials 服务间调用、网关鉴权和服务侧授权边界、token 吊销策略。不得提交真实私钥或 token。更新安全专题文档、使用说明和面试追问。
```

### 可观测性生产化 Prompt

```text
深化当前 Prometheus/Grafana/Zipkin/JSON logging/Sentry 基线。补充 OpenTelemetry Collector、Tempo/Loki 或等价本地方案的设计说明，增加 PromQL 查询、告警规则草案、SLO、trace sampling、label 基数控制、日志检索样例和故障排查 playbook。保持默认服务可以不依赖这些后端启动。
```

### Gateway 生产能力 Prompt

```text
深化当前 Spring Cloud Gateway 专题。补充 CORS、灰度路由、权重路由、Header 路由、动态路由边界、分布式限流设计、网关鉴权与服务鉴权职责边界。可增加轻量测试或文档验收，不引入生产级动态路由平台。
```

### 消息队列深化 Prompt

```text
深化当前 RabbitMQ 和 Kafka 专题。Kafka 侧补 retry topic、consumer lag 查询和排查、rebalance、producer transaction 边界；RabbitMQ 侧补 publisher confirm、return callback、manual ack、prefetch 和堆积排查；RocketMQ 侧补 tag、顺序消息、延迟消息、事务半消息设计说明。更新消息路线和面试追问。
```

### JVM 并发诊断 Prompt

```text
为当前项目补充 JVM、并发和 Java 21 诊断专题。基于现有 virtual-thread profile，补充线程池参数、CompletableFuture、MDC/trace context 传播、虚拟线程 pinned 场景、JFR、jcmd、jstack、jmap、GC log 排查流程。输出可复现示例或文档验收，并沉淀 CPU 高、线程阻塞、内存上涨、接口超时的排查路径。
```

### 工程质量门禁 Prompt

```text
为当前项目补充工程质量与 CI 门禁专题。评估并引入或文档化 JaCoCo、Checkstyle/SpotBugs/PMD、依赖漏洞扫描、SBOM、镜像扫描、ArchUnit 架构规则。保持 CI 成本可控，区分默认必跑检查和手动深度检查。输出验收命令和面试追问。
```

## 完成定义

本总计划完成后，后续每个专题的完成定义为：

- 有明确入口文档或 task plan。
- 有当前项目结合点，不是泛泛资料摘抄。
- 有场景、失败路径、处理策略和验收标准。
- 有资深面试追问和回答要点。
- 有命令或自检清单可以验证。
- 如涉及代码，必须保持默认 profile 不被污染。
