# 29 Kafka 学习与项目场景实施总计划

## 目标

围绕当前 Spring Boot 3 多模块项目，建立一份 Kafka 后续学习、文档沉淀、项目实战和资深后端面试补齐的总计划。本文最初作为计划入口，现在也记录已落地的文档和 demo lab 结果。

本计划承接现有 Kafka 基线：

- `order-service` 已通过 Maven profile `kafka` 和 Spring profile `kafka` 隔离 Kafka 能力。
- 当前事件主线是订单预览事件 `OrderPreviewCreatedEvent` 到 `OrderPreviewKafkaEvent`。
- 已有 `KafkaTemplate` producer、`@KafkaListener` consumer、manual ack、内存幂等、DLT、Micrometer 指标和 Testcontainers 集成测试。
- 已新增 `/api/kafka-demo` demo lab，覆盖基础模型、重复消费、幂等、顺序、retry topic、DLT、lag、rebalance 状态、Schema V2 兼容、事务 commit/abort 可见性、安全模板、容量规划和 MQ 选型。
- 默认 profile 继续保持轻量，不强制依赖 Kafka、数据库或 Redis。

## 任务 Prompt

后续执行本专题时，使用下面 prompt 作为总入口：

```text
基于当前 Spring Boot 3 多模块项目，按 docs/task-plans/29-kafka-learning-and-project-scenarios.md 实施 Kafka 学习与项目场景专题。请先阅读：

- docs/task-plans/29-kafka-learning-and-project-scenarios.md
- docs/task-plans/18-kafka.md
- docs/kafka-playbook.md
- docs/messaging-roadmap.md
- docs/messaging-production-playbook.md
- docs/interview-coverage-assessment.md
- order-service/src/main/resources/application-kafka.yml
- order-service/src/kafka/java/com/taoking/spring3/order/messaging/kafka
- order-service/src/kafka-test/java/com/taoking/spring3/order/messaging/kafka

目标：
1. 系统补齐 Kafka 知识文档：基础概念、架构原理、Spring Kafka 使用、生产实践、排障、面试重难点、追问和项目表达。
2. 按当前项目设计各类 Kafka 使用场景的实施计划，并逐项落地、验证、记录。
3. 以资深后端面试官视角检查知识和项目覆盖是否完整，发现遗漏后补充到文档、测试或后续计划。

边界：
1. 默认 profile 不引入 Kafka 强依赖。
2. 不把 Kafka 作为订单预览同步主链路的必需依赖。
3. 不接入真实数据库、Redis、Schema Registry、SASL/TLS 密钥或外部生产 Kafka 集群，除非后续任务明确调整项目边界。
4. 不宣称业务 exactly-once；必须区分 Kafka 内部 EOS 和外部副作用幂等。
5. 所有新增运行能力必须有独立 profile、配置、文档和验收命令。
```

## 当前项目基线

| 模块 | 当前能力 | 可用于 Kafka 专题的切入点 |
| --- | --- | --- |
| `order-service` | 订单预览、Catalog 调用、Resilience4j、JWT、安全、指标 | Kafka producer/consumer、事件建模、失败注入、链路追踪、指标告警 |
| `catalog-service` | 商品查询、故障模拟、OpenAPI、安全 | 作为订单预览的上游依赖，用于解释同步调用和异步事件的边界 |
| `gateway-service` | 路由、认证透传、限流、审计 | 演示 `X-Request-Id`、trace header 和 Kafka 事件关联 |
| `common` | DTO、API header、AOP 注解 | 复用请求 ID、统一 DTO 和事件字段说明 |
| `platform/kafka` | 本地 Kafka Compose | 本地学习、Kafka UI、topic/lag 排查入口 |
| `observability` | Prometheus、Grafana、告警规则 | Kafka 业务指标、consumer lag、DLT 告警计划 |

## 交付物规划

| 交付物 | 类型 | 目的 |
| --- | --- | --- |
| `docs/kafka-learning-guide.md` | 新增文档 | 系统学习 Kafka 基础、原理、使用、生产实践和面试 |
| `docs/kafka-project-scenarios.md` | 新增文档 | 当前项目 Kafka 场景清单、设计、验收和演示脚本 |
| `docs/kafka-interview-question-bank.md` | 新增文档 | 高频题、追问链路、标准回答要点和项目表达 |
| `docs/kafka-operations-runbook.md` | 新增文档 | lag、rebalance、DLT、broker 故障、分区倾斜、重放 runbook |
| `docs/kafka-coverage-review.md` | 新增文档 | 按资深面试维度做覆盖度检查和遗漏清单 |
| `docs/kafka-playbook.md` | 增量更新 | 保留本地使用入口，链接到新增专题文档 |
| `docs/messaging-roadmap.md` | 增量更新 | 把 Kafka 深化成果纳入 MQ 总路线 |
| `docs/interview-roadmap.md` | 增量更新 | 把 Kafka 面试追问纳入资深面试路线 |

## 当前实施结果

已完成第一批文档实施，覆盖资深 Kafka 学习、项目场景、面试追问、运维排障和完整性复查：

- `docs/kafka-learning-guide.md`
- `docs/kafka-project-scenarios.md`
- `docs/kafka-interview-question-bank.md`
- `docs/kafka-operations-runbook.md`
- `docs/kafka-coverage-review.md`
- `KafkaDemo*` 代码演示和 `OrderKafkaProfileIT` 集成测试扩展

- [Kafka 资深后端学习指南](../kafka-learning-guide.md)
- [Kafka 项目场景实施文档](../kafka-project-scenarios.md)
- [Kafka 资深后端面试追问题库](../kafka-interview-question-bank.md)
- [Kafka 运维排障 Runbook](../kafka-operations-runbook.md)
- [Kafka 资深面试覆盖度复查](../kafka-coverage-review.md)

已同步入口：

- [Kafka 使用与面试专题](../kafka-playbook.md)
- [消息队列专题路线](../messaging-roadmap.md)
- [Spring Boot 3 面试补充路线](../interview-roadmap.md)
- [使用说明](../USAGE.md)
- [项目 README](../../README.md)

代码增强已完成 demo lab：retry topic、Schema V1/V2 兼容、Kafka transaction commit/abort 可见性、consumer lag/rebalance 状态、安全模板、容量规划和 MQ 选型均有端点、文档和集成测试。后续如继续生产化，应优先落地 DLT 重放工具、完整 Kafka 内 offset+output EOS、Schema Registry、真实认证和 Grafana 面板。

## 第一部分：Kafka 学习计划

### 学习阶段总览

| 阶段 | 主题 | 必须掌握 | 项目练习 | 面试输出 |
| --- | --- | --- | --- | --- |
| 1 | Kafka 定位和模型 | topic、partition、offset、producer、consumer、consumer group | 画出订单预览事件流 | 能解释 Kafka 与传统 MQ 的区别 |
| 2 | Broker 和存储架构 | log segment、顺序追加、page cache、zero copy、retention、compaction | 用 Kafka UI/CLI 查看 topic、partition、offset | 能回答 Kafka 为什么吞吐高 |
| 3 | Producer 使用 | key、partitioner、acks、retries、batch、linger、compression、callback | 调整 `KafkaOrderPreviewEventPublisher` 参数说明和验证 | 能解释如何避免生产丢消息和重复 |
| 4 | Consumer 使用 | poll、group、rebalance、manual ack、offset commit、pause/resume | 深化 `KafkaOrderPreviewConsumer` 和测试 | 能解释先处理后提交为什么会重复 |
| 5 | 分区与顺序 | partition 内有序、key 选择、热点 key、扩分区风险 | 同一 orderId/sku 多事件顺序测试 | 能回答如何保证同一订单有序 |
| 6 | 可靠性语义 | at-most-once、at-least-once、producer idempotence、ISR、min.insync.replicas | 对照当前配置解释可靠性边界 | 能回答“不丢不重”为什么要分层讨论 |
| 7 | 幂等和一致性 | eventId、业务唯一键、inbox/outbox、CDC、补偿、重放 | 抽象持久化幂等设计，不强制接 DB/Redis | 能解释业务 exactly-once 为什么不能靠 Kafka 单独完成 |
| 8 | 重试和 DLT | blocking retry、retry topic、DLT、失败分类、重放流程 | 从当前 `DefaultErrorHandler` 扩展到 retry topic 计划 | 能回答 DLT 后如何处置 |
| 9 | Schema 演进 | JSON/Avro/Protobuf、Schema Registry、兼容模式、版本字段 | 增加 V1/V2 事件兼容说明和测试计划 | 能回答字段删除、重命名、消费者滞后升级怎么办 |
| 10 | 事务和 EOS | producer transaction、read-process-write、isolation.level、transactional.id | 设计 Kafka 内部转发事务示例 | 能明确 Kafka EOS 不覆盖 DB/HTTP/邮件 |
| 11 | Spring Kafka | `KafkaTemplate`、`@KafkaListener`、container factory、ack mode、error handler、`@RetryableTopic`、Testcontainers | 形成 Spring Kafka 使用章节 | 能把原生概念映射到 Spring 配置 |
| 12 | 观测和运维 | lag、rebalance、DLT、吞吐、失败率、broker 磁盘、网络、告警 | 接入 Prometheus/Grafana 文档和 runbook | 能回答线上 Kafka 消费慢如何排查 |
| 13 | 安全和治理 | SASL_SSL、mTLS、ACL、配额、命名规范、多环境隔离 | 提供配置模板，不提交真实密钥 | 能回答生产权限和配置怎么管 |
| 14 | 生态和选型 | Kafka Connect、Kafka Streams、Flink、MirrorMaker、RabbitMQ/RocketMQ 对比 | 结合当前 RabbitMQ 基线做选型对比 | 能回答为什么本场景选 Kafka 或不选 Kafka |

### 学习节奏建议

| 周期 | 学习重点 | 产出 |
| --- | --- | --- |
| 第 1 周 | Kafka 核心模型、producer、consumer、Spring Kafka 基础 | `kafka-learning-guide` 基础篇、当前代码导读 |
| 第 2 周 | 顺序、幂等、重试、DLT、offset、rebalance | 项目场景设计和 Testcontainers 验收清单 |
| 第 3 周 | 可靠性、事务、Schema、数据一致性边界 | 面试追问库、故障矩阵、边界说明 |
| 第 4 周 | consumer lag、运维排障、性能调优、安全治理、选型表达 | runbook、Grafana/PromQL 计划、覆盖度复查 |

### 文档章节要求

`docs/kafka-learning-guide.md` 后续至少包含：

1. Kafka 是什么、适合什么、不适合什么。
2. 核心概念：broker、cluster、topic、partition、replica、leader、follower、ISR、offset、consumer group。
3. 写入链路：partition 选择、batch、linger、compression、acks、idempotence、ISR、HW/LEO。
4. 读取链路：poll、fetch、offset、commit、rebalance、group coordinator、分区分配策略。
5. 存储机制：顺序写、segment、index、retention、compaction、page cache、zero copy。
6. 可靠性语义：消息丢失、重复、乱序、幂等、事务、EOS 边界。
7. Spring Kafka 实战：producer、consumer、manual ack、error handler、retry topic、DLT、事务、测试。
8. 生产治理：topic 命名、分区规划、容量估算、限流、配额、安全、监控、告警、升级。
9. 排障手册：lag、rebalance、分区倾斜、poison message、broker 宕机、磁盘满、消费阻塞。
10. 面试高频题和追问：每题必须包含回答要点、项目落地点、边界说明。

## 第二部分：当前项目 Kafka 场景实施计划

### 场景优先级

| 优先级 | 场景 | 当前状态 | 后续实施目标 |
| --- | --- | --- | --- |
| P0 | 订单预览事件生产消费 | 已有基线 | 补文档导读、演示脚本、失败路径说明 |
| P0 | manual ack 与 at-least-once | 已有基线 | 增加 offset 提交时机、重复消费原因和测试说明 |
| P0 | 幂等消费 | 已有内存实现 | 抽象生产级方案，对比 DB/Redis/compact topic/inbox |
| P0 | 重试与 DLT | 已有 blocking retry + DLT | 补失败分类、DLT 重放流程和告警 |
| P0 | 分区顺序 | 已有同 key 顺序测试 | 补 key 选择、热点 key、扩分区风险 |
| P1 | retry topic | Demo 已实现 | 后续可补真实延迟调度、防循环和 `@RetryableTopic` 对比 |
| P1 | consumer lag 和 rebalance | Demo 已实现基础状态 | 后续补 PromQL、Grafana 面板和多实例故障演练 |
| P1 | producer transaction | Demo 已实现 commit/abort 可见性 | 后续补 consumed offset + output topic 同事务 |
| P1 | Schema 演进 | Demo 已实现 V2 兼容 | 后续补 Schema Registry 和 Avro/Protobuf |
| P1 | DLT 可控重放 | 未实现 | 增加只读/手动确认的重放工具或脚本计划 |
| P2 | 安全配置模板 | Demo 已实现模板 | 后续接真实密钥管理和 ACL 验证 |
| P2 | 性能调优 | Demo 已实现容量估算 | 后续增加 batch、compression、poll、并发、压测基线 |
| P2 | Kafka Connect/CDC/outbox | 设计型覆盖 | 只做设计文档，除非后续允许引入 DB |
| P2 | 多集群容灾 | 未实现 | 增加 MirrorMaker/Cluster Linking 设计说明 |

### 场景 1：订单预览事件生产消费

目标：

- 以 `/api/orders/preview` 为入口，讲清同步业务返回和异步事件发布的关系。
- 说明 `ApplicationEventPublisher` 到 Kafka producer 的衔接。
- 补齐本地启动、curl 请求、Kafka UI 查看、指标查看和测试命令。

实施范围：

- 更新 `docs/kafka-project-scenarios.md`。
- 必要时补充测试说明，不改变业务主链路。

验收：

- 文档能从 HTTP 请求一路说明到 Kafka topic、partition、offset。
- 能解释请求失败、Catalog fallback、Kafka 发送失败对用户响应的影响。

### 场景 2：生产可靠性和配置取舍

目标：

- 系统解释当前 `acks=all`、`enable.idempotence=true`、`retries`、`max.in.flight.requests.per.connection` 的意义。
- 补充 `delivery.timeout.ms`、`request.timeout.ms`、`linger.ms`、`batch.size`、`compression.type` 的取舍。

实施范围：

- 文档化当前 `application-kafka.yml`。
- 增加配置矩阵和面试回答模板。

验收：

- 能回答 producer ack 丢失、broker leader 切换、重试乱序、吞吐下降等追问。

### 场景 3：消费确认、offset 和重复消费

目标：

- 讲清 manual ack、处理成功后提交、处理失败不 ack 的原因。
- 枚举重复消费来源：producer retry、commit 失败、rebalance、应用重启、DLT 重放。

实施范围：

- 深化 `KafkaOrderPreviewConsumer` 相关文档。
- 后续可增加故障注入测试：处理成功但 ack 前模拟异常。

验收：

- 文档能明确 at-least-once 和业务幂等的关系。
- 能解释为什么“提交 offset 成功”与“业务副作用成功”不是同一件事。

### 场景 4：幂等消费和一致性边界

目标：

- 当前内存 `ProcessedKafkaEventStore` 只作为 demo。
- 生产方案必须覆盖数据库唯一键、inbox/outbox、Redis set、compact topic、业务状态机版本号。

实施范围：

- 不强制接数据库或 Redis。
- 输出生产设计图和方案对比表。

验收：

- 能回答“重复下单、重复扣库存、重复发短信”怎么防。
- 能说明当前项目已经实现什么、哪些只是设计方案。

### 场景 5：分区、顺序和热点 key

目标：

- 解释 Kafka 只保证 partition 内有序。
- 结合订单场景讨论 key 选 `orderId`、`sku`、`userId` 的差异。
- 说明扩分区导致 key 映射变化的风险。

实施范围：

- 补充同 key 顺序、多 key 并发、热点 SKU 倾斜的测试或文档。
- 后续可增加模拟热点 key 的本地脚本。

验收：

- 能回答“如何保证同一订单状态有序”和“为什么不能保证全局有序”。

### 场景 6：重试、失败分类和 DLT

目标：

- 区分瞬时异常、业务不可重试异常、poison message、反序列化失败。
- 明确 blocking retry 与 retry topic 的取舍。
- 设计 DLT 重放前的排查、修复、限速、审计和防循环策略。

实施范围：

- 基于当前 `DefaultErrorHandler` 补充文档。
- 后续引入 retry topic 或 `@RetryableTopic`。

验收：

- 能回答“为什么 DLT 不是自动补偿”和“重放 DLT 怎么防止再次打爆系统”。

### 场景 7：consumer lag、rebalance 和排障

目标：

- 增加 Kafka CLI、Kafka UI、Actuator/Micrometer、PromQL/Grafana 的排障路径。
- 演示 lag 上涨、消费者扩缩容、consumer 数量超过 partition、rebalance 频繁等场景。

实施范围：

- `docs/kafka-operations-runbook.md` 增加命令和流程。
- 后续可补 Grafana panel 或 Prometheus alert rules。

验收：

- 能从生产速率、消费耗时、partition skew、下游依赖、rebalance、broker 资源六个方向排查 lag。

### 场景 8：Schema 演进和兼容测试

目标：

- 从当前 `eventVersion=1` 扩展到 V1/V2 兼容策略。
- 明确允许新增可选字段，不直接删除/重命名字段。
- 说明 JSON、Avro、Protobuf 和 Schema Registry 的生产差异。

实施范围：

- 后续增加兼容性测试，验证旧消费者读取新事件。
- 不强制接真实 Schema Registry。

验收：

- 能回答“生产者先升级还是消费者先升级”“字段改名怎么办”“老消息如何处理”。

### 场景 9：Kafka 事务和 exactly-once 边界

目标：

- 增加 Kafka 内 read-process-write 事务示例设计，例如读取订单预览事件后写入审计 topic。
- 明确该示例不覆盖数据库、HTTP、邮件、短信等外部副作用。

实施范围：

- 后续可新增独立 topic 和 profile 内测试。
- 文档必须说明 `transactional.id`、`isolation.level=read_committed`、事务超时和 zombie producer。

验收：

- 能回答 Kafka EOS 的适用范围和 outbox/inbox 的必要性。

### 场景 10：安全、权限和多环境治理

目标：

- 补充 SASL_SSL、mTLS、ACL、topic 命名、consumer group 命名、多环境隔离。
- 给出模板配置，不提交真实密钥和生产地址。

实施范围：

- 只做文档和示例配置片段。

验收：

- 能回答生产环境如何防止任意服务读写任意 topic。

### 场景 11：性能调优和容量评估

目标：

- 建立压测和调优思路：分区数、批量、压缩、生产并发、消费并发、poll 参数、反压。
- 说明调优不能只追求 TPS，还要看延迟、错误率、lag、broker 资源和下游容量。

实施范围：

- 后续可增加 k6/JMeter/wrk 或自定义 producer 脚本。
- 当前阶段先完成指标和测试计划。

验收：

- 能回答“Kafka 吞吐低怎么调”和“partition 越多是否越好”。

### 场景 12：与 RabbitMQ/RocketMQ/同步调用的选型对比

目标：

- 基于当前 RabbitMQ 基线和订单预览场景，形成选型表达。
- 说明 Kafka 更适合事件流、日志流、高吞吐、多订阅和可回放场景。
- 说明 RabbitMQ 更适合业务路由、传统队列、低延迟任务分发等场景。

实施范围：

- 更新 `docs/messaging-roadmap.md` 和面试题库。

验收：

- 面试时能结合项目说明为什么使用 Kafka，也能说明什么时候不该用 Kafka。

## 第三部分：完整性检查与补充

### 覆盖度检查结论

| 维度 | 当前覆盖 | 风险 | 补充动作 |
| --- | --- | --- | --- |
| 基础概念 | 已有 topic、producer、consumer、partition、offset | 缺统一学习文档 | 新增 `kafka-learning-guide` |
| Spring Kafka 使用 | 已有 producer/consumer/ack/DLT/Testcontainers 和 demo lab | 完整 offset+output EOS、DLT 重放和生产延迟调度未落地 | 后续按生产化场景增强 |
| 面试高频 | `kafka-playbook` 已覆盖一部分 | 追问链路还不够系统 | 新增题库，按“一问、二问、三问”组织 |
| 生产可靠性 | 有 producer 幂等和 manual ack | ISR、min.insync.replicas、unclean leader election、事务边界需要补 | 增加可靠性专题章节 |
| 排障运维 | 有指标和 DLT 基线 | lag/rebalance/分区倾斜/重放流程不够 | 新增 runbook 和 PromQL/Grafana 计划 |
| 数据一致性 | 设计文档已有 outbox/inbox | 当前无 DB/Redis 实战，面试需主动说明边界 | 在 Kafka 文档中明确“已实现 vs 设计型覆盖” |
| Schema 治理 | 已有 V2 JSON 兼容 demo | Schema Registry 未落地 | 后续新增 Schema Registry 设计或本地基础设施 |
| 安全治理 | 已有安全配置模板端点 | 真实 SASL/TLS/ACL 环境未落地 | 后续接密钥管理和权限验证 |
| 性能调优 | 已有容量规划端点 | 未做压测基线 | 后续新增 batch、poll、分区规划和压测记录 |
| 生态扩展 | 有 MQ 对比 | Kafka Connect/Streams/Flink/CDC 覆盖浅 | 设计型覆盖，不强制接入 |

### 资深后端面试必补清单

后续文档和实现必须能回答以下追问：

1. Kafka 为什么吞吐高？必须覆盖顺序写、批量、压缩、page cache、zero copy、partition 并行。
2. Kafka 如何保证消息不丢？必须分 producer、broker、副本、consumer 四层回答。
3. Kafka 为什么仍会重复？必须覆盖 retry、ack/commit 失败、rebalance、重启、重放。
4. 如何保证顺序？必须说明 partition 内有序、key 选择、并发消费、扩分区风险。
5. consumer lag 上涨怎么排查？必须覆盖生产速率、消费耗时、partition skew、下游慢、rebalance、broker 资源。
6. rebalance 为什么发生，怎么降低影响？必须覆盖心跳、`max.poll.interval.ms`、static membership、cooperative rebalance。
7. DLT 后如何处理？必须覆盖告警、定位、修复、抽样、限速重放、防循环、审计。
8. 幂等 producer 和业务幂等有什么区别？必须结合当前内存幂等和生产持久化方案说明。
9. Kafka exactly-once 能解决什么，不能解决什么？必须区分 Kafka 内部事务和外部副作用。
10. Schema 如何演进？必须覆盖兼容模式、字段新增/删除/重命名、消费者滞后升级。
11. 分区数如何规划？必须覆盖吞吐、并发、key 分布、未来扩容、文件句柄、恢复成本。
12. retry topic 和 blocking retry 怎么选？必须说明 partition 阻塞、延迟调度、topic 数量和排障复杂度。
13. Kafka 与 RabbitMQ/RocketMQ 怎么选？必须结合当前项目已有 RabbitMQ 示例回答。
14. 生产安全怎么做？必须覆盖 SASL_SSL、mTLS、ACL、topic/group 命名、多环境隔离。
15. 如果订单服务处理成功但发 Kafka 失败怎么办？必须覆盖同步主链路边界、outbox、重试、补偿和告警。
16. 如果消费 Kafka 成功但调用下游失败怎么办？必须覆盖重试、DLT、幂等、补偿、限流、熔断。
17. 如何做 Kafka 监控告警？必须覆盖 producer error、consumer lag、rebalance count、DLT count、broker disk/network、under replicated partitions。
18. 如何安全重放消息？必须覆盖幂等、时间窗口、速率控制、按 key 顺序、dry run、审计。
19. Kafka broker 宕机或磁盘满怎么办？必须覆盖副本、ISR、leader election、扩容、清理策略、降级。
20. 当前项目哪些是已落地，哪些是设计型覆盖？必须如实区分，避免面试中过度声称。

### 内容补漏计划

| 缺口 | 补齐方式 | 优先级 |
| --- | --- | --- |
| Kafka 存储和 broker 内核 | 在学习文档增加 log segment、index、HW/LEO、ISR、controller、KRaft | P0 |
| rebalance 深水区 | 增加 consumer group 协议、分配策略、static membership、cooperative rebalance | P0 |
| lag 排障 | 增加 CLI、Kafka UI、PromQL、Grafana 和 runbook | P0 |
| DLT 重放 | 增加重放流程和后续工具计划 | P0 |
| retry topic | 增加 Spring Kafka `@RetryableTopic` 或手写 retry topic 场景 | P1 |
| producer transaction | 增加 Kafka 内 read-process-write 示例 | P1 |
| Schema 兼容 | 增加 V1/V2 测试和 Schema Registry 设计说明 | P1 |
| 安全治理 | 增加 SASL/TLS/ACL 模板 | P1 |
| 容量和性能 | 增加参数调优、压测指标和分区规划 | P1 |
| 多集群和容灾 | 增加 MirrorMaker 2、Cluster Linking、跨地域延迟和 RPO/RTO 设计 | P2 |

## 后续实施顺序

1. 新增 `docs/kafka-learning-guide.md`，先补完整知识体系和当前代码导读。
2. 新增 `docs/kafka-project-scenarios.md`，把十二类项目场景拆成可执行任务。
3. 新增 `docs/kafka-interview-question-bank.md`，整理一问、二问、三问和项目回答模板。
4. 新增 `docs/kafka-operations-runbook.md`，补 lag、rebalance、DLT、重放和 broker 故障排查。
5. 新增 `docs/kafka-coverage-review.md`，以资深面试官视角复查并回填遗漏。
6. 代码实施已补齐 demo lab；后续生产化增强优先 DLT 重放、完整 Kafka 内 EOS、Schema Registry、lag/rebalance 面板和压测基线。

## 验收标准

本计划执行完成后，必须满足：

- 文档能覆盖 Kafka 学习、使用、生产实践、面试重难点、追问、项目场景和边界说明。
- 每个项目场景都有目标、实施范围、验收方式、面试表达和不做事项。
- 当前项目已有能力和设计型能力区分清楚。
- 后续代码任务不破坏默认 profile，不引入强制外部依赖。
- Kafka 相关内容能经受资深后端面试追问，尤其是可靠性、顺序、幂等、事务、lag、rebalance、DLT、Schema、安全和容量规划。

## 保留边界

- 不接数据库、Redis、Schema Registry、Flink、Kafka Streams 或生产 Kafka 集群。
- 不提交 SASL/TLS 密钥或真实生产凭证。
- 不修改订单预览同步业务行为。
- 不把 demo 级能力描述为生产级能力。
- 不宣称订单预览业务 exactly-once。
