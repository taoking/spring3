# Kafka 资深后端面试追问题库

## 使用方式

这份题库不是背诵答案，而是训练回答结构。每个问题都按三层组织：

- 一问：基础问题，验证概念是否清楚。
- 二问：生产追问，验证边界和故障意识。
- 三问：项目追问，验证是否真的能结合当前项目讲清楚。

回答时优先使用结构：

```text
先给结论 -> 拆层次 -> 结合项目 -> 说明边界 -> 给排障或验收方式
```

当前项目表达底线：

- 已落地：producer、consumer、manual ack、内存幂等、blocking retry、DLT、同 key 顺序测试、Testcontainers。
- 设计型：持久化幂等、outbox/inbox、retry topic、Kafka transaction、Schema Registry、安全认证、生产 lag 面板。
- 不要把内存幂等说成生产级 exactly-once。

## 1. Kafka 是什么，和普通 MQ 有什么区别

一问：

- Kafka 是什么？
- 和 RabbitMQ 这类消息队列有什么区别？

回答要点：

- Kafka 是分布式事件流平台，也可以作为消息队列使用。
- 核心是持久化、可分区、可回放的追加日志。
- RabbitMQ 更强调 exchange/queue/routing，Kafka 更强调 topic/partition/offset/consumer group。

二问：

- 为什么说 Kafka 更适合事件流？
- Kafka 是否适合所有异步任务？

回答要点：

- Kafka 支持多订阅、保留时间内回放、高吞吐顺序日志。
- 不适合复杂低吞吐业务路由、临时队列、强人工确认工作流。

三问：

- 当前项目为什么既有 RabbitMQ 又有 Kafka？

项目回答：

- RabbitMQ 用来学习 exchange、queue、routing key、DLQ。
- Kafka 用来学习 topic、partition、offset、consumer group、lag、rebalance、重放。
- 两者都通过独立 profile 隔离，不污染默认启动。

常见坑：

- 只说 Kafka 是“高吞吐 MQ”，没有说事件日志和可回放。
- 无脑说 Kafka 比 RabbitMQ 好。

## 2. Kafka 为什么吞吐高

一问：

- Kafka 为什么性能高？

回答要点：

- partition 并行。
- 顺序追加写。
- 批量发送和批量拉取。
- page cache。
- zero copy。
- 压缩。
- consumer pull 模型可控。

二问：

- 顺序写是否意味着 Kafka 永远不慢？
- page cache 有什么风险？

回答要点：

- broker 磁盘、网络、页缓存命中、刷盘策略、分区数、请求队列都会影响性能。
- page cache 依赖 OS 内存，内存不足、IO 抖动、磁盘满都会影响延迟。

三问：

- 当前项目如何证明吞吐？

项目回答：

- 当前没有做压测，只做功能和语义验证。
- 后续要增加 producer batch、compression、consumer concurrency、lag 的基线测试。

常见坑：

- 只说“顺序写”，漏掉 batch、page cache、zero copy 和分区并行。

## 3. topic、partition、offset、consumer group 的关系

一问：

- Kafka 的 topic、partition、offset 是什么？

回答要点：

- topic 是逻辑分类。
- partition 是 topic 的物理分片和有序日志。
- offset 是 partition 内的位置。
- consumer group 是一组消费者共同消费一个 topic。

二问：

- 一个 partition 同时能被同一个 group 的几个 consumer 消费？
- consumer 数大于 partition 数会怎样？

回答要点：

- 同一 group 内，一个 partition 同一时刻只分配给一个 consumer。
- consumer 数超过 partition 数，多余 consumer 空闲。

三问：

- 当前项目 topic 和 group 是什么？

项目回答：

- 主 topic 是 `spring3.order-preview.events.v1`。
- DLT 是 `spring3.order-preview.dlt.v1`。
- group 默认是 `spring3-order-preview`。
- topic、group、partition 数通过 `application-kafka.yml` 配置。

常见坑：

- 把 offset 说成全局递增。offset 只在 partition 内有意义。

## 4. Kafka 如何保证顺序

一问：

- Kafka 能保证消息顺序吗？

回答要点：

- 只能保证单 partition 内顺序。
- 不保证跨 partition 全局顺序。
- 同一业务 key 进入同一 partition，才能保证该 key 维度顺序。

二问：

- 扩 partition 对顺序有什么影响？
- consumer 并发会不会破坏顺序？

回答要点：

- 扩 partition 会改变 key 到 partition 的映射，新旧消息可能分散。
- 同 partition 内由一个 consumer 实例消费，但业务内部再异步并发可能打乱处理顺序。

三问：

- 当前项目如何保证同 key 顺序？

项目回答：

- producer 使用 `partitionKey` 作为 Kafka key。
- 当前 `partitionKey` 等于订单预览 ID。
- `OrderKafkaProfileIT.sameKeyKafkaEventsAreConsumedInOrder` 验证同 key 多条事件按顺序处理。

常见坑：

- 说 Kafka 保证所有消息有序。
- 忽略 key 选择和热点 key。

## 5. 如何选择 message key

一问：

- Kafka key 有什么作用？

回答要点：

- 参与分区路由。
- 支撑同 key 顺序。
- 影响分区负载均衡。

二问：

- key 选 `orderId`、`sku`、`userId` 有什么差异？

回答要点：

- `orderId` 保证订单维度有序。
- `sku` 保证库存/商品维度有序，但热点 SKU 可能倾斜。
- `userId` 保证用户维度有序，但大客户可能倾斜。

三问：

- 当前项目选的 key 是否完美？

项目回答：

- 当前订单预览事件选订单预览 ID 合理，因为学习目标是同一订单预览维度顺序。
- 如果以后做库存扣减事件，可能要改用 SKU 或库存聚合 ID，并处理热点问题。

常见坑：

- 为了全局顺序使用固定 key，导致吞吐退化。

## 6. Kafka 如何保证消息不丢

一问：

- Kafka 如何保证消息不丢？

回答要点：

- producer：`acks=all`、retry、callback、idempotence。
- broker：复制因子、ISR、`min.insync.replicas`、禁用 unclean leader election。
- consumer：处理成功后提交 offset。
- business：幂等、outbox/inbox、补偿和对账。

二问：

- `acks=all` 是否绝对不丢？
- broker 返回 ack 前后网络断开怎么办？

回答要点：

- `acks=all` 只是等待 ISR 确认，不是绝对。
- 发送结果未知时可能需要重试，重试又可能带来重复。
- 业务需要根据 eventId 和 outbox 兜底。

三问：

- 当前项目做到哪一层？

项目回答：

- producer 配了 `acks=all`、idempotence、retries、callback。
- consumer 处理成功后 manual ack。
- 幂等只有内存 demo。
- 没有 outbox，所以不能说业务事件发布绝对可靠。

常见坑：

- 只回答 `acks=all`。
- 不提业务幂等和补偿。

## 7. 为什么 Kafka 仍会重复消费

一问：

- Kafka 会重复消费吗？

回答要点：

- 会。at-least-once 语义下重复是正常情况。

二问：

- 重复来源有哪些？

回答要点：

- producer retry。
- consumer 处理成功但 commit 失败。
- rebalance 前 offset 未提交。
- 应用重启。
- DLT 或历史消息重放。
- 业务重复发送。

三问：

- 当前项目如何处理重复？

项目回答：

- 使用 `eventId` 做内存去重。
- 重复消息增加 duplicate counter 并 ack。
- 生产环境应改成数据库唯一键、inbox/outbox、Redis 或状态机版本。

常见坑：

- 说 Kafka 开启幂等 producer 后 consumer 就不会重复。

## 8. at-most-once、at-least-once、exactly-once 怎么理解

一问：

- 三种语义分别是什么？

回答要点：

- at-most-once：最多一次，可能丢。
- at-least-once：至少一次，可能重复。
- exactly-once：在特定边界内一次。

二问：

- Kafka exactly-once 的边界是什么？

回答要点：

- Kafka transaction 能覆盖 Kafka 内 read-process-write。
- 消费 offset 和输出 Kafka records 可以同事务提交。
- 不自动覆盖 DB、HTTP、短信等外部副作用。

三问：

- 当前项目是不是 exactly-once？

项目回答：

- 不是业务 exactly-once。
- 当前是 at-least-once + 内存幂等 demo。
- 生产订单场景要靠持久化幂等和 outbox/inbox。

常见坑：

- 把 Kafka EOS 夸大成业务所有副作用只执行一次。

## 9. manual ack 为什么放在处理成功后

一问：

- 为什么不自动提交 offset？

回答要点：

- 自动提交可能在业务处理成功前提交 offset，处理失败会丢消息。
- 手动提交可以把 offset 推进放在业务成功之后。

二问：

- 手动提交后还会重复吗？

回答要点：

- 会。处理成功但提交失败、进程崩溃、rebalance 都会重复。
- 所以手动 ack 和幂等要一起设计。

三问：

- 当前项目怎么配置？

项目回答：

- `enable-auto-commit=false`。
- `ack-mode=manual_immediate`。
- `KafkaOrderPreviewConsumer` 成功处理后调用 `acknowledgment.acknowledge()`。

常见坑：

- 以为 manual ack 就不重复。

## 10. consumer lag 上涨怎么排查

一问：

- lag 是什么？

回答要点：

- lag 是 consumer 已提交或当前位置与 log end offset 的差距，表示消费落后。

二问：

- lag 上涨从哪些方向排查？

回答要点：

- 生产速率突然上涨。
- 单条消费耗时变长。
- partition skew。
- 下游依赖慢或失败。
- rebalance 频繁。
- consumer 数不足或超过 partition 后无效。
- broker 磁盘、网络、CPU。
- poison message 阻塞。

三问：

- 当前项目如何看？

项目回答：

- 本地可用 Kafka UI 或 `kafka-consumer-groups --describe`。
- 应用侧有 processed/failed/duplicates counter。
- 后续应补 consumer lag PromQL 和 Grafana panel。

常见坑：

- 只说“加机器”。

## 11. rebalance 是什么，为什么危险

一问：

- rebalance 是什么？

回答要点：

- consumer group 成员或订阅变化时，重新分配 partition。

二问：

- rebalance 频繁的原因和影响？

回答要点：

- consumer 启停、心跳超时、poll 间隔太长、partition 变化。
- 影响是暂停消费、重复消费、lag 上涨。

三问：

- 怎么优化？

项目回答：

- 控制单条处理耗时。
- 合理配置 `max.poll.records`、`max.poll.interval.ms`。
- 使用 cooperative assignor 和 static membership。
- 优雅停机。
- 当前 demo lab 的 listener 会记录 assigned/revoked 到 `/api/kafka-demo/state`，但还没有多实例扩缩容脚本和 Grafana 面板。

常见坑：

- 只把 rebalance 说成“消费者重新分配”，不说重复和 lag 影响。

## 12. DLT 是什么，怎么处理

一问：

- DLT 是什么？

回答要点：

- Dead Letter Topic，用于保存处理失败、重试耗尽或 poison message。

二问：

- DLT 后是不是自动解决？

回答要点：

- 不是。DLT 是失败隔离和排查现场。
- 需要告警、定位、修复、限速重放、防循环、审计。

三问：

- 当前项目 DLT 怎么做？

项目回答：

- `DefaultErrorHandler` 重试后通过 `DeadLetterPublishingRecoverer` 写入 DLT。
- 测试验证 DLT header 包含原 topic、partition、offset 和异常信息。

常见坑：

- 把 DLT 当补偿机制。

## 13. blocking retry 和 retry topic 怎么选

一问：

- 两种 retry 有什么区别？

回答要点：

- blocking retry 在当前 listener 线程重试，会阻塞同 partition 后续消息。
- retry topic 把失败消息转移到重试 topic，避免主 topic 长时间阻塞。

二问：

- retry topic 有什么代价？

回答要点：

- topic/listener 增多。
- 排障复杂。
- 可能影响原始顺序。
- 和事务组合有限制，需要看 Spring Kafka 支持边界。

三问：

- 当前项目选哪个？

项目回答：

- 当前已落地 blocking retry，简单可控。
- demo lab 已落地手写 retry topic：失败进入 retry topic，超过 attempt 后进入 retry DLT。
- 后续可以补 `@RetryableTopic` 对比、真实延迟调度和防循环重放审计。

常见坑：

- 认为 retry topic 全面优于 blocking retry。

## 14. producer idempotence 原理和边界

一问：

- producer 幂等解决什么？

回答要点：

- 避免 producer retry 时同一批消息被 broker 重复写入。
- 依赖 producer id、sequence number、broker 端去重。

二问：

- 开启 producer 幂等后还要消费幂等吗？

回答要点：

- 需要。producer 幂等不覆盖业务重复发送、consumer 重复投递、DLT 重放和外部副作用。

三问：

- 当前项目怎么配置？

项目回答：

- `enable.idempotence=true`。
- `acks=all`。
- `max.in.flight.requests.per.connection=5`。
- 仍然做 eventId 消费幂等。

常见坑：

- 混淆 producer 幂等和业务幂等。

## 15. Kafka transaction 适合什么

一问：

- Kafka transaction 解决什么？

回答要点：

- Kafka 内部多 partition 写入原子性。
- read-process-write 场景下，输出 records 和消费 offset 同事务提交。

二问：

- Kafka transaction 不解决什么？

回答要点：

- 不解决数据库、HTTP、短信、邮件等外部系统副作用的一次性。

三问：

- 当前项目怎么演示？

项目回答：

- demo lab 已实现 producer transaction commit/abort 可见性：`read_committed` consumer 能看到 committed input，看不到 aborted input。
- 事务 consumer 会把 input 转成 audit topic 事件，但当前没有把 consumed offset 和 output topic 放入同一事务。
- 不把它描述成订单业务 exactly-once。

常见坑：

- 说用了 Kafka transaction 就不用 outbox/inbox。

## 16. outbox 和 inbox 分别解决什么

一问：

- outbox 是什么？

回答要点：

- 业务表和待发布事件表在同一个本地事务写入，再由 relay 发布到 Kafka。

二问：

- inbox 是什么？

回答要点：

- 消费端记录已处理 eventId，把消息处理和业务写入放在本地事务里，防止重复副作用。

三问：

- 当前项目为什么没做？

项目回答：

- 当前项目明确不接数据库。
- 只做设计型覆盖。
- 如果后续允许 DB profile，优先实现 outbox/inbox。

常见坑：

- 用本地内存 set 替代生产幂等。

## 17. Schema 演进怎么做

一问：

- Kafka 事件字段变更怎么保证兼容？

回答要点：

- `eventVersion`。
- 新增可选字段。
- 不直接删除/重命名字段。
- 老消费者能忽略新字段。
- 必要时使用 Schema Registry。

二问：

- 生产者和消费者谁先升级？

回答要点：

- 通常先让消费者兼容新旧 schema，再升级生产者。
- 删除字段要经过长周期迁移。

三问：

- 当前项目如何处理？

项目回答：

- 当前有 `eventVersion=1`。
- JSON 序列化，没有 Schema Registry。
- demo lab 已有 `schema-v2` 端点和 V1 consumer 兼容测试，旧消费者会忽略新增可选字段。
- 生产级兼容治理仍建议上 Schema Registry。

常见坑：

- 把 Kafka event 当 Java DTO 随意改字段。

## 18. 反序列化失败怎么办

一问：

- consumer 反序列化失败会发生什么？

回答要点：

- listener 可能拿不到业务对象。
- 如果没有错误处理，消费可能卡住或不断失败。

二问：

- Spring Kafka 如何处理？

回答要点：

- 可以使用 `ErrorHandlingDeserializer` 包装真实 deserializer。
- 配合 error handler/DLT 保存失败消息和异常信息。

三问：

- 当前项目怎么做？

项目回答：

- value deserializer 使用 `ErrorHandlingDeserializer`。
- delegate 是 `JsonDeserializer`。
- DLT 处理保留异常 header。

常见坑：

- 只处理业务异常，不处理 deserialization poison message。

## 19. 分区数怎么规划

一问：

- partition 数如何确定？

回答要点：

- 看生产吞吐、消费并发、有序 key 分布、未来扩容、broker 资源。

二问：

- partition 越多越好吗？

回答要点：

- 不是。过多会增加文件句柄、内存、metadata、controller 压力、恢复时间。

三问：

- 当前项目为什么是 3？

项目回答：

- 本地学习环境使用 3 个 partition，便于演示分区和 listener concurrency。
- 生产需要按容量评估，不照搬。

常见坑：

- 把 partition 数设成 consumer 数就结束，不考虑 key 和未来扩容。

## 20. min.insync.replicas 和 acks=all 的关系

一问：

- `acks=all` 依赖什么？

回答要点：

- leader 等待 ISR 中满足条件的副本确认。
- `min.insync.replicas` 控制写入至少需要多少同步副本。

二问：

- `min.insync.replicas` 配太大或太小会怎样？

回答要点：

- 太大：副本故障时写入不可用。
- 太小：可靠性下降。

三问：

- 当前项目为什么没配？

项目回答：

- 本地单 broker Kafka 复制因子是 1，不适合演示生产 ISR。
- 文档中要说明生产应多 broker、多副本。

常见坑：

- 认为 `acks=all` 等于所有副本都成功。

## 21. leader election 和 unclean election

一问：

- partition leader 挂了怎么办？

回答要点：

- controller 从 ISR 里选新 leader。
- producer/consumer 刷新 metadata 后继续。

二问：

- unclean leader election 有什么风险？

回答要点：

- 可能选非 ISR 落后副本，导致已确认数据丢失。

三问：

- 面试怎么结合项目说？

项目回答：

- 本地单 broker 不演示 leader election。
- 生产部署必须讨论复制因子、ISR、磁盘和告警。

常见坑：

- 完全不提 broker 层副本和 ISR。

## 22. 消费慢但 CPU 很低怎么办

一问：

- CPU 低是否说明消费没问题？

回答要点：

- 不一定。可能卡在 IO、下游、锁、rebalance、poll 参数、单分区热点。

二问：

- 怎么排查？

回答要点：

- 看 lag 分区分布。
- 看单条处理耗时。
- 看下游 HTTP/DB 指标。
- 看 consumer records rate 和 fetch rate。
- 看 rebalance 指标。

三问：

- 当前项目可以怎么模拟？

项目回答：

- 通过 catalog 慢调用、poison SKU、listener concurrency 配置和 Kafka UI 观察。
- 后续可增加延迟消费测试。

常见坑：

- 只扩容 consumer，不看 partition skew。

## 23. DLT 重放如何防止事故

一问：

- DLT 怎么重放？

回答要点：

- 修复问题后，从 DLT 读取，按规则写回原 topic 或专用重放 topic。

二问：

- 重放前要检查什么？

回答要点：

- 失败原因已修复。
- 幂等能力可用。
- 重放范围准确。
- 限速。
- 顺序影响可接受。
- dry run 和审计。

三问：

- 当前项目是否有重放工具？

项目回答：

- 当前没有，只提供 runbook。
- 后续可做手动确认重放工具。

常见坑：

- 直接把 DLT 全量打回主 topic。

## 24. Kafka 安全怎么做

一问：

- 生产 Kafka 如何做认证授权？

回答要点：

- SASL_SSL 或 mTLS。
- ACL 限制 topic 和 group。
- 凭证放密钥系统。

二问：

- 为什么 consumer group 也要授权？

回答要点：

- 防止服务冒用其他 group 读取数据或影响 offset。

三问：

- 当前项目怎么处理？

项目回答：

- 本地 Kafka 无认证。
- 只提供配置模板，不提交真实账号、证书、生产 bootstrap。

常见坑：

- 所有服务共用一个超级账号。

## 25. Kafka 监控告警看哪些

一问：

- Kafka 应用看哪些指标？

回答要点：

- producer success/error/latency。
- consumer processed/failed/lag。
- DLT count。
- rebalance count。
- broker under replicated partitions、disk、network、request latency。

二问：

- 哪些告警优先级最高？

回答要点：

- DLT 激增。
- lag 持续上涨。
- under replicated partitions。
- offline partitions。
- producer error rate。
- broker 磁盘接近满。

三问：

- 当前项目有啥？

项目回答：

- 有 `orders.preview.kafka.*` 业务指标。
- Kafka client/broker lag 面板还未落地，后续 P1 补。

常见坑：

- 只看应用日志，不看 lag 和 broker 指标。

## 26. Kafka 和数据库事务如何保证一致

一问：

- 下单成功但发 Kafka 失败怎么办？

回答要点：

- 不能靠本地内存重试完全解决。
- 用 outbox，把业务数据和事件记录写在同一个数据库事务，再异步发布。

二问：

- 发 Kafka 成功但 DB 回滚怎么办？

回答要点：

- 如果先发 Kafka 再写 DB，就会产生脏事件。
- 应避免这种顺序，或用事务消息/outbox 模式。

三问：

- 当前项目如何回答？

项目回答：

- 当前订单预览无 DB，不处理强一致。
- 文档明确 outbox 是设计型覆盖。

常见坑：

- 说 Kafka transaction 可以解决 DB 和 Kafka 一致性。

## 27. 如何处理 poison message

一问：

- 什么是 poison message？

回答要点：

- 永远无法被当前消费者正常处理的消息，例如 schema 不兼容、字段非法、脏数据。

二问：

- 如何避免它阻塞消费？

回答要点：

- 反序列化错误处理。
- 不可重试异常分类。
- DLT。
- 告警和重放治理。

三问：

- 当前项目怎么模拟？

项目回答：

- `SKU-KAFKA-FAIL` 模拟消费失败。
- `IllegalArgumentException` 标记不可重试。
- DLT 测试验证异常 header。

常见坑：

- 无限重试 poison message，导致 partition 卡死。

## 28. 如何设计 topic 命名

一问：

- topic 名怎么设计？

回答要点：

- 包含域、聚合、事件类别和版本。
- 避免环境混在 topic 名里时失控，环境更适合集群或 namespace 隔离。

二问：

- version 放 topic 还是 event 字段？

回答要点：

- 小兼容变更用 eventVersion。
- 大破坏性变更可以新 topic。

三问：

- 当前项目 topic 是否合理？

项目回答：

- `spring3.order-preview.events.v1` 表达了系统、聚合和版本。
- DLT topic 后缀 `.dlt` 清晰。

常见坑：

- topic 名随接口名或 Java 类名变化。

## 29. Kafka 消息积压时是否直接扩 partition

一问：

- lag 高能不能扩 partition？

回答要点：

- 不一定。先看瓶颈。

二问：

- 扩 partition 的风险？

回答要点：

- key 映射变化。
- 严格顺序风险。
- 不能解决下游慢。
- broker metadata 和文件成本上升。

三问：

- 当前项目怎么规划？

项目回答：

- 本地默认 3 分区只是学习值。
- 生产要按吞吐和 key 分布规划。

常见坑：

- 把扩 partition 当万能解。

## 30. 面试项目总讲法

一问：

- 请介绍你在项目里怎么用 Kafka。

回答模板：

> 我在 Spring Boot 3 学习项目里把 Kafka 作为可选 profile，不影响默认启动。业务上围绕订单预览事件建模，`OrderService` 发布应用事件，Kafka publisher 转成 `OrderPreviewKafkaEvent` 发到 `spring3.order-preview.events.v1`。事件包含 eventId、eventVersion、aggregateId、partitionKey、requestId、traceId 和 payload。消费端用 `@KafkaListener`、manual ack、eventId 幂等、失败重试和 DLT。另有 `/api/kafka-demo` 演示基础模型、重复消费、顺序、retry topic、Schema V2、事务 commit/abort、lag/rebalance、安全模板、容量规划和 MQ 选型。Testcontainers 覆盖主链路和 demo lab。

二问：

- 这个方案有什么不足？

回答模板：

> 当前幂等是内存实现，不能抗重启；没有 DB，所以 outbox/inbox 只是设计型覆盖；retry topic、Schema V2、事务 commit/abort、lag/rebalance 已经是 demo 级演示，不等于生产级能力；Schema Registry、真实 SASL_SSL/ACL、lag Grafana 面板、DLT 重放工具和完整 offset+output EOS 还没有落地。面试或生产设计里我会明确这些边界。

三问：

- 如果要上生产，你会补什么？

回答模板：

> 第一补 outbox/inbox 和持久化幂等；第二补 consumer lag、DLT、producer error、broker ISR 告警和 Grafana 面板；第三补 DLT dry run、限速、审计和防循环重放工具；第四补 Schema Registry；第五补真实 SASL_SSL/ACL；第六补完整 Kafka 内 offset+output EOS；最后做压测和分区容量规划。

## 高频反问清单

面试结束前可以主动补充：

- 当前项目默认 profile 不依赖 Kafka，说明我注意工程边界。
- 当前示例不是业务 exactly-once，说明我理解 Kafka EOS 边界。
- 当前内存幂等只是 demo，生产需要持久化。
- DLT 不是补偿，重放必须治理。
- lag 排查不能只加机器，要看生产速率、消费耗时、分区倾斜、rebalance 和 broker。
- Kafka 与 RabbitMQ/RocketMQ 是选型问题，不是高低问题。

## 最小背诵版

如果只能记一段：

> Kafka 是可分区、可持久化、可回放的事件日志。生产可靠性要拆 producer、broker、consumer 和 business 四层；顺序只在 partition 内成立，key 决定业务有序维度；处理成功后提交 offset 是 at-least-once，所以消费端必须幂等；producer 幂等不等于业务幂等；DLT 是失败隔离，不是自动补偿；Kafka transaction 主要覆盖 Kafka 内 read-process-write，不覆盖数据库和 HTTP 副作用。当前项目已落地 producer/consumer/manual ack/eventId 幂等/DLT/Testcontainers，并用 demo lab 演示 retry topic、Schema V2、事务可见性、lag/rebalance、安全模板、容量规划和 MQ 选型；outbox、持久化幂等、Schema Registry、真实安全认证、Grafana 面板和完整 offset+output EOS 是后续生产化补充。
