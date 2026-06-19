# Kafka 资深面试覆盖度复查

## 复查目标

检查当前 Kafka 文档和项目示例是否足以支撑资深后端学习和面试追问。

复查范围：

- [Kafka 资深后端学习指南](kafka-learning-guide.md)
- [Kafka 项目场景实施文档](kafka-project-scenarios.md)
- [Kafka 资深后端面试追问题库](kafka-interview-question-bank.md)
- [Kafka 运维排障 Runbook](kafka-operations-runbook.md)
- [Kafka 使用与面试专题](kafka-playbook.md)
- `order-service/src/kafka/java`
- `order-service/src/kafka-test/java`
- `order-service/src/main/resources/application-kafka.yml`

## 总体结论

当前 Kafka 专题已经能覆盖资深后端面试中的核心范围：

- Kafka 基础模型。
- Broker 存储和复制。
- Producer 可靠性。
- Consumer offset 和 rebalance。
- 分区顺序和 key 设计。
- at-least-once、幂等和 exactly-once 边界。
- Spring Kafka 使用。
- retry、DLT 和重放治理。
- consumer lag 和生产排障。
- Schema 演进。
- 安全治理。
- 容量和性能规划。
- 与 RabbitMQ/RocketMQ/同步调用选型。
- 当前项目表达和边界说明。

当前仍需明确区分：

- 已落地能力：订单预览 producer/consumer/manual ack/内存幂等/blocking retry/DLT/Testcontainers，以及 demo lab 的 retry topic、Schema V2 兼容、事务 commit/abort 可见性、lag/rebalance 状态、安全模板、容量规划和 MQ 选型。
- 设计型覆盖：持久化幂等、outbox/inbox、Schema Registry、真实安全认证、lag Grafana 面板、DLT 重放工具、完整 Kafka 内 offset+output EOS。

## 覆盖矩阵

| 领域 | 文档覆盖 | 项目落地 | 面试承压 | 结论 |
| --- | --- | --- | --- | --- |
| Kafka 定位 | 学习指南、题库 | RabbitMQ/Kafka 双 profile | 能讲事件流 vs 传统 MQ | 已覆盖 |
| topic/partition/offset/group | 学习指南、场景文档、题库 | topic、group、3 partitions | 能讲分配和 offset | 已覆盖 |
| broker 存储 | 学习指南 | 未落地，属原理 | 能讲 segment/index/page cache | 已覆盖 |
| replica/ISR | 学习指南、题库 | 本地单 broker 不演示 | 能讲生产多副本 | 文档覆盖，项目不落地 |
| producer 可靠性 | 学习指南、场景文档 | `acks=all`、idempotence、callback | 能讲防丢和边界 | 已覆盖 |
| consumer ack | 学习指南、场景文档、题库 | manual ack | 能讲 at-least-once | 已覆盖 |
| 重复消费 | 学习指南、题库 | 内存 eventId 去重 | 能讲重复来源和幂等 | 已覆盖 |
| 业务幂等 | 学习指南、场景文档 | demo 内存幂等 | 能讲生产持久化方案 | 设计型覆盖 |
| 顺序性 | 学习指南、场景文档 | 同 key 顺序测试 | 能讲 key、热点、扩分区 | 已覆盖 |
| retry/DLT | 学习指南、场景文档、runbook | blocking retry、DLT 测试 | 能讲失败分类和重放 | 已覆盖 |
| retry topic | 学习指南、场景文档、题库 | demo 手写 retry topic + DLT | 能讲取舍 | 已覆盖，生产延迟调度待补 |
| lag 排障 | runbook、题库 | demo 慢消费 + Kafka UI/CLI 文档 | 能讲故障树 | 已覆盖，指标面板待补 |
| rebalance | 学习指南、runbook、题库 | demo listener 记录 assigned/revoked | 能讲原因和优化 | 已覆盖，扩缩容脚本待补 |
| Kafka transaction | 学习指南、场景文档、题库 | demo producer transaction commit/abort | 能讲 EOS 边界 | 已覆盖，offset+output EOS 待补 |
| Schema 演进 | 学习指南、场景文档、题库 | V2 JSON 兼容测试 | 能讲兼容策略 | 已覆盖，Schema Registry 待补 |
| 安全治理 | 学习指南、场景文档、题库 | `/api/kafka-demo/security-template` | 能讲 SASL/ACL | 模板覆盖，真实认证不落地 |
| 容量性能 | 学习指南、场景文档 | `/api/kafka-demo/capacity-plan` | 能讲调优方向 | 规划覆盖，压测待补 |
| MQ 选型 | 学习指南、场景文档、题库 | Kafka/RabbitMQ profile | 能讲取舍 | 已覆盖 |
| 项目表达 | 场景文档、题库 | 指向真实代码 | 能讲已落地/未落地 | 已覆盖 |

## 资深面试必答检查

| 问题 | 是否覆盖 | 证据 |
| --- | --- | --- |
| Kafka 为什么吞吐高 | 是 | `kafka-learning-guide` 第 3 节、题库第 2 题 |
| Kafka 如何保证消息不丢 | 是 | 学习指南第 4/7 节、题库第 6 题 |
| Kafka 为什么会重复 | 是 | 学习指南第 7 节、题库第 7 题 |
| 如何保证顺序 | 是 | 学习指南第 6 节、场景 6、题库第 4/5 题 |
| offset 提交时机 | 是 | 学习指南第 5 节、场景 4、题库第 9 题 |
| consumer lag 怎么排查 | 是 | runbook 第 1 节、题库第 10 题 |
| rebalance 怎么优化 | 是 | 学习指南第 5 节、runbook 第 2 节、题库第 11 题 |
| DLT 后怎么处理 | 是 | 学习指南第 8 节、runbook 第 4/8 节、题库第 12/23 题 |
| 幂等 producer 和业务幂等区别 | 是 | 学习指南第 7 节、题库第 14 题 |
| exactly-once 边界 | 是 | 学习指南第 10 节、题库第 8/15 题 |
| outbox/inbox | 是 | 学习指南第 7/10 节、题库第 16/26 题 |
| Schema 演进 | 是 | 学习指南第 9 节、场景 10、题库第 17 题 |
| 反序列化失败 | 是 | 题库第 18 题、当前 `ErrorHandlingDeserializer` 配置 |
| 分区数规划 | 是 | 学习指南第 14 节、题库第 19/29 题 |
| ISR/min.insync.replicas | 是 | 学习指南第 3/7 节、题库第 20/21 题 |
| 安全认证授权 | 是 | 学习指南第 13 节、场景 12、题库第 24 题 |
| 监控告警 | 是 | runbook PromQL/告警建议、题库第 25 题 |
| Kafka 与 RabbitMQ/RocketMQ 选型 | 是 | 学习指南第 15 节、场景 14、题库第 1/30 题 |
| 当前项目怎么讲 | 是 | 场景文档、题库第 30 题 |
| 当前项目短板怎么讲 | 是 | 场景文档“已落地能力与设计型能力”、题库第 30 题 |

## 项目代码支撑检查

| 面试说法 | 代码证据 | 判断 |
| --- | --- | --- |
| Kafka 是可选 profile | `order-service/pom.xml` 的 `kafka` profile | 有证据 |
| Spring profile 隔离 | `@Profile("kafka")` 和 `application-kafka.yml` | 有证据 |
| Producer 发布订单预览事件 | `KafkaOrderPreviewEventPublisher` | 有证据 |
| Event 有 trace/request metadata | `OrderPreviewKafkaEvent` 和 publisher headers | 有证据 |
| Consumer manual ack | `KafkaOrderPreviewConsumer` | 有证据 |
| 消费端 eventId 幂等 | `ProcessedKafkaEventStore` / `InMemoryProcessedKafkaEventStore` | 有证据，但仅 demo |
| DLT | `KafkaOrderMessagingConfig` | 有证据 |
| 不可重试异常分类 | `errorHandler.addNotRetryableExceptions` | 有证据 |
| Testcontainers 验证 | `OrderKafkaProfileIT` | 有证据 |
| demo retry topic | `KafkaDemoRetryTopicConsumer` / `KafkaDemoPublisher` | 有证据 |
| demo Schema V2 兼容 | `KafkaDemoSchemaConsumer` / `KafkaDemoPublisher.sendSchemaV2` | 有证据 |
| demo transaction commit/abort | `KafkaDemoPublisher` / `KafkaDemoTransactionConsumer` | 有证据，但不是完整业务 EOS |
| demo lag/rebalance 状态 | `KafkaDemoConsumer` / `KafkaDemoMessagingConfig` / `KafkaDemoState` | 有证据 |
| 安全模板、容量规划、MQ 选型 | `KafkaDemoScenarioService` / `KafkaDemoController` | 有证据 |
| Schema Registry | 无代码 | 不能说已实现 |
| 真实 SASL_SSL/ACL | 只有安全模板，无认证环境 | 不能说生产认证已实现 |
| lag Grafana 面板 | 无面板 | 不能说已实现 |

## 风险和补充建议

### P0：已经通过文档补齐

| 缺口 | 补齐结果 |
| --- | --- |
| Kafka 系统学习材料不足 | 新增 `kafka-learning-guide.md` |
| 项目场景不够系统 | 新增 `kafka-project-scenarios.md` |
| 面试追问不够完整 | 新增 `kafka-interview-question-bank.md` |
| 运维排障不够清晰 | 新增 `kafka-operations-runbook.md` |
| 已落地和设计型能力容易混淆 | 本文和场景文档明确区分 |

### P1：已经通过代码补齐的演示能力

| 任务 | 价值 | 验收 |
| --- | --- | --- |
| retry topic | 对比 blocking retry 和非阻塞重试 | 失败消息进入 retry topic，重试耗尽进 DLT |
| Schema V1/V2 兼容测试 | 支撑 Schema 演进面试 | 旧消费者能读新增可选字段 |
| Kafka transaction 示例 | 支撑 EOS 边界讲解 | committed transaction 可见，aborted transaction 不可见 |
| lag/rebalance 演练 | 支撑生产排障 | 慢消费和 assignment/revoked 状态可查看 |
| 安全、容量和选型端点 | 支撑生产治理追问 | 有模板、估算和 MQ 对比返回值 |

### P1：后续建议生产化增强

| 任务 | 价值 | 验收 |
| --- | --- | --- |
| DLT 重放工具 | 支撑故障恢复 | 可按 eventId/offset 限速重放，带 dry run |
| 完整 Kafka 内 read-process-write EOS | 支撑精确边界讲解 | output topic 和 consumed offset 同事务提交 |
| lag/rebalance 面板 | 支撑生产排障 | PromQL/Grafana 能展示 lag、rebalance、处理耗时和错误率 |

### P2：后续建议设计或可选 profile

| 任务 | 边界 |
| --- | --- |
| outbox/inbox 实战 | 需要引入 DB profile |
| Redis 去重 | 需要 Redis profile |
| Schema Registry | 需要新增本地基础设施 |
| SASL_SSL/mTLS | 可先只提供模板，不提交密钥 |
| 压测基线 | 需要明确机器和测试方法 |

## 面试完整性评分

| 维度 | 评分 | 说明 |
| --- | --- | --- |
| 基础概念 | 5 / 5 | topic、partition、offset、group、broker、replica 均覆盖 |
| 使用能力 | 5 / 5 | Spring Kafka 主链路和 demo lab 均已落地 |
| 生产可靠性 | 4.5 / 5 | 可靠性分层完整，outbox/inbox 仍是设计型 |
| 排障能力 | 4.5 / 5 | runbook 和 lag/rebalance demo 已有，broker/JMX/Grafana 未落地 |
| 面试追问 | 5 / 5 | 30 个高频题覆盖一问到三问 |
| 项目表达 | 5 / 5 | 能清楚区分已实现和未实现 |
| 资深深度 | 4.8 / 5 | Schema、EOS、安全、容量已有代码演示，生产外部设施仍设计型 |

总体评分：`4.8 / 5`。

当前已经足以支撑资深后端 Kafka 学习、代码演示和面试知识范围。若要进一步从“教学演示”提升到“生产实战承压”，优先落地 DLT 重放工具、完整 Kafka 内 read-process-write EOS、Schema Registry 和 lag/rebalance Grafana 面板。

## 面试前最终检查清单

- 能画出当前订单预览 Kafka 事件链路。
- 能说明默认 profile 为什么不依赖 Kafka。
- 能指出 producer、consumer、DLT、幂等的代码位置。
- 能指出 `/api/kafka-demo` 端点覆盖 retry topic、Schema、transaction、lag/rebalance、安全、容量和选型。
- 能解释当前内存幂等为什么不是生产方案。
- 能回答 producer idempotence 和业务幂等区别。
- 能回答 Kafka EOS 边界。
- 能回答 DLT 重放流程。
- 能回答 lag 排障路径。
- 能回答 rebalance 原因和优化。
- 能回答 Schema 演进。
- 能回答安全和 ACL。
- 能回答分区数规划。
- 能对比 Kafka、RabbitMQ、RocketMQ。
- 能诚实说明当前未落地能力。

## 后续任务入口

- 如果继续做文档深化：补图、补演示截图、补故障复盘样例。
- 如果继续做代码实现：从 `docs/task-plans/29-kafka-learning-and-project-scenarios.md` 的 P1 场景开始。
- 如果准备面试：优先复习 `kafka-interview-question-bank.md` 第 1 到 30 题。
