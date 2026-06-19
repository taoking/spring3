# Kafka 资深后端学习指南

## 目标读者

这份文档面向已经会写 Spring Boot 业务代码、需要把 Kafka 从“会用”提升到“能讲清生产语义、故障排查和面试追问”的后端工程师。

当前项目的 Kafka 代码不是生产订单系统，而是一个学习实验室：

- Kafka 只在 Maven profile `kafka` 和 Spring profile `kafka` 下启用。
- 默认 profile 不依赖 Kafka。
- 当前只演示订单预览事件 `OrderPreviewCreatedEvent`。
- 当前幂等 store 是内存实现，只能证明消费端幂等思路，不能当作生产方案。
- 当前没有数据库、Redis、Schema Registry 和真实生产 Kafka 集群。

面试时要主动区分三类能力：

| 类型 | 表达方式 | 当前项目示例 |
| --- | --- | --- |
| 已落地可演示 | 可以指向代码、配置、测试和命令 | producer、consumer、manual ack、DLT、内存幂等、Testcontainers |
| 设计型覆盖 | 有方案、有边界，但未接真实外部依赖 | outbox/inbox、持久化去重、Schema Registry、SASL_SSL |
| 生产经验表达 | 解释原理、故障和取舍 | lag、rebalance、分区规划、容量评估、重放治理 |

## 1. Kafka 是什么

Kafka 是分布式事件流平台。它可以当消息队列使用，但它的核心抽象更接近“可持久化、可回放、可分区并行消费的事件日志”。

适合：

- 高吞吐事件流，例如订单事件、支付事件、行为日志。
- 多消费者订阅同一份数据，例如订单服务产生事件，营销、风控、搜索、数仓各自消费。
- 需要保留一段时间并可回放的消息。
- 日志采集、数据管道、CDC、流处理上游。

不适合：

- 需要复杂业务路由和临时队列的场景，RabbitMQ 往往更直接。
- 强实时 RPC，不应该用 Kafka 等同步结果。
- 消息数量很少但每条都要求人工工作流式确认的场景。
- 没有幂等、重放和治理能力的关键外部副作用场景。

一句面试回答：

> Kafka 不只是 MQ，它是持久化事件日志。我的使用重点不是“发出去就完”，而是 topic/partition 建模、offset 语义、消费幂等、DLT 治理、lag 排障和重放边界。

## 2. 核心概念

| 概念 | 说明 | 面试重点 |
| --- | --- | --- |
| broker | Kafka 服务节点 | broker 宕机后副本和 leader election 如何工作 |
| cluster | broker 组成的集群 | controller、metadata、KRaft/ZooKeeper 时代差异 |
| topic | 消息逻辑分类 | 命名、分区数、保留策略、权限 |
| partition | topic 的物理分片和有序日志 | Kafka 只保证 partition 内有序 |
| replica | partition 副本 | leader/follower、ISR、复制因子 |
| leader | partition 的读写副本 | producer/consumer 通常与 leader 交互 |
| follower | 从 leader 复制数据的副本 | 落后过多会退出 ISR |
| ISR | in-sync replicas | `acks=all` 依赖 ISR 达成可靠写入 |
| offset | partition 内递增位置 | consumer 提交 offset 表示消费进度 |
| producer | 生产消息 | key、batch、ack、retry、idempotence |
| consumer | 拉取消息 | poll、commit、rebalance、lag |
| consumer group | 消费组 | 同一 group 内一个 partition 同时只给一个 consumer |
| record key | 分区路由键 | 决定同一业务维度是否有序 |
| record header | 元数据 | eventId、traceId、requestId、schema version |

当前项目映射：

| Kafka 概念 | 当前项目落点 |
| --- | --- |
| topic | `spring3.order-preview.events.v1` |
| DLT topic | `spring3.order-preview.dlt.v1` |
| producer | `KafkaOrderPreviewEventPublisher` |
| consumer | `KafkaOrderPreviewConsumer` |
| key | `OrderPreviewKafkaEvent.partitionKey()`，当前等于订单预览 ID |
| offset commit | `Acknowledgment.acknowledge()` |
| group id | `spring3-order-preview` |
| eventId | 当前等于订单预览 ID，用于幂等 |

## 3. Broker 与存储机制

Kafka 吞吐高不是单点原因，而是一组设计叠加：

- topic 拆成多个 partition，天然并行。
- partition 是追加日志，写入偏顺序。
- producer 批量发送，减少网络和系统调用开销。
- broker 充分利用 OS page cache。
- 日志文件按 segment 滚动，配合 offset index/time index 查找。
- 消费端按 offset 顺序拉取，适合批量和顺序 IO。
- 网络传输可利用零拷贝路径减少用户态和内核态拷贝。
- 压缩可以用 CPU 换网络和磁盘。

### Log、segment、index

一个 partition 在 broker 上表现为一组日志段：

```text
topic-partition/
  00000000000000000000.log
  00000000000000000000.index
  00000000000000000000.timeindex
  00000000000000123456.log
  00000000000000123456.index
  00000000000000123456.timeindex
```

需要掌握：

- `.log` 存数据。
- `.index` 存 offset 到物理位置的稀疏索引。
- `.timeindex` 支持按时间找 offset。
- segment 滚动降低单文件大小和清理成本。
- retention 根据时间或大小清理。
- compact 根据 key 保留较新值，适合状态快照类 topic，不适合所有业务事件。

### HW、LEO、ISR

| 概念 | 说明 |
| --- | --- |
| LEO | log end offset，副本当前日志末尾位置 |
| HW | high watermark，消费者可见的已提交位置 |
| ISR | 与 leader 保持同步的副本集合 |

面试关键点：

- leader 收到消息不代表所有 consumer 立刻可见。
- `acks=all` 是等待 ISR 内副本确认，不是等待所有副本确认。
- 如果 ISR 太小，可靠性下降；如果 `min.insync.replicas` 配得太严格，可用性下降。
- 生产环境通常不建议开启 unclean leader election，因为可能选出落后副本导致数据丢失。

## 4. 写入链路：Producer

Producer 发送过程可以分成：

1. 序列化 key/value。
2. 根据 topic metadata 找 partition leader。
3. 根据 key 或 partitioner 选择 partition。
4. 放入 RecordAccumulator，按 batch 聚合。
5. Sender 线程按 broker 发送 ProduceRequest。
6. broker leader 追加日志，复制到 follower。
7. 满足 ack 条件后返回结果。
8. callback 得到 topic、partition、offset 或异常。

当前项目：

- `KafkaOrderPreviewEventPublisher` 监听应用内 `OrderPreviewCreatedEvent`。
- 使用 `KafkaTemplate<String, OrderPreviewKafkaEvent>` 发送。
- 发送 key 是 `partitionKey`。
- header 携带 `eventId`、`eventType`、`eventVersion`、`source`、`X-Request-Id`、`traceId`、`traceparent`。
- callback 成功时记录 topic/partition/offset，失败时增加 `orders.preview.kafka.send.failed.total`。

### Producer 关键配置

| 配置 | 当前值 | 生产含义 | 追问 |
| --- | --- | --- | --- |
| `acks` | `all` | 等待 ISR 确认，提高可靠性 | ISR 太小怎么办 |
| `enable.idempotence` | `true` | 避免 producer retry 导致 broker 写入重复批次 | 是否等于业务幂等 |
| `retries` | `10` | 可重试 broker/网络瞬时错误 | 重试耗尽怎么处理 |
| `max.in.flight.requests.per.connection` | `5` | 单连接未确认请求数 | 幂等关闭时可能乱序 |
| `delivery.timeout.ms` | `30000` | 发送总超时边界 | 过短会误失败，过长会拖延告警 |
| `request.timeout.ms` | `5000` | 单次请求响应超时 | broker 慢时影响重试 |
| `max.block.ms` | `1000` | metadata 或 buffer 阻塞最大时间 | broker 不可用时业务线程阻塞 |

需要补充学习的吞吐配置：

| 配置 | 作用 | 取舍 |
| --- | --- | --- |
| `batch.size` | 单 batch 大小 | 大 batch 提高吞吐，但增加延迟和内存 |
| `linger.ms` | 等待更多消息成 batch | 增加少量延迟换吞吐 |
| `compression.type` | 压缩算法 | 降低网络/磁盘，增加 CPU |
| `buffer.memory` | producer 缓冲区 | 太小会阻塞，太大可能掩盖故障 |

面试回答模板：

> 我会把生产可靠性拆成 producer、broker 和 consumer。producer 侧用 `acks=all`、retry、idempotence、callback 和超时边界；broker 侧配复制因子、ISR、`min.insync.replicas`；consumer 侧处理成功后提交 offset。即便这样也只能做到消息层面更可靠，业务副作用仍要靠幂等和对账。

## 5. 读取链路：Consumer

Consumer 是 pull 模型：

1. consumer 加入 group。
2. group coordinator 触发分区分配。
3. 每个 consumer 只消费分配到的 partition。
4. consumer 调用 `poll()` 拉取数据。
5. 业务处理消息。
6. 提交 offset。

当前项目：

- `enable-auto-commit=false`。
- `ack-mode=manual_immediate`。
- listener 成功处理后调用 `acknowledgment.acknowledge()`。
- 重复 eventId 也会 ack，避免重复消息一直阻塞。
- 失败抛异常，交给 `DefaultErrorHandler` 重试和 DLT。

### Offset 提交语义

| 提交时机 | 语义 | 风险 |
| --- | --- | --- |
| 处理前提交 | at-most-once | 处理失败会丢消息 |
| 处理后提交 | at-least-once | 提交失败、重启、rebalance 会重复 |
| Kafka 事务提交 offset 和 output record | Kafka 内 exactly-once | 不覆盖数据库、HTTP、邮件等外部副作用 |

当前项目选择处理成功后手动 ack，因此目标是 at-least-once + 消费幂等。

### Rebalance

rebalance 是 group 内 partition 分配变化。

常见触发：

- consumer 启动或停止。
- consumer 长时间不 poll，超过 `max.poll.interval.ms`。
- 心跳异常，超过 `session.timeout.ms`。
- topic partition 数变化。
- consumer group 订阅 topic 变化。

影响：

- 发生 rebalance 时，部分 partition 会暂停消费。
- 未提交 offset 的消息可能被重新消费。
- rebalance 频繁会导致 lag 上涨。

降低影响：

- 控制单条消息处理耗时，避免长时间不 poll。
- 合理配置 `max.poll.records` 和 `max.poll.interval.ms`。
- 使用 cooperative sticky assignor 减少全量撤销。
- 使用 static membership 减少短暂重启带来的抖动。
- 消费者优雅停机，及时提交和离组。

## 6. 顺序性

Kafka 顺序性边界：

- 同一 partition 内有序。
- 不同 partition 之间没有全局顺序。
- 同一 key 只有稳定路由到同一 partition，才有该 key 维度的顺序。
- 单 partition 内如果业务处理再异步并发，也可能在业务侧打乱顺序。

当前项目 key 策略：

- 当前 `partitionKey` 等于订单预览 ID。
- 这能保证同一订单预览相关事件进入同一 partition。
- 如果后续要保证同一 SKU 的库存事件顺序，key 可能要选 `sku`，但热点 SKU 会造成 partition skew。

key 选择对比：

| key | 优点 | 风险 |
| --- | --- | --- |
| `orderId` | 同一订单状态有序，分布通常较均匀 | 不能保证同一 SKU 维度有序 |
| `sku` | 同一商品库存事件有序 | 热点 SKU 造成倾斜 |
| `userId` | 用户维度风控/画像有序 | 大客户或活跃用户倾斜 |
| 固定 key | 全局有序 | 退化为单 partition，吞吐差 |

面试回答模板：

> Kafka 不能保证全局顺序，只能保证 partition 内顺序。业务上先确定有序维度，再用这个维度做 key。选 `orderId` 是订单状态有序，选 `sku` 是库存维度有序。扩 partition 会改变 key 到 partition 的映射，所以严格顺序场景要提前规划分区数或做迁移方案。

## 7. 可靠性、重复和幂等

Kafka 面试里“如何保证不丢不重”不能一句话回答。要拆开说：

| 层次 | 防丢措施 | 仍然可能的问题 |
| --- | --- | --- |
| producer | `acks=all`、retry、idempotence、callback、超时告警 | 发送结果未知、重试耗尽、应用崩溃 |
| broker | 多副本、ISR、`min.insync.replicas`、磁盘监控 | 多副本同时故障、错误清理、unclean election |
| consumer | 处理成功后提交 offset、失败不提交 | 处理成功但 commit 失败会重复 |
| business | 幂等键、唯一约束、状态机、outbox/inbox、对账 | 没有持久化幂等就无法抗重启 |

重复消息来源：

- producer 发送超时但 broker 已写入，producer 重试。
- consumer 处理成功但 offset commit 失败。
- rebalance 前处理了消息但没提交。
- 应用重启。
- DLT 或历史消息重放。
- 多生产者使用不同 eventId 发送了业务等价消息。

### 幂等 producer 不等于业务幂等

幂等 producer 主要解决 producer retry 导致的 broker 端重复批次写入。它不能阻止：

- 业务代码调用两次发送。
- 两个服务实例生成不同 eventId 的重复业务事件。
- consumer 处理成功后 commit 失败导致重新投递。
- DLT 重放。
- 消费者对数据库、HTTP、短信等外部系统重复副作用。

当前项目的 `ProcessedKafkaEventStore`：

- 用 eventId 做内存去重。
- 能在测试中证明“重复 eventId 只处理一次”。
- 进程重启后丢失状态，不能用于生产。

生产幂等方案：

| 方案 | 适合 | 注意点 |
| --- | --- | --- |
| 数据库唯一键 | 订单、支付、库存等强业务副作用 | 和业务事务绑定 |
| inbox 表 | 消费消息再做本地事务 | 需要清理和重放治理 |
| outbox 表 | 业务写库和事件记录同事务提交 | 需要 relay/CDC 发布 |
| Redis set/bloom | 高吞吐轻量去重 | 需要 TTL、误判和丢失风险评估 |
| compact topic | Kafka 内维护最新处理状态 | 查询和一致性设计更复杂 |
| 状态机版本号 | 订单状态流转 | 事件必须有版本或序号 |

## 8. 重试、失败分类和 DLT

失败要先分类：

| 类型 | 示例 | 处理 |
| --- | --- | --- |
| 瞬时异常 | 下游超时、临时网络错误 | retry，必要时退避 |
| 业务不可重试 | 必填字段缺失、非法状态流转 | DLT 或记录后跳过 |
| poison message | 反序列化失败、schema 不兼容 | DLT，修复后重放 |
| 下游长期不可用 | 库存服务故障、DB 慢 | 限速、熔断、DLT、告警 |

当前项目：

- 使用 `DefaultErrorHandler` + `FixedBackOff`。
- `IllegalArgumentException` 标记为不可重试。
- 失败后通过 `DeadLetterPublishingRecoverer` 进入 DLT。

Blocking retry 与 retry topic：

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| blocking retry | 简单、顺序语义清晰 | 会阻塞同 partition 后续消息 |
| retry topic | 不阻塞主消费，可做延迟退避 | topic/listener 增多，顺序和排障复杂 |

DLT 不是自动补偿。DLT 后应该：

1. 告警。
2. 确认失败类型和影响范围。
3. 修复代码、配置或脏数据。
4. 抽样验证 DLT 消息。
5. 限速重放。
6. 确认幂等和顺序风险。
7. 留审计记录。

## 9. Schema 演进

事件不是内部 Java 对象，事件一旦进入 Kafka，就可能被多个服务长期消费。Schema 演进要保守。

基本原则：

- 事件名和业务语义稳定。
- `eventVersion` 必须存在。
- 新增字段优先可选，并提供默认值。
- 不直接删除字段。
- 不直接重命名字段；新增新字段，保留旧字段一段时间。
- 枚举新增值要考虑旧消费者不认识。
- 金额、时间、ID 类型不要随意改变。

JSON、Avro、Protobuf 对比：

| 格式 | 优点 | 缺点 |
| --- | --- | --- |
| JSON | 易读，调试简单 | schema 约束弱，字段类型容易漂移 |
| Avro | 适合 Schema Registry，兼容治理成熟 | 可读性差，需要 schema 基础设施 |
| Protobuf | 跨语言、紧凑、字段编号稳定 | 事件演进要严格管理字段号 |

当前项目：

- 事件为 Java record JSON 序列化。
- 有 `eventVersion=1`。
- 没有 Schema Registry。
- 后续应增加 V1/V2 兼容测试，证明旧消费者可以忽略新增字段。

## 10. Kafka 事务和 exactly-once 边界

Kafka exactly-once 的常见误区：它不是“业务永远只执行一次”。

Kafka 事务适合：

- 从 Kafka topic A 消费。
- 处理后写 Kafka topic B。
- 把消费 offset 和输出消息放在同一个 Kafka 事务里提交。
- 下游使用 `isolation.level=read_committed`。

Kafka 事务不自动覆盖：

- 写数据库。
- 调 HTTP。
- 发短信、邮件、推送。
- 调第三方支付。
- 修改 Redis。

如果消费 Kafka 后要写数据库，常见方案仍然是：

- 数据库唯一键和状态机幂等。
- inbox 表记录已处理 eventId。
- outbox 表保证本地事务和事件发布一致。
- CDC relay 发布事件。
- 对账和补偿。

面试回答模板：

> Kafka EOS 主要覆盖 Kafka 内部 read-process-write，也就是消费 offset 和新写入 Kafka 的消息可以进同一个事务。它不能把数据库、HTTP、短信这些外部副作用自动纳入事务。订单、支付这类场景仍然要靠本地事务、outbox/inbox、唯一键、状态机和补偿。

## 11. Spring Kafka 映射

| Kafka 原生概念 | Spring Kafka 组件 | 当前项目 |
| --- | --- | --- |
| producer | `KafkaTemplate` | `KafkaOrderPreviewEventPublisher` |
| consumer | `@KafkaListener` | `KafkaOrderPreviewConsumer` |
| listener container | `ConcurrentKafkaListenerContainerFactory` | `kafkaOrderPreviewListenerContainerFactory` |
| manual commit | `Acknowledgment` | `acknowledgment.acknowledge()` |
| error handler | `DefaultErrorHandler` | `kafkaOrderPreviewErrorHandler` |
| DLT recoverer | `DeadLetterPublishingRecoverer` | 发送到 `deadLetterTopic` |
| topic declaration | `NewTopic` / `KafkaAdmin` | `KafkaOrderMessagingConfig` |
| non-blocking retry | `@RetryableTopic` / `RetryTopicConfiguration` | 当前未实现，后续 P1 |
| integration test | `spring-kafka-test` / Testcontainers | `OrderKafkaProfileIT` |

Spring Kafka 使用重点：

- listener 是否批量消费。
- ack mode 选择。
- error handler 是否会 commit recovered record。
- 反序列化失败是否能进入 DLT。
- listener concurrency 不应超过 partition 数太多。
- 是否需要 `ConsumerRecord` 元数据。
- 是否要把 retry topic 和 DLT topic 自动创建。

## 12. 观测、告警和排障

Kafka 生产排障不能只看应用日志。

应用侧指标：

- producer 成功数、失败数、发送耗时。
- consumer 成功数、失败数、重复数。
- DLT 数量。
- 单条处理耗时。
- 下游调用耗时和错误。

consumer 指标：

- lag。
- records consumed rate。
- fetch latency。
- commit latency。
- rebalance total/rate。
- last poll seconds ago。
- assigned partitions。

broker 指标：

- under replicated partitions。
- offline partitions。
- request handler idle。
- network processor idle。
- disk usage。
- bytes in/out。
- produce/fetch request latency。

排障思路：

| 现象 | 优先检查 |
| --- | --- |
| lag 上涨 | 生产速率、消费耗时、分区倾斜、下游慢、rebalance、broker 资源 |
| 消费停滞 | consumer 是否在 group、是否频繁 rebalance、是否 poison message 阻塞 |
| DLT 激增 | 最近发布、schema 变更、脏数据、下游异常 |
| producer timeout | broker leader、metadata、网络、acks/ISR、buffer 是否满 |
| 顺序错乱 | key 是否一致、是否扩分区、业务是否异步并发 |

详细命令见 [Kafka 运维排障 Runbook](kafka-operations-runbook.md)。

## 13. 安全和治理

生产 Kafka 至少要考虑：

- SASL_SSL 或 mTLS。
- ACL 限制 producer/consumer 对 topic 和 group 的权限。
- topic 命名规范。
- consumer group 命名规范。
- 配额，避免单应用打满集群。
- 多环境隔离，避免测试服务读写生产 topic。
- 密钥管理，不提交 bootstrap servers、密码、证书和 JAAS 配置到仓库。

topic 命名建议：

```text
<domain>.<aggregate>.<event-category>.v<version>
spring3.order-preview.events.v1
```

consumer group 命名建议：

```text
<system>-<bounded-context>-<purpose>
spring3-order-preview
```

## 14. 容量和性能

容量规划要先问：

- 峰值 QPS 多少。
- 单条消息平均大小和 P99 大小。
- 消费者处理一条消息平均耗时和 P99。
- 保留时间多久。
- 是否需要压缩或 compact。
- 是否要多订阅。
- 是否有严格顺序维度。
- 下游能承受多少并发。

分区数粗略思路：

```text
需要分区数 = max(生产吞吐所需分区数, 消费并发所需分区数, 顺序 key 分布所需分区数)
```

注意：

- partition 不是越多越好。
- partition 多会增加文件句柄、内存、leader election、恢复、controller metadata 成本。
- 分区数扩容会影响 key 到 partition 的映射，严格顺序场景要谨慎。

调优方向：

| 目标 | 调整 |
| --- | --- |
| 提高 producer 吞吐 | 增大 batch、增加 linger、开启压缩、增加分区 |
| 降低 producer 延迟 | 降低 linger、控制 batch、检查 broker 延迟 |
| 提高 consumer 吞吐 | 增加 partition 和 consumer、调整 `max.poll.records`、优化业务处理 |
| 降低 lag | 扩消费、优化下游、拆热点 key、减少 rebalance |
| 降低 broker 压力 | 压缩、限流、调整保留策略、增加 broker |

## 15. 与 RabbitMQ / RocketMQ 对比

| 维度 | Kafka | RabbitMQ | RocketMQ |
| --- | --- | --- | --- |
| 核心模型 | topic、partition、offset、consumer group | exchange、queue、binding、routing key | topic、tag、queue、consumer group |
| 主要优势 | 高吞吐、事件日志、回放、多订阅 | 路由灵活、传统队列、ack/nack 简洁 | 事务消息、延迟消息、顺序消息 |
| 顺序 | partition 内有序 | 单 queue 内有序 | 顺序消息能力更贴近业务 |
| 重试 | blocking retry、retry topic、DLT | listener retry、DLX/DLQ | broker 原生重试和 DLQ |
| 典型面试点 | offset、rebalance、lag、EOS | confirm、ack、DLX、prefetch | 半消息、回查、tag、延迟级别 |

项目表达：

> 当前项目同时保留 RabbitMQ 和 Kafka 可选 profile。RabbitMQ 用来学习 exchange/queue/routing、ack、DLQ；Kafka 用来学习事件流、partition、offset、consumer group、lag、rebalance、DLT 和重放。两个都不是默认运行强依赖，避免学习项目被外部组件绑死。

## 16. 学习验收

完成 Kafka 学习后，应能不看资料回答：

- Kafka 为什么吞吐高。
- topic、partition、offset、consumer group 的关系。
- consumer group 内 partition 如何分配。
- Kafka 如何保证 partition 内顺序。
- 处理成功后提交 offset 为什么仍会重复。
- producer idempotence 和业务幂等的区别。
- `acks=all`、ISR、`min.insync.replicas` 的关系。
- lag 上涨怎么定位。
- rebalance 为什么发生，怎么降低影响。
- blocking retry 和 retry topic 怎么选。
- DLT 后如何安全重放。
- Kafka 事务解决什么，不解决什么。
- Schema 如何演进。
- Kafka、RabbitMQ、RocketMQ 如何选型。
- 当前项目哪些能力已落地，哪些只是设计型覆盖。

## 参考资料

- Apache Kafka Documentation: https://kafka.apache.org/documentation/
- Apache Kafka Design: https://kafka.apache.org/documentation/#design
- Apache Kafka Producer Configs: https://kafka.apache.org/documentation/#producerconfigs
- Apache Kafka Monitoring: https://kafka.apache.org/documentation/#monitoring
- Spring for Apache Kafka `@KafkaListener`: https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/listener-annotation.html
- Spring for Apache Kafka Non-Blocking Retries: https://docs.spring.io/spring-kafka/reference/retrytopic.html
