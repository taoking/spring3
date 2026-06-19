# Kafka 项目场景实施文档

## 目标

把 Kafka 知识落到当前 Spring Boot 3 项目中，形成可以演示、可以测试、可以面试表达的项目场景库。

当前边界：

- Kafka 仅在 `-Pkafka` + `SPRING_PROFILES_ACTIVE=kafka` 下启用。
- 当前业务主线是 `order-service` 的订单预览接口。
- 当前不接数据库、Redis、Schema Registry、生产 Kafka 集群。
- 当前已落地订单预览 Kafka 主链路，以及独立 Kafka demo lab。
- demo lab 覆盖基础模型、重复消费、幂等、顺序、manual retry topic、DLT、lag、rebalance 状态记录、Schema V2 兼容、事务 commit/abort 可见性、安全模板、容量规划和 MQ 选型。
- 后续 P1/P2 场景可以继续按本文实施，但不能破坏默认 profile。

## 当前事件链路

```text
HTTP POST /api/orders/preview
  -> OrderController.preview
  -> OrderService.preview
  -> CatalogGovernanceService 调 catalog-service 或 fallback
  -> ApplicationEventPublisher 发布 OrderPreviewCreatedEvent
  -> KafkaOrderPreviewEventPublisher 监听应用事件
  -> KafkaTemplate 发送 OrderPreviewKafkaEvent
  -> spring3.order-preview.events.v1
  -> KafkaOrderPreviewConsumer 消费
  -> 业务处理成功后 manual ack
  -> 失败后 DefaultErrorHandler 重试
  -> 重试耗尽后 spring3.order-preview.dlt.v1
```

Kafka demo lab 事件链路：

```text
HTTP /api/kafka-demo/*
  -> KafkaDemoController
  -> KafkaDemoScenarioService
  -> KafkaDemoPublisher
  -> spring3.kafka-demo.* topics
  -> KafkaDemoConsumer / KafkaDemoRetryTopicConsumer / KafkaDemoSchemaConsumer / KafkaDemoTransactionConsumer
  -> KafkaDemoState 暴露消费、重复、顺序、retry、DLT、lag、rebalance 和事务状态
```

关键文件：

| 文件 | 作用 |
| --- | --- |
| `order-service/src/main/resources/application-kafka.yml` | Kafka producer、consumer、topic、ack、retry、DLT 配置 |
| `KafkaOrderPreviewEventPublisher` | 把应用内订单预览事件发布到 Kafka |
| `OrderPreviewKafkaEvent` | Kafka 事件模型 |
| `KafkaOrderPreviewConsumer` | 消费订单预览事件、manual ack、幂等和失败注入 |
| `KafkaOrderMessagingConfig` | topic、listener container、error handler、DLT recoverer |
| `ProcessedKafkaEventStore` | 消费幂等抽象 |
| `InMemoryProcessedKafkaEventStore` | 学习用内存幂等实现 |
| `OrderKafkaProfileIT` | Testcontainers 集成测试 |
| `platform/kafka/docker-compose.yml` | 本地 Kafka 和 Kafka UI |
| `KafkaDemoController` | `/api/kafka-demo` 演示入口 |
| `KafkaDemoScenarioService` | 封装基础、重复、顺序、retry、lag、Schema、事务、安全、容量和选型场景 |
| `KafkaDemoMessagingConfig` | demo topic、manual ack listener、`read_committed` consumer 和 rebalance listener |
| `KafkaDemoRetryTopicConsumer` | 手写 retry topic 到 DLT 示例 |
| `KafkaDemoSchemaConsumer` | V1 consumer 读取 V2 JSON 并忽略新增字段 |
| `KafkaDemoTransactionConsumer` | 事务输入和审计 topic 示例 |
| `KafkaDemoState` | 演示状态快照和测试断言依据 |

## 已落地能力与设计型能力

| 能力 | 当前状态 | 说明 |
| --- | --- | --- |
| 可选 profile 隔离 | 已落地 | 默认构建不编译 Kafka 专题源码 |
| Kafka producer | 已落地 | `KafkaTemplate` + send callback |
| Kafka consumer | 已落地 | `@KafkaListener` |
| topic 自动声明 | 已落地 | `NewTopic` |
| manual ack | 已落地 | `MANUAL_IMMEDIATE` + `Acknowledgment` |
| 幂等消费 | Demo 已落地 | 内存 Set，不抗重启 |
| blocking retry | 已落地 | `DefaultErrorHandler` + `FixedBackOff` |
| DLT | 已落地 | `DeadLetterPublishingRecoverer` |
| Testcontainers | 已落地 | 生产消费、幂等、顺序、DLT |
| retry topic | Demo 已落地 | `KafkaDemoRetryTopicConsumer` 手写 retry topic 和 retry DLT |
| producer transaction | Demo 已落地 | commit 可见、abort 不可见；不宣称业务 EOS |
| Schema V2 兼容 | Demo 已落地 | 旧消费者读取 V2 JSON 并忽略新增可选字段 |
| lag/rebalance 状态 | Demo 已落地 | 慢消费和 assignment/revoked 状态可从 `/api/kafka-demo/state` 查看 |
| Schema Registry | 未落地 | 只做设计型覆盖 |
| 持久化 outbox/inbox | 未落地 | 当前项目不接 DB |
| SASL_SSL/mTLS | 未落地 | 只提供模板和面试设计 |
| lag Grafana 面板 | 未落地 | 可后续接入观测性专题 |

## 场景 1：订单预览事件生产消费

### 学习目标

- 理解同步 HTTP 业务和异步 Kafka 事件的边界。
- 理解应用事件到 Kafka 事件的转换。
- 能从接口请求追踪到 topic、partition、offset。

### 当前实现

- `OrderService.preview()` 创建 `OrderPreviewResponse` 后发布 `OrderPreviewCreatedEvent`。
- `KafkaOrderPreviewEventPublisher` 监听该事件并发送 `OrderPreviewKafkaEvent`。
- 发送成功日志包含 eventId、requestId、traceId、topic、partition、offset。

### 本地演示

```bash
docker compose -f platform/kafka/docker-compose.yml up -d
./mvnw -pl catalog-service spring-boot:run
SPRING_PROFILES_ACTIVE=kafka ./mvnw -Pkafka -pl order-service spring-boot:run
```

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: kafka-demo-request' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  -d '{"sku":"SKU-KAFKA-OK","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

### 验收

- HTTP 返回 200。
- order-service 日志出现 Kafka publish 成功和 consume 成功。
- Kafka UI 可以看到 `spring3.order-preview.events.v1`。
- Actuator 可以查看 `orders.preview.kafka.published.total` 和 `orders.preview.kafka.processed.total`。

### 面试表达

> 订单预览接口返回不依赖 Kafka 消费成功。当前示例用应用事件解耦同步主流程和异步事件发布，Kafka profile 关闭时不影响默认运行。生产里如果要求“业务成功必须发布事件”，需要 outbox，而不是仅靠应用内事件监听。

## 场景 2：事件模型和 Header 设计

### 学习目标

- 学会设计可追踪、可幂等、可演进的事件。
- 理解 payload 与 metadata 的边界。

### 当前事件字段

| 字段 | 用途 |
| --- | --- |
| `eventId` | 幂等键，当前等于订单预览 ID |
| `eventType` | 事件类型，当前是 `OrderPreviewCreated` |
| `eventVersion` | 事件版本，当前是 1 |
| `source` | 事件来源服务 |
| `occurredAt` | 业务事件发生时间 |
| `aggregateType` | 聚合类型 |
| `aggregateId` | 聚合 ID |
| `partitionKey` | Kafka key |
| `requestId` | 请求关联 ID |
| `traceId` | 链路追踪 ID |
| `payload` | 订单预览业务数据 |

### Header

| Header | 用途 |
| --- | --- |
| `eventId` | 消费幂等和排障 |
| `eventType` | 消费路由 |
| `eventVersion` | 兼容治理 |
| `source` | 来源识别 |
| `X-Request-Id` | 日志关联 |
| `traceId` / `traceparent` | 链路追踪 |

### 后续实施

- 增加事件字段规范章节。
- V1/V2 兼容测试已由 `KafkaDemoSchemaConsumer` 和 `OrderKafkaProfileIT.kafkaDemoScenariosCoverDuplicatesOrderingSchemaAndLag` 覆盖。
- 后续如果增加 `OrderPaid`、`OrderCancelled`，应保持同一事件 envelope。

### 面试表达

> 我不会只把业务 DTO 直接塞到 Kafka。事件需要 envelope，至少包含 eventId、eventType、eventVersion、source、occurredAt、aggregateId、partitionKey、trace 信息和 payload。这样才能支持幂等、追踪、Schema 演进和重放。

## 场景 3：Producer 可靠性配置

### 学习目标

- 理解 `acks=all`、producer retry、idempotence、callback 的边界。
- 能解释发送结果未知和业务补偿。

### 当前配置

| 配置 | 当前值 |
| --- | --- |
| `acks` | `all` |
| `enable.idempotence` | `true` |
| `retries` | `10` |
| `max.in.flight.requests.per.connection` | `5` |
| `delivery.timeout.ms` | `30000` |
| `request.timeout.ms` | `5000` |
| `max.block.ms` | `1000` |

### 当前实现

- send callback 成功时记录 topic、partition、offset。
- send callback 失败时增加失败 counter 并记录异常。
- 当前没有 outbox，所以 Kafka 发送失败不会自动补偿订单预览事件。

### 后续实施

| 动作 | 优先级 | 验收 |
| --- | --- | --- |
| 文档补齐 producer 参数矩阵 | P0 | 能解释可靠性和吞吐取舍 |
| 增加 send failure 故障注入说明 | P1 | broker 不可用时能看到失败指标 |
| 设计 outbox 发布方案 | P1 | 文档说明 DB profile 之前不落地 |

### 面试表达

> `acks=all` 和 producer idempotence 能提高 Kafka 写入可靠性，但不能保证业务事件一定发布。业务成功后应用崩溃、callback 失败、发送结果未知都需要 outbox、重试任务或补偿机制兜底。

## 场景 4：Consumer manual ack 和 offset

### 学习目标

- 理解处理成功后提交 offset 的 at-least-once 语义。
- 能解释重复消费和丢消息边界。

### 当前实现

- `enable-auto-commit=false`。
- listener container 使用 `MANUAL_IMMEDIATE`。
- 消费成功后 `acknowledgment.acknowledge()`。
- 业务异常抛出，由 error handler 接管。
- 重复 eventId 跳过后仍 ack。

### 风险矩阵

| 情况 | 结果 |
| --- | --- |
| 处理前提交 offset，处理失败 | 消息可能丢 |
| 处理成功，ack 前进程崩溃 | 消息会重复 |
| 处理成功，commit 请求失败 | 消息会重复 |
| rebalance 发生且 offset 未提交 | 消息会重复 |
| 重复消息不 ack | 同一消息可能反复阻塞 |

### 后续实施

- 增加“处理成功但 ack 前失败”的测试设计。
- 文档补充 offset committed、current position、log end offset 的区别。
- runbook 增加 `kafka-consumer-groups --describe` 使用。

### 面试表达

> 我们选择处理成功后手动提交 offset，所以是 at-least-once。它优先避免丢消息，但必须接受重复消费。重复不是 Kafka bug，而是可靠消费模型的正常结果，所以消费侧必须幂等。

## 场景 5：幂等消费

### 学习目标

- 理解 at-least-once 下业务幂等是必须项。
- 区分 demo 内存去重和生产持久化幂等。

### 当前实现

- `ProcessedKafkaEventStore.markProcessing(eventId, event)`。
- 同一 eventId 再次到达时计入 duplicate counter。
- 重复消息直接 ack。

### 生产方案对比

| 方案 | 如何做 | 适合场景 |
| --- | --- | --- |
| 数据库唯一键 | `event_id` 唯一索引 | 强业务副作用 |
| inbox 表 | 消息入库和业务处理同事务 | 消费消息后写本地库 |
| outbox 表 | 业务数据和待发事件同事务 | 业务成功必须发事件 |
| Redis set | eventId + TTL | 高吞吐、可接受过期窗口 |
| 状态机版本 | aggregateId + version | 订单状态流转 |
| compact topic | eventId 处理状态写 Kafka | Kafka 内部状态管理 |

### 后续实施

- 当前项目不接 DB/Redis，先补设计文档。
- 如果后续允许 DB profile，优先做 inbox/outbox 集成测试。

### 面试表达

> 当前项目实现的是教学级内存幂等，证明逻辑路径。生产中我会用数据库唯一键或 inbox 表做持久化去重，并把消费 offset 和业务处理的失败窗口纳入补偿和对账。

## 场景 6：分区、顺序和热点 key

### 学习目标

- 理解 partition 内有序。
- 学会根据业务有序维度选择 key。
- 识别热点 key 和扩分区风险。

### 当前实现

- topic 默认 3 个 partition。
- key 是 `partitionKey`，当前等于订单预览 ID。
- `OrderKafkaProfileIT.sameKeyKafkaEventsAreConsumedInOrder` 验证同 key 顺序。
- `POST /api/kafka-demo/ordered?key=order-1001&count=3` 可直接演示同 key 顺序，`GET /api/kafka-demo/state` 查看按 key 记录的 eventId 顺序。

### key 策略

| key | 当前项目含义 | 风险 |
| --- | --- | --- |
| `orderId` | 同一订单预览相关事件有序 | 不保证同一 SKU 有序 |
| `sku` | 同一商品事件有序 | 热点商品导致倾斜 |
| `userId` | 同一用户行为有序 | 活跃用户倾斜 |
| 固定值 | 全局有序 | 吞吐退化为单分区 |

### 后续实施

- 增加热点 SKU 场景说明。
- 增加扩分区后 key 映射变化的风险说明。
- 后续可增加多 key 分布测试。

### 面试表达

> Kafka 不保证全局顺序。要先定义“业务到底要求哪个维度有序”，再把这个维度作为 key。订单状态用 orderId，库存流水可能用 sku。key 选错会导致顺序语义不成立，key 太集中会导致分区倾斜。

## 场景 7：重试、失败分类和 DLT

### 学习目标

- 理解 blocking retry 和 DLT。
- 学会区分可重试和不可重试异常。
- 理解 DLT 重放不是自动补偿。

### 当前实现

- `DefaultErrorHandler` 使用 `FixedBackOff`。
- `IllegalArgumentException` 不重试。
- `DeadLetterPublishingRecoverer` 写入 DLT。
- poison SKU `SKU-KAFKA-FAIL` 用于模拟消费失败。
- `KafkaDemoRetryTopicConsumer` 额外提供手写 retry topic 示例：输入 topic 失败后转入 retry topic，超过 attempt 后进入 retry DLT。

### 演示

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-KAFKA-FAIL","quantity":2}' \
  http://localhost:8080/api/orders/preview

curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/retry-topic?key=retry-1001&failUntilAttempt=2'

curl -u user:user123 http://localhost:8080/api/kafka-demo/state
```

### DLT 消息应保留

- 原 topic。
- 原 partition。
- 原 offset。
- 异常类型。
- eventId。
- 原始 payload。

### 后续实施

| 动作 | 优先级 |
| --- | --- |
| 补 DLT 重放 runbook | P0 |
| 增强 retry topic 延迟调度和防循环 header | P1 |
| 增加不可重试异常分类表 | P1 |
| 增加 DLT 告警建议 | P1 |

### 面试表达

> DLT 的价值是隔离坏消息和保留排查现场，不是自动修复。进 DLT 后要告警、定位、修复、限速重放，并确认幂等和顺序风险。否则盲目重放会再次失败，甚至造成重复副作用。

## 场景 8：consumer lag 和 rebalance 排障

### 学习目标

- 能定位消费慢。
- 能解释 rebalance 频繁造成 lag 的原因。
- 能用命令和指标证明问题。

### 当前状态

- 当前代码有业务 counter。
- `KafkaDemoConsumer` 提供 lag topic 慢消费演示。
- `KafkaDemoMessagingConfig` 在 demo listener 上注册 `ConsumerRebalanceListener`，把 assigned/revoked 写入 `KafkaDemoState`。
- 尚未新增 Kafka lag Grafana 面板。
- 可用 Kafka CLI 和 Kafka UI 做本地排查。

### 演示

```bash
curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/lag?key=lag-1001&count=50&processingDelayMs=200'

curl -u user:user123 http://localhost:8080/api/kafka-demo/state
```

### 仍需补齐

- Grafana 面板。
- PromQL 告警规则的真实接入。
- 多实例扩缩容触发 rebalance 的脚本化演练。

详细内容见 [Kafka 运维排障 Runbook](kafka-operations-runbook.md)。

### 面试表达

> lag 上涨我不会只说“加消费者”。我会先拆生产速率、消费耗时、partition 分布、下游依赖、rebalance 和 broker 资源。consumer 数超过 partition 数也不会继续提升同一 group 的并发。

## 场景 9：retry topic

### 学习目标

- 理解非阻塞重试。
- 避免短暂失败阻塞主 topic 同 partition 后续消息。

### 当前状态

- 订单预览主链路仍使用 blocking retry，便于说明简单业务失败处理。
- demo lab 已落地手写 retry topic：`spring3.kafka-demo.retry.input.v1` -> `spring3.kafka-demo.retry.wait.v1` -> `spring3.kafka-demo.retry.dlt.v1`。
- retry attempt 写入 header，`KafkaDemoState` 记录每次 attempt 和最终 DLT。

### 演示

```bash
curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/retry-topic?key=retry-1001&failUntilAttempt=2'

curl -u user:user123 http://localhost:8080/api/kafka-demo/state
```

### 后续增强

方案 A：Spring Kafka `@RetryableTopic`

- 对比框架生成 retry topic 和当前手写 retry topic。
- 配置 attempts、backoff、DltStrategy。
- 验证主 topic 不被长时间阻塞。

方案 B：手写 retry topic

- 当前 demo 已实现 consumer 失败后发送到 retry topic。
- retry listener 延迟后再处理或回主 topic。
- 后续可补真实延迟调度、防循环 header 和重放审计。

### 验收

- 失败消息进入 retry topic。
- 重试耗尽进入 DLT。
- 主 topic 后续消息不被长期阻塞。
- 文档说明 retry topic 对顺序的影响。

### 面试表达

> retry topic 解决的是非阻塞重试，但会牺牲一部分原始顺序语义，也会增加 topic、listener 和排障复杂度。不是所有失败都应该 retry topic，短暂少量失败用 blocking retry 更简单。

## 场景 10：Schema 演进

### 学习目标

- 理解事件长期兼容。
- 能回答消费者滞后升级。

### 当前状态

- `OrderPreviewKafkaEvent` 有 `eventVersion=1`。
- JSON 序列化。
- 没有 Schema Registry。
- demo lab 已落地 V2 JSON：`POST /api/kafka-demo/schema-v2` 新增 `channel` 和 `newOptionalField`，V1 consumer 只读取稳定字段并记录 ignored fields。

### 演示

```bash
curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/schema-v2?key=schema-1001'

curl -u user:user123 http://localhost:8080/api/kafka-demo/state
```

### 后续实施

- 文档化字段删除和重命名策略。
- 设计 Avro/Protobuf + Schema Registry 的生产方案。

### 验收

- 旧消费者忽略新增字段。
- 必填字段缺失进入 DLT 或明确失败。
- 文档能解释向后兼容和向前兼容。

### 面试表达

> 事件一旦上 Kafka 就不是单服务内部 DTO。Schema 演进要按消费者滞后升级考虑，优先新增可选字段，不直接删除或重命名字段，复杂场景上 Schema Registry 做兼容检查。

## 场景 11：Kafka 事务

### 学习目标

- 理解 Kafka 内部 read-process-write exactly-once。
- 不把 Kafka EOS 误认为业务 exactly-once。

### 当前状态

- demo lab 已实现 producer transaction 提交和回滚可见性示例。
- `KafkaDemoMessagingConfig` 的 demo consumer 使用 `isolation.level=read_committed`。
- `OrderKafkaProfileIT.kafkaDemoTransactionCommitIsVisibleAndAbortIsHidden` 验证已提交事务可被消费，abort 事务不可见。

### 演示

```bash
curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/transaction/commit?key=tx-commit-1001'

curl -u user:user123 -X POST \
  'http://localhost:8080/api/kafka-demo/transaction/abort?key=tx-abort-1001'

curl -u user:user123 http://localhost:8080/api/kafka-demo/state
```

### 边界

当前教学场景：

```text
spring3.kafka-demo.tx.input.v1
  -> read_committed transaction consumer
  -> spring3.kafka-demo.tx.audit.v1
  -> audit event 使用 transactional KafkaTemplate 写出
```

已经涉及：

- `transaction-id-prefix`。
- `isolation.level=read_committed`。
- 集成测试验证事务提交和回滚。

后续若要演示完整 Kafka 内 read-process-write EOS，需要引入 Kafka transaction manager，并把 consumed offset 通过 `sendOffsetsToTransaction` 与 output topic 放进同一个事务。

### 不做

- 不把数据库、HTTP、短信纳入 Kafka 事务。
- 不宣称订单预览业务 exactly-once。

### 面试表达

> Kafka 事务适合 Kafka 内部 read-process-write，能把消费 offset 和输出 topic 写入放在同一个事务里。它不能覆盖外部数据库和 HTTP 副作用，所以订单支付这类业务仍需要 outbox/inbox 和业务幂等。

## 场景 12：安全、权限和多环境治理

### 学习目标

- 理解生产 Kafka 权限边界。
- 避免把敏感配置提交到仓库。

### 当前状态

- 本地 Kafka 无认证。
- 未配置 SASL_SSL、mTLS、ACL。
- `GET /api/kafka-demo/security-template` 返回安全配置模板和 ACL 示例，只用于说明，不包含真实密钥。

### 演示

```bash
curl -u user:user123 http://localhost:8080/api/kafka-demo/security-template
```

### 后续实施

- 增加安全配置模板文档。
- 增加 topic/group 命名规范。
- 增加多环境隔离说明。

示例模板：

```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
    properties:
      security.protocol: SASL_SSL
      sasl.mechanism: SCRAM-SHA-512
      sasl.jaas.config: ${KAFKA_SASL_JAAS_CONFIG}
```

### 面试表达

> 生产 Kafka 不能让所有服务共用一个超级账号。应按 topic 和 consumer group 配 ACL，按环境隔离 bootstrap 和凭证，密码、证书、JAAS 配置走密钥管理，不进代码仓库。

## 场景 13：性能调优和容量评估

### 学习目标

- 理解吞吐、延迟、可靠性的取舍。
- 能做分区数和消费者并发规划。

### 当前状态

- 未做压测。
- 已有 topic 分区数和 listener concurrency 配置。
- `GET /api/kafka-demo/capacity-plan` 提供教学级分区估算，输入峰值 QPS、单条消费耗时和目标分区吞吐。

### 演示

```bash
curl -u user:user123 \
  'http://localhost:8080/api/kafka-demo/capacity-plan?peakMessagesPerSecond=5000&consumerMessageCostMs=20&targetPartitionThroughput=1000'
```

### 后续实施

- 增加本地压测脚本或测试 helper。
- 对比 `linger.ms`、`batch.size`、`compression.type`。
- 观察 producer latency、consumer lag、处理耗时。
- 增加分区数规划公式和示例。

### 验收

- 能输出一组基线吞吐和延迟。
- 能解释瓶颈在 producer、broker、consumer 还是下游。

### 面试表达

> Kafka 调优不能只看 TPS。要同时看端到端延迟、broker 资源、consumer lag、错误率和下游承载。partition 也不是越多越好，过多会增加元数据、文件句柄和恢复成本。

## 场景 14：与 RabbitMQ / RocketMQ / 同步调用选型

### 学习目标

- 能结合项目说明为什么用 Kafka，以及什么时候不用。

### 当前项目

- RabbitMQ profile 已有 exchange/queue/routing、retry、DLQ。
- Kafka profile 用于 event stream、partition、offset、lag、replay。
- 同步 HTTP 调用用于订单预览实时查询 catalog。
- `GET /api/kafka-demo/selection-matrix` 输出 Kafka、RabbitMQ、RocketMQ 和当前项目的选型提示。

### 演示

```bash
curl -u user:user123 http://localhost:8080/api/kafka-demo/selection-matrix
```

### 选型表达

| 需求 | 更合适 |
| --- | --- |
| 实时查询商品价格 | 同步 HTTP/Feign/RestClient |
| 订单事件多系统订阅 | Kafka |
| 复杂 routing key 路由 | RabbitMQ |
| 高吞吐行为日志 | Kafka |
| 传统任务队列 | RabbitMQ |
| 事务消息、延迟消息强需求 | RocketMQ 可评估 |

### 面试表达

> 当前项目不是为了堆组件，而是用 RabbitMQ 学业务队列模型，用 Kafka 学事件流模型。订单预览实时返回仍是同步调用；Kafka 用于异步事件扩散和可回放数据流。

## 后续实施优先级

| 顺序 | 任务 | 交付 |
| --- | --- | --- |
| 1 | 已完成：P0 文档和 demo lab | 本文、学习指南、题库、runbook、`KafkaDemo*` 代码 |
| 2 | 已完成：retry topic / Schema / transaction / lag 基础演示 | 代码、测试、文档 |
| 3 | DLT 可控重放工具 | dry run、限速、审计和防循环 |
| 4 | lag/rebalance 生产观测 | PromQL、Grafana panel、多实例故障演练 |
| 5 | 完整 Kafka 内 read-process-write EOS | transaction manager、`sendOffsetsToTransaction` 测试 |
| 6 | Schema Registry | Avro/Protobuf、兼容策略和本地基础设施 |
| 7 | 性能压测 | 脚本、基线结果、调优记录 |

## 验收命令

默认构建不应依赖 Kafka：

```bash
./mvnw test
```

Kafka 专题编译：

```bash
./mvnw -Pkafka -pl order-service -am test -DskipTests
```

Kafka 集成测试：

```bash
./mvnw -Pkafka,integration-test -pl order-service -am \
  -Dtest=none \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=OrderKafkaProfileIT \
  -Dfailsafe.failIfNoSpecifiedTests=false \
  verify
```

Compose 校验：

```bash
docker compose -f platform/kafka/docker-compose.yml config
```

## 面试总表达

> 这个项目里的 Kafka 是可选专题，不污染默认 profile。已落地订单预览 producer/consumer、manual ack、eventId 幂等、同 key 顺序、blocking retry、DLT、指标和 Testcontainers；也补了 demo lab 覆盖 retry topic、Schema V2 兼容、事务 commit/abort 可见性、lag/rebalance 状态、安全模板、容量规划和 MQ 选型。生产级 outbox/inbox、持久化幂等、Schema Registry、真实 SASL_SSL、Grafana 面板、DLT 重放工具和完整 Kafka 内 offset+output EOS 仍是设计型或后续能力，面试时我会明确区分。
