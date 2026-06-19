# Kafka 使用与面试专题

Kafka 是当前项目的可选消息队列专题。默认 profile 不引入 Kafka 运行依赖，只有同时使用 Maven `-Pkafka` 和 Spring `SPRING_PROFILES_ACTIVE=kafka` 时，才会编译并启用 `order-service/src/kafka/java` 下的发布者、消费者和 topic 配置。

## 深化文档入口

面向资深后端学习和面试的 Kafka 文档已拆分为：

- [Kafka 资深后端学习指南](kafka-learning-guide.md)：系统学习 Kafka 核心模型、读写链路、可靠性、顺序、幂等、事务、Schema、Spring Kafka 和生产治理。
- [Kafka 项目场景实施文档](kafka-project-scenarios.md)：把当前订单预览事件链路拆成 producer、consumer、offset、幂等、顺序、DLT、lag、Schema、事务、安全和性能场景。
- [Kafka 资深后端面试追问题库](kafka-interview-question-bank.md)：覆盖 30 个高频问题的一问、二问、三问和项目回答模板。
- [Kafka 运维排障 Runbook](kafka-operations-runbook.md)：覆盖 lag、rebalance、DLT、producer 失败、broker 故障和安全重放。
- [Kafka 资深面试覆盖度复查](kafka-coverage-review.md)：检查已落地能力、设计型能力、面试覆盖度和后续补齐优先级。

## 当前业务示例

围绕订单预览事件 `OrderPreviewCreatedEvent` 构建 Kafka 事件流：

| 能力 | 实现 |
| --- | --- |
| Producer | `KafkaOrderPreviewEventPublisher` 监听订单预览事件，发送 `OrderPreviewKafkaEvent` |
| Consumer | `KafkaOrderPreviewConsumer` 使用 `@KafkaListener` 消费主 topic |
| Topic | `spring3.order-preview.events.v1` |
| DLT | `spring3.order-preview.dlt.v1` |
| Key | 默认使用 `orderId` / `partitionKey`，保证同一 key 进入同一 partition |
| Offset | 关闭 auto commit，listener 使用 manual ack |
| 幂等 | `ProcessedKafkaEventStore` 基于 eventId 做内存去重 |
| 重试 | `DefaultErrorHandler` + `FixedBackOff` |
| 死信 | `DeadLetterPublishingRecoverer` 发布到 DLT |
| 指标 | `orders.preview.kafka.*` Micrometer Counter |
| 测试 | `OrderKafkaProfileIT` 使用 Testcontainers Kafka |

当前幂等 store 是内存实现，只适合学习和自动化测试。生产环境必须使用数据库唯一键、inbox/outbox、Redis、compact topic 或其他持久化机制。本项目明确不接入数据库和 Redis。

## Kafka Demo Lab

`order-service/src/kafka/java/com/taoking/spring3/order/messaging/kafka/KafkaDemo*` 提供一组独立于订单业务的代码演示端点，用来覆盖资深面试常问的 Kafka 场景。

| 能力 | 入口 | 代码证据 |
| --- | --- | --- |
| 基础模型 | `POST /api/kafka-demo/basic` | 发送 `KafkaDemoEvent`，状态里可看 topic/partition/offset/group |
| 重复消费和幂等 | `POST /api/kafka-demo/duplicates` | 同一 `eventId` 连发两次，consumer 只把第一次记为有效 |
| 同 key 顺序 | `POST /api/kafka-demo/ordered` | 同一 key 的事件按 `sequence` 进入同一 partition |
| manual retry topic 和 DLT | `POST /api/kafka-demo/retry-topic` | 失败进入 retry topic，超过 attempt 后进入 retry DLT |
| lag 和 rebalance 观测 | `POST /api/kafka-demo/lag`、`GET /api/kafka-demo/state` | 慢消费累积处理状态，listener 记录 assigned/revoked |
| Schema V2 兼容 | `POST /api/kafka-demo/schema-v2` | V1 视角 consumer 读取 V2 JSON，忽略新增可选字段 |
| Kafka 事务边界 | `POST /api/kafka-demo/transaction/commit`、`POST /api/kafka-demo/transaction/abort` | `read_committed` consumer 可见已提交事务，看不到 abort 事务 |
| 安全模板 | `GET /api/kafka-demo/security-template` | 返回 SASL/ACL 配置模板，不包含真实密钥 |
| 容量规划 | `GET /api/kafka-demo/capacity-plan` | 按峰值、单条处理耗时和目标分区吞吐估算 partitions |
| MQ 选型 | `GET /api/kafka-demo/selection-matrix` | 对比 Kafka、RabbitMQ、RocketMQ 和同步调用 |

事务 demo 只证明 Kafka 事务提交/回滚与 `isolation.level=read_committed` 的可见性边界；它不是订单业务 exactly-once，也没有把数据库、HTTP 或消费 offset 纳入同一个业务事务。

## 本地 Kafka

校验 Compose：

```bash
docker compose -f platform/kafka/docker-compose.yml config
```

启动 Kafka 和 Kafka UI：

```bash
docker compose -f platform/kafka/docker-compose.yml up -d
docker compose -f platform/kafka/docker-compose.yml ps
```

访问：

- Kafka bootstrap: `localhost:9092`
- Kafka UI: `http://localhost:8089`

停止：

```bash
docker compose -f platform/kafka/docker-compose.yml down
```

## 启动业务服务

先启动 `catalog-service`：

```bash
./mvnw -pl catalog-service spring-boot:run
```

启用 Kafka profile 启动 `order-service`：

```bash
SPRING_PROFILES_ACTIVE=kafka ./mvnw -Pkafka -pl order-service spring-boot:run
```

正常生产和消费：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: kafka-demo-request' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  -d '{"sku":"SKU-KAFKA-OK","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

消费失败、重试和 DLT：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-KAFKA-FAIL","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

Kafka demo lab：

```bash
curl -u user:user123 -X POST http://localhost:8080/api/kafka-demo/state/reset
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/basic?key=demo-basic'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/duplicates?eventId=demo-dup-1&key=demo-dup'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/ordered?key=order-1001&count=3'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/retry-topic?key=retry-1001&failUntilAttempt=2'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/lag?key=lag-1001&count=20&processingDelayMs=200'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/schema-v2?key=schema-1001'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/transaction/commit?key=tx-commit-1001'
curl -u user:user123 -X POST 'http://localhost:8080/api/kafka-demo/transaction/abort?key=tx-abort-1001'
curl -u user:user123 http://localhost:8080/api/kafka-demo/state
curl -u user:user123 'http://localhost:8080/api/kafka-demo/capacity-plan?peakMessagesPerSecond=5000&consumerMessageCostMs=20&targetPartitionThroughput=1000'
curl -u user:user123 http://localhost:8080/api/kafka-demo/security-template
curl -u user:user123 http://localhost:8080/api/kafka-demo/selection-matrix
```

查看指标：

```bash
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.published.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.processed.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.duplicates.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.failed.total
```

## 自动化验证

```bash
./mvnw -Pkafka -pl order-service -am test -DskipTests
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

`OrderKafkaProfileIT` 覆盖：

- 调用订单预览接口后发布并消费 Kafka event。
- event 携带 `X-Request-Id` 和 `traceparent` 中的 traceId。
- 重复 eventId 只处理一次。
- 同一 key 的多条消息按顺序消费。
- poison SKU 重试后进入 DLT，并保留原始 topic、partition、offset 和异常 header。
- demo lab 覆盖重复消费、同 key 顺序、Schema V2 兼容、lag 慢消费、retry topic 到 DLT、事务 commit 可见和 abort 不可见。

## 核心配置

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker 地址 |
| `ORDER_KAFKA_TOPIC` | `spring3.order-preview.events.v1` | 主 topic |
| `ORDER_KAFKA_RETRY_TOPIC` | `spring3.order-preview.retry.v1` | 预留 retry topic |
| `ORDER_KAFKA_DLT_TOPIC` | `spring3.order-preview.dlt.v1` | 死信 topic |
| `ORDER_KAFKA_CONSUMER_GROUP` | `spring3-order-preview` | 消费组 |
| `ORDER_KAFKA_POISON_SKU` | `SKU-KAFKA-FAIL` | 消费失败演示 SKU |
| `ORDER_KAFKA_PARTITIONS` | `3` | topic 分区数 |
| `ORDER_KAFKA_LISTENER_CONCURRENCY` | `3` | listener 并发度 |
| `ORDER_KAFKA_RETRY_BACKOFF` | `100ms` | 消费失败重试间隔 |
| `ORDER_KAFKA_RETRY_MAX_ATTEMPTS` | `1` | DLT 前重试次数 |
| `KAFKA_DEMO_TOPIC` | `spring3.kafka-demo.events.v1` | demo lab 基础 topic |
| `KAFKA_DEMO_RETRY_INPUT_TOPIC` | `spring3.kafka-demo.retry.input.v1` | demo retry 输入 topic |
| `KAFKA_DEMO_RETRY_TOPIC` | `spring3.kafka-demo.retry.wait.v1` | demo retry topic |
| `KAFKA_DEMO_RETRY_DLT_TOPIC` | `spring3.kafka-demo.retry.dlt.v1` | demo retry DLT |
| `KAFKA_DEMO_SCHEMA_TOPIC` | `spring3.kafka-demo.schema.v1` | demo Schema 兼容 topic |
| `KAFKA_DEMO_TX_INPUT_TOPIC` | `spring3.kafka-demo.tx.input.v1` | demo 事务输入 topic |
| `KAFKA_DEMO_TX_AUDIT_TOPIC` | `spring3.kafka-demo.tx.audit.v1` | demo 事务审计 topic |
| `KAFKA_DEMO_LAG_TOPIC` | `spring3.kafka-demo.lag.v1` | demo lag topic |

Producer 可靠性配置：

- `acks=all`
- `enable.idempotence=true`
- `retries=10`
- `max.in.flight.requests.per.connection=5`

Consumer 可靠性配置：

- `enable-auto-commit=false`
- `ack-mode=manual_immediate`
- 业务处理成功后显式 ack。
- 失败交给 `DefaultErrorHandler` 重试并进入 DLT。

## 事件设计

事件类型：`OrderPreviewKafkaEvent`

核心字段：

- `eventId`：幂等键，当前等于订单预览 ID。
- `eventType`：固定为 `OrderPreviewCreated`。
- `eventVersion`：当前为 `1`。
- `aggregateId` / `partitionKey`：默认使用订单预览 ID。
- `requestId`：从 `X-Request-Id` 请求头读取。
- `traceId`：优先从 `traceparent` 解析，其次读取当前 tracing span 或 MDC。
- `payload`：订单预览业务数据。

Header：

- `eventId`
- `eventType`
- `eventVersion`
- `source`
- `X-Request-Id`
- `traceId`
- `traceparent`

## 面试复盘

### 必须讲清楚

- Kafka 是分布式事件流平台，核心模型是 topic、partition、offset、producer、consumer、consumer group。
- Kafka 只保证 partition 内顺序，不保证全局顺序。
- 同一业务 key 进入同一 partition，才能保证该 key 的顺序。
- consumer group 内同一 partition 同一时刻只会分配给一个 consumer。
- 处理成功后再提交 offset 通常是 at-least-once，可能重复但不容易丢。
- 处理前提交 offset 可能丢消息。
- 重复消息来自 producer retry、consumer commit 失败、rebalance、应用重启和 DLT 重放。
- 幂等 producer 不等于业务幂等。它主要避免 producer 重试导致 broker 写入重复。
- Kafka exactly-once 主要覆盖 Kafka 内 read-process-write，不能自动覆盖数据库、HTTP、邮件等外部副作用。
- DLT 是失败隔离和排查入口，不是自动补偿。

### 高频追问

| 追问 | 回答方向 |
| --- | --- |
| 为什么 Kafka 吞吐高？ | 顺序追加写、批量、压缩、page cache、零拷贝、partition 并行 |
| 如何保证消息不丢？ | `acks=all`、副本 ISR、producer retry/idempotence、处理后提交 offset |
| 如何保证顺序？ | 同 key 同 partition，单 partition 内有序，业务侧避免同 key 并发乱序 |
| 为什么仍会重复？ | ack 丢失、commit 失败、rebalance、重启、重放 |
| 如何做消费幂等？ | eventId、业务唯一键、状态机版本、inbox/outbox、唯一约束 |
| Retry topic 和阻塞重试怎么选？ | 阻塞重试简单但阻塞 partition；retry topic 复杂但不阻塞主消费 |
| DLT 后怎么处理？ | 告警、排查、修复、可控重放，防止无限循环 |
| lag 上涨怎么排查？ | 生产速率、消费耗时、partition skew、下游慢、rebalance、broker 磁盘/网络 |
| consumer 数量超过 partition 数会怎样？ | 多余 consumer 空闲 |
| 扩 partition 有什么风险？ | key 到 partition 映射可能变化，严格顺序场景要谨慎 |

### 和 RabbitMQ / RocketMQ 对比

| 维度 | Kafka | RabbitMQ | RocketMQ |
| --- | --- | --- | --- |
| 核心模型 | topic、partition、offset、consumer group | exchange、queue、binding、routing key | topic、tag、queue、consumer group |
| 擅长 | 高吞吐事件流、日志流、多订阅 | 业务路由、传统队列、DLQ 简洁 | 事务消息、延迟消息、顺序消息 |
| 顺序 | partition 内有序 | 单队列有序，并发后需业务保证 | 顺序消息能力更贴近业务消息 |
| 重试 | error handler、retry topic、DLT | listener retry、DLX/DLQ | broker 原生重试和 DLQ |
| 面试重点 | offset、rebalance、lag、幂等、EOS 边界 | ack/nack、exchange、DLX、confirm | tag、事务半消息、延时级别、顺序消费 |
