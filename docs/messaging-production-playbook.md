# 消息队列生产语义专题

## 定位

当前项目已经有 RabbitMQ 和 Kafka 可选 profile，默认运行路径不依赖消息队列。本专题把现有“能生产消费”的基线，提升到资深面试需要的生产语义：可靠投递、幂等、顺序、重试、死信、堆积、rebalance、事务边界和组件选型。

当前约束仍然不变：不接入数据库和 Redis，因此生产级幂等表、outbox/inbox、Redis 锁和持久化去重只做设计说明，不在默认路径落代码。

## 当前项目结合点

| 组件 | 当前代码 | 已覆盖 | 生产缺口 |
| --- | --- | --- | --- |
| Kafka | `order-service/src/kafka/java` | producer/consumer、manual ack、eventId 内存幂等、同 key 顺序、DLT、Testcontainers | retry topic、consumer lag 面板、rebalance 排查、producer transaction、Schema Registry |
| RabbitMQ | `order-service/src/rabbitmq/java` | exchange/queue/binding、listener retry、DLQ、eventId 内存幂等、Testcontainers | publisher confirm、return callback、manual ack/nack、prefetch、延迟队列、堆积排查 |
| RocketMQ | 当前未接入 | 选型文档覆盖 | tag、顺序消息、延迟消息、事务半消息、消息轨迹 |

## 事件设计原则

| 字段 | 作用 | 面试追问 |
| --- | --- | --- |
| `eventId` | 消费幂等键 | 如何保证全局唯一，重复消息如何处理 |
| `eventType` | 事件类型路由 | 多事件共 topic 时如何分发 |
| `eventVersion` | 兼容演进 | 删除字段、重命名字段会怎样 |
| `aggregateId` | 聚合根 ID | 为什么不用随机 key |
| `partitionKey` / routing key | 顺序和路由 | 热点 key、扩分区、顺序破坏 |
| `requestId` | HTTP 请求关联 | 如何从网关、日志、MQ 串起来 |
| `traceId` / `traceparent` | 分布式追踪 | 异步边界如何继续追踪 |
| `occurredAt` | 业务发生时间 | 和消息投递时间有什么区别 |

生产建议：

- 事件语义使用过去式，例如 `OrderPreviewCreated`。
- `eventId` 必须稳定，重试和重放不能生成新 ID。
- 不在事件中放敏感信息、明文 token 或不可兼容的大对象。
- 事件版本只能兼容增加字段，删除或改名需要新版本。

## Kafka 深化

### 可靠生产

当前配置已经使用 `acks=all`、`enable.idempotence=true`、`retries=10`。面试需要能说清这些配置的边界：

| 配置 | 解决什么 | 不能解决什么 |
| --- | --- | --- |
| `acks=all` | leader 等待 ISR 副本确认 | ISR 太小时仍可能丢，吞吐会下降 |
| `enable.idempotence=true` | producer 重试导致的 broker 端重复写入 | 不能保证消费者业务幂等 |
| `retries` | 瞬时网络失败重试 | 无限重试会放大延迟 |
| `delivery.timeout.ms` | 限制单条消息最大投递时间 | 太短会误判失败，太长会拖慢反馈 |
| `max.in.flight.requests.per.connection` | 控制并发请求 | 配错会影响顺序或吞吐 |

追问要点：

- producer callback 成功只代表 broker 接收成功，不代表消费者处理成功。
- 生产端幂等不是业务 exactly-once。
- topic 副本数、`min.insync.replicas`、ISR 健康度必须和 `acks=all` 配套。

### 顺序性

Kafka 只保证 partition 内有序。当前项目使用 `orderId` / `partitionKey` 作为 message key，同一 key 进入同一 partition。

| 场景 | 风险 | 处理 |
| --- | --- | --- |
| 同一订单多事件 | 必须顺序处理 | 使用 `orderId` 作为 key，避免同 key 并发乱序 |
| 热点 SKU | 单 partition 堆积 | 不用 SKU 做 key，或拆分热点、增加业务维度 |
| 扩分区 | key 到 partition 映射变化 | 严格顺序 topic 谨慎扩分区，必要时新建 topic |
| listener concurrency | 并发提升吞吐 | concurrency 不超过 partition 数，单 partition 仍串行 |

面试回答底线：不要承诺 Kafka 全局有序。要说明“同一业务 key、同一 partition、同一 consumer 实例内有序”。

### Offset 和幂等

当前 listener 使用 manual ack，业务处理成功后提交 offset，这是典型 at-least-once。

| 失败点 | 结果 | 处理 |
| --- | --- | --- |
| 处理前提交 offset | 可能丢消息 | 不建议，除非业务允许丢 |
| 处理成功后提交 offset 失败 | 会重复消费 | 消费端幂等 |
| 应用处理成功后崩溃 | 会重复消费 | 幂等表、状态机版本、inbox |
| DLT 修复后重放 | 会重复副作用 | 重放前保留原 eventId |

当前 `ProcessedKafkaEventStore` 是内存实现，只能证明接口形态和测试语义。生产实现建议：

- 数据库唯一键：`event_id` 唯一索引。
- Inbox：先记录事件，再执行业务。
- 状态机版本：只允许状态向前迁移。
- Compact topic：部分场景可保存已处理 key，但不适合所有业务副作用。

### 重试和 DLT

当前使用 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`，属于阻塞重试。

| 方案 | 优点 | 风险 |
| --- | --- | --- |
| blocking retry | 简单，顺序语义清楚 | 阻塞同 partition 后续消息 |
| retry topic | 不阻塞主 topic，延迟可控 | topic 变多，顺序语义更复杂 |
| DLT | 隔离 poison message | 不是自动补偿，需要告警和重放流程 |

生产建议：

- 瞬时异常短重试，例如网络抖动、临时 5xx。
- 参数错误、schema 不兼容直接 DLT。
- DLT 消息保留原 topic、partition、offset、eventId、异常类型和堆栈摘要。
- 重放前先修复消费者或数据，再按 eventId 幂等处理。

### Consumer Lag 排查

本地 Kafka 容器启动后可以查看消费组：

```bash
docker exec spring3-kafka \
  /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group spring3-order-preview
```

排查顺序：

1. 看生产速率是否突增。
2. 看消费者处理耗时是否上升。
3. 看 partition 是否倾斜，是否某个 key 热点。
4. 看 rebalance 是否频繁，是否有实例频繁重启。
5. 看下游依赖是否变慢，例如 HTTP、数据库、第三方接口。
6. 看 broker 磁盘、网络、ISR、controller 是否异常。

面试追问：

- lag 是消息数还是时间？业务更关心哪一个？
- consumer 数量超过 partition 数会怎样？
- 为什么频繁 rebalance 会导致延迟抖动？
- lag 清零是否代表业务恢复？

### Kafka Transaction 边界

Kafka transaction 适合 Kafka 内 read-process-write：从 topic A 读，处理后写 topic B，并提交 offset 到事务中。

不能自动覆盖：

- 数据库写入。
- Redis 修改。
- HTTP 调用。
- 邮件、短信、第三方 API。

本项目不接数据库，因此只说明边界：如果未来增加 DB profile，需要使用 outbox/inbox 或事务消息方案，而不是把 Kafka EOS 误当成业务 EOS。

## RabbitMQ 深化

### 发布确认

RabbitMQ 生产端需要区分 confirm 和 return：

| 机制 | 表示什么 | 典型处理 |
| --- | --- | --- |
| publisher confirm ack | broker 已接收消息 | 记录成功，更新 outbox 状态 |
| publisher confirm nack | broker 未确认 | 重试或进入补偿 |
| return callback | exchange 找不到可路由 queue | 修正 routing key/binding，告警 |

面试陷阱：confirm ack 不代表消费者处理成功，只代表 broker 接收。

### 消费 ack 和 prefetch

| 配置 | 作用 | 风险 |
| --- | --- | --- |
| auto ack | 收到即确认 | 处理失败会丢 |
| manual ack | 处理成功后 ack | 代码复杂，但可靠 |
| nack/requeue | 失败后重回队列 | poison message 可能无限循环 |
| prefetch | 控制单 consumer 未确认消息数量 | 太大导致消息堆在某个消费者，太小吞吐低 |

生产建议：

- 业务成功后 ack。
- 可重试异常 nack/requeue 或进入 retry 队列。
- 不可重试异常进入 DLQ。
- 配置合理 prefetch，避免单实例占用大量未确认消息。

### 堆积排查

本地 RabbitMQ 容器启动后可以查看队列：

```bash
docker exec spring3-rabbitmq \
  rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
```

排查顺序：

1. `messages_ready` 高：消费者处理不过来或消费者不在线。
2. `messages_unacknowledged` 高：消费者拿到消息但处理慢或卡死。
3. consumers 为 0：监听容器未启动、连接失败或权限错误。
4. DLQ 增长：poison message、重试耗尽或业务异常。
5. routing 错误：exchange、routing key、binding 不匹配。

## RocketMQ 设计补充

RocketMQ 当前不接入代码，保留为面试设计专题。

| 能力 | 面试重点 | 和当前项目结合 |
| --- | --- | --- |
| tag | topic 内二级过滤 | `OrderPreviewCreated`、`OrderPaid` 可用 tag 区分 |
| 顺序消息 | 同一 sharding key 进入同一 queue | 用 `orderId` 做 sharding key |
| 延迟消息 | 固定延迟级别或延迟时间 | 订单超时取消、延迟补偿 |
| 事务半消息 | producer 本地事务和 broker 回查 | 当前无 DB，只能说明边界 |
| 消费重试/DLQ | broker 管理重试和死信 | 和 Kafka/RabbitMQ 对比 |

追问底线：

- RocketMQ 事务消息也不能替代所有分布式事务。
- 顺序消息会降低并行度，要按业务 key 缩小有序范围。
- tag 过滤适合粗粒度类型，不要把复杂业务查询塞进 MQ。

## 选型取舍

| 场景 | 推荐 | 理由 |
| --- | --- | --- |
| 高吞吐事件流、日志流 | Kafka | partition 并行、顺序追加、生态成熟 |
| 传统业务异步、灵活路由 | RabbitMQ | exchange/queue/binding 语义清晰 |
| 国内业务消息、事务/延迟/顺序 | RocketMQ | 事务半消息、延迟消息、顺序消息常见 |
| 严格全局顺序 | 谨慎使用 MQ | 全局顺序通常牺牲吞吐和可用性 |
| 需要外部副作用 exactly-once | MQ + 幂等/outbox | MQ 本身不能覆盖所有外部系统 |

## 面试追问清单

| 追问 | 回答要点 |
| --- | --- |
| Kafka 如何保证不丢消息？ | producer `acks=all`、副本 ISR、重试、处理成功后提交 offset |
| Kafka 为什么仍会重复？ | ack/commit 失败、rebalance、重启、DLT 重放 |
| Kafka exactly-once 能否保证数据库 exactly-once？ | 不能，只覆盖 Kafka 内事务边界 |
| retry topic 为什么不阻塞主 topic？ | 失败消息转移到延迟 topic，主 partition 继续消费 |
| DLT 怎么重放？ | 修复原因、保留 eventId、限速重放、监控重复和失败 |
| RabbitMQ confirm 和 ack 区别？ | confirm 是生产到 broker，ack 是消费者处理完成 |
| RabbitMQ return callback 什么时候触发？ | exchange 找不到匹配 queue，消息不可路由 |
| prefetch 配太大会怎样？ | 消息堆在某个消费者，其他消费者空闲，恢复慢 |
| 顺序消息和吞吐怎么取舍？ | 有序范围越大，吞吐越低；按业务 key 局部有序 |
| 消息堆积怎么查？ | lag/ready/unacked、消费者数、处理耗时、下游慢、broker 资源 |
| 如何设计消息幂等？ | eventId、唯一约束、inbox、状态机、重放不换 ID |
| 如何做事件版本兼容？ | 只增不删、默认值、消费者先兼容、必要时新 topic |

## 验收清单

- 能基于当前订单预览事件解释 Kafka 和 RabbitMQ 的消息生命周期。
- 能明确当前内存幂等实现只是 demo，不宣称生产 exactly-once。
- 能解释 Kafka partition 顺序、consumer group、offset commit 和 rebalance。
- 能解释 RabbitMQ exchange、queue、binding、confirm、ack、prefetch 和 DLQ。
- 能给出 lag、队列堆积、DLT 增长的排查步骤。
- 能说明 RocketMQ 的 tag、顺序、延迟和事务半消息适用场景。
