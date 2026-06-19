# Kafka 运维排障 Runbook

## 目标

这份 runbook 用于回答和演练 Kafka 生产排障问题，重点覆盖：

- consumer lag 上涨。
- rebalance 频繁。
- producer 发送失败。
- DLT 激增。
- poison message。
- 顺序错乱。
- broker 故障。
- 安全重放。

当前项目说明：

- 本地 Kafka 使用 `platform/kafka/docker-compose.yml`，服务名是 `kafka`，容器名是 `spring3-kafka`。
- Kafka UI 端口是 `http://localhost:8089`。
- 当前 Prometheus 只抓取 Spring Boot Actuator 指标，没有抓 broker JMX exporter。
- 当前项目有业务指标 `orders.preview.kafka.*`，生产级 broker/consumer lag 面板是后续补充项。

## 快速入口

启动本地 Kafka：

```bash
docker compose -f platform/kafka/docker-compose.yml up -d
```

查看容器：

```bash
docker compose -f platform/kafka/docker-compose.yml ps
```

进入 Kafka CLI：

```bash
docker exec -it spring3-kafka bash
```

容器内 Kafka 命令目录：

```bash
/opt/kafka/bin
```

Kafka bootstrap：

```bash
localhost:9092
```

Kafka UI：

```text
http://localhost:8089
```

## 基础巡检

### 1. 查看 topic

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### 2. 查看 topic 详情

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic spring3.order-preview.events.v1
```

重点看：

- partition 数。
- leader。
- replicas。
- ISR。

本地单 broker 只有一个 replica，不能用来证明生产高可用。

### 3. 查看 consumer group

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --list
```

### 4. 查看 lag

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group spring3-order-preview
```

字段解释：

| 字段 | 含义 |
| --- | --- |
| `CURRENT-OFFSET` | 当前 group 已提交 offset |
| `LOG-END-OFFSET` | partition 当前日志末尾 |
| `LAG` | 消费落后条数 |
| `CONSUMER-ID` | 当前持有 partition 的 consumer |
| `HOST` | consumer 所在主机 |
| `CLIENT-ID` | client id |

### 5. 读取主 topic

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic spring3.order-preview.events.v1 \
  --from-beginning \
  --timeout-ms 10000
```

### 6. 读取 DLT

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic spring3.order-preview.dlt.v1 \
  --from-beginning \
  --timeout-ms 10000
```

## Runbook 1：consumer lag 上涨

### 现象

- `LAG` 持续上涨。
- 业务延迟变大。
- 下游系统收到事件变慢。
- DLT 可能没有明显增加。

### 排查顺序

1. 确认是否所有 partition lag 都涨。
2. 确认 producer 速率是否突然上涨。
3. 确认 consumer 是否在线和稳定。
4. 确认单条消息处理耗时是否变长。
5. 确认是否某个 partition skew。
6. 确认是否 rebalance 频繁。
7. 确认下游 HTTP/DB/Redis 是否慢。
8. 确认 broker 磁盘、网络、CPU。

### 命令

查看 group lag：

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group spring3-order-preview
```

查看 topic partition 分布：

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic spring3.order-preview.events.v1
```

查看应用业务指标：

```bash
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.processed.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.failed.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.duplicates.total
```

### 判断

| 现象 | 可能原因 | 处理 |
| --- | --- | --- |
| 所有 partition lag 同时上涨 | 生产速率增加或消费者整体慢 | 扩 consumer、优化处理、限流 |
| 单个 partition lag 很高 | 热点 key 或 poison message | 查 key 分布、DLT、拆热点 |
| lag 周期性上涨又下降 | 流量波峰或批处理 | 评估容量和告警阈值 |
| consumer 不显示 | 应用未启动、group id 错、认证失败 | 查应用日志和配置 |
| lag 上涨且 rebalance 多 | poll 超时、心跳异常、频繁发布 | 调整消费耗时和 rebalance 配置 |

### 临时处理

- 如果下游慢，先限流 producer 或暂停非关键消费者。
- 如果 consumer 数少且 partition 足够，增加 consumer 实例。
- 如果是热点 key，扩 consumer 不一定有效，需要拆 key 或分流。
- 如果是 poison message，进入 DLT 后再恢复主消费。

### 面试回答

> lag 上涨我会先看是不是所有 partition 都涨，再看生产速率、消费耗时、partition skew、下游依赖、rebalance 和 broker 资源。不能上来就加 consumer，因为 consumer 数超过 partition 数无效，热点 partition 也不是简单扩容能解决。

## Runbook 2：rebalance 频繁

### 现象

- consumer 日志频繁出现 partition revoked/assigned。
- lag 抖动。
- 消费吞吐下降。
- 重复消费增加。

### 常见原因

- consumer 实例频繁发布或重启。
- 单条处理太慢，超过 `max.poll.interval.ms`。
- 心跳超时。
- broker 或网络抖动。
- topic partition 数调整。
- 消费者订阅 topic 变化。

### 检查项

应用日志：

```bash
rg -n "revoked|assigned|rebalance|CommitFailed|poll|Kafka" logs/
```

如果没有集中日志，先看 order-service 控制台输出。

消费者 group：

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group spring3-order-preview
```

### 参数方向

| 参数 | 作用 | 调整方向 |
| --- | --- | --- |
| `max.poll.interval.ms` | 两次 poll 之间最大间隔 | 单条处理慢时增大，或降低每批数量 |
| `max.poll.records` | 每次 poll 最大记录数 | 降低可减少单批处理时间 |
| `session.timeout.ms` | session 超时 | 网络抖动时评估调整 |
| `heartbeat.interval.ms` | 心跳间隔 | 通常小于 session timeout |
| partition assignor | 分配策略 | 考虑 cooperative sticky |
| static membership | 静态成员 | 降低短暂重启触发的抖动 |

### 处理

- 优化单条消息处理时间。
- 把耗时外部调用加超时、熔断和隔离。
- 控制批量大小。
- 优雅停机。
- 减少滚动发布并发。
- 对长期任务改成异步任务状态机，不在 listener 线程里阻塞太久。

### 面试回答

> rebalance 不是只影响分配，它会造成暂停消费和重复消费。频繁 rebalance 通常和实例抖动、poll 超时、心跳异常有关。优化要从消费耗时、poll 参数、分配策略、static membership 和发布策略入手。

## Runbook 3：producer 发送失败

### 现象

- `orders.preview.kafka.send.failed.total` 增加。
- 应用日志出现 publish failed。
- HTTP 接口可能仍然返回成功，因为当前 Kafka 发送不是同步主链路。

### 排查顺序

1. Kafka 是否启动。
2. bootstrap servers 是否正确。
3. topic 是否存在。
4. broker leader 是否可用。
5. producer 是否 metadata 获取超时。
6. ISR 是否不足。
7. 序列化是否失败。
8. producer buffer 是否满。

### 命令

查看 Kafka 容器健康：

```bash
docker compose -f platform/kafka/docker-compose.yml ps
```

查看 topic：

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic spring3.order-preview.events.v1
```

查看失败指标：

```bash
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.send.failed.total
```

### 处理

- broker 不可用：恢复 broker。
- topic 不存在：确认 `NewTopic` 自动创建是否生效，或手动创建。
- 序列化失败：检查 event 字段和 JsonSerializer。
- 发送超时：检查 broker 负载、网络、`delivery.timeout.ms`、`request.timeout.ms`。
- ISR 不足：生产多 broker 环境检查副本同步。

### 面试回答

> 当前项目 Kafka 发送失败不会回滚订单预览响应，因为它是学习型异步事件。生产如果要求业务成功必须发布事件，不能只靠 callback 重试，要用 outbox 和补偿任务。

## Runbook 4：DLT 激增

### 现象

- DLT topic 消息快速增加。
- `orders.preview.kafka.failed.total` 增加。
- 告警触发。

### 先不要做

- 不要立刻全量重放 DLT。
- 不要清空 DLT。
- 不要提高 retry 次数掩盖问题。
- 不要忽略原始异常 header。

### 排查顺序

1. 看最近发布和配置变更。
2. 抽样查看 DLT 消息 payload。
3. 查看 DLT header：原 topic、partition、offset、exception。
4. 判断失败类型：瞬时异常、业务不可重试、poison message、schema 不兼容。
5. 评估影响范围。
6. 修复代码、配置或脏数据。
7. 再做限速重放。

### 读取 DLT

```bash
docker exec spring3-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic spring3.order-preview.dlt.v1 \
  --from-beginning \
  --property print.headers=true \
  --timeout-ms 10000
```

### 当前项目触发方式

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-KAFKA-FAIL","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

### 处理策略

| 失败类型 | 处理 |
| --- | --- |
| schema 不兼容 | 修复消费者兼容逻辑，验证后重放 |
| 必填字段缺失 | 修数据或确认丢弃，保留审计 |
| 下游暂时失败 | 恢复下游后限速重放 |
| 业务非法状态 | 检查是否应该跳过或补偿 |
| 代码 bug | 修复发布后抽样重放 |

### 面试回答

> DLT 激增首先要保留现场、分类失败、确认影响范围。DLT 不是自动补偿，修复后才能限速重放，并且重放前必须确认幂等、防循环和审计。

## Runbook 5：poison message 阻塞消费

### 现象

- 某个 partition lag 卡住。
- 同一条消息反复失败。
- 后续同 partition 消息无法推进。

### 原因

- 反序列化失败。
- schema 不兼容。
- 必填字段缺失。
- 消费者代码对某种值必然抛异常。

### 当前项目防护

- 使用 `ErrorHandlingDeserializer`。
- `DefaultErrorHandler` 失败后 DLT。
- `IllegalArgumentException` 不重试。
- poison SKU 可以模拟业务失败。

### 处理

- 将 poison message 送 DLT。
- 修复 consumer 或数据。
- 不要无限 blocking retry。
- 如果必须跳过，保留审计和补偿。

### 面试回答

> poison message 的核心风险是卡住 partition。要用错误分类、反序列化错误处理和 DLT 隔离，不能让同一条坏消息无限重试拖垮整条分区。

## Runbook 6：顺序错乱

### 现象

- 同一订单状态先后顺序不符合预期。
- 消费端看到状态回退。
- 重放后出现旧事件覆盖新状态。

### 排查顺序

1. 事件是否使用同一个 key。
2. key 是否在生产者多个版本中变化。
3. topic 是否扩过 partition。
4. 业务处理是否异步并发。
5. 是否 DLT 重放旧消息。
6. 消费端是否缺状态版本校验。

### 处理

- 明确有序维度。
- 保证同一维度用同一 key。
- 单 key 严格顺序时避免业务异步乱序。
- 使用 aggregate version 或状态机防止旧事件覆盖新状态。
- DLT 重放按 key 和时间窗口控制。

### 面试回答

> Kafka 只保证 partition 内顺序。如果 key 变了、扩 partition、业务异步并发或重放旧消息，都可能破坏业务看到的顺序。生产里还要用状态版本防止旧事件覆盖新状态。

## Runbook 7：broker 故障

### 本地单 broker

当前本地 `spring3-kafka` 是单 broker：

- broker 停止后不可写不可读。
- 没有副本切换能力。
- 不代表生产高可用。

检查：

```bash
docker compose -f platform/kafka/docker-compose.yml ps
docker logs spring3-kafka --tail=200
```

恢复：

```bash
docker compose -f platform/kafka/docker-compose.yml restart kafka
```

### 生产多 broker

生产要看：

- under replicated partitions。
- offline partitions。
- active controller。
- leader election。
- ISR shrink/expand。
- broker disk usage。
- network request latency。

关键问题：

| 问题 | 风险 |
| --- | --- |
| broker 宕机 | ISR 缩小，leader 迁移 |
| ISR 不足 | `acks=all` 写入失败 |
| 磁盘满 | broker 不可写或异常 |
| controller 异常 | metadata 和 leader election 受影响 |
| unclean election | 可能丢已确认数据 |

### 面试回答

> 本地单 broker 只能学习 API 和语义，不能证明生产可靠性。生产要用多 broker、多副本、ISR、`min.insync.replicas`、磁盘和 under replicated partitions 告警来支撑。

## Runbook 8：安全重放

### 重放前检查

| 检查项 | 必须回答 |
| --- | --- |
| 为什么进 DLT | 失败原因是否确认 |
| 是否已修复 | 代码、配置、数据是否修复 |
| 重放范围 | topic、partition、offset、时间窗口、eventId |
| 幂等 | 重复执行是否安全 |
| 顺序 | 是否会打乱同 key 顺序 |
| 限速 | 是否会打爆下游 |
| 审计 | 谁重放、何时、多少、结果如何 |
| 回滚 | 重放失败如何停止和恢复 |

### 重放策略

| 策略 | 说明 |
| --- | --- |
| 写回原 topic | 简单，但要防循环 |
| 写到 replay topic | 可控，消费者单独订阅 |
| 手动处理 | 少量关键数据 |
| 丢弃并审计 | 明确无业务价值或非法数据 |

### 防循环

- 增加 replay attempt header。
- 达到次数上限后停止。
- 区分原始 DLT 和 replay DLT。
- 重放工具默认 dry run。
- 重放按 eventId 白名单或 offset 范围执行。

### 面试回答

> DLT 重放必须是一个受控流程，不是命令行全量倒回。要先修复原因，再按范围、限速、幂等、顺序和审计执行，必要时用 replay topic 隔离。

## PromQL 建议

当前项目业务指标经 Prometheus 抓取后会转换成下划线格式。

### 业务失败率

```promql
sum(rate(orders_preview_kafka_failed_total[5m]))
/
clamp_min(sum(rate(orders_preview_kafka_processed_total[5m])), 0.001)
```

### Kafka 发送失败

```promql
sum(rate(orders_preview_kafka_send_failed_total[5m])) > 0
```

### DLT 业务失败增长

当前项目没有单独 DLT counter，可先用 failed counter 近似：

```promql
sum(increase(orders_preview_kafka_failed_total[10m])) > 0
```

后续建议增加：

- DLT published counter。
- consumer lag gauge。
- rebalance counter。

### 生产环境建议指标

这些指标需要 Kafka client metrics、JMX exporter 或托管 Kafka 指标接入后使用：

```promql
kafka_consumer_records_lag_max
kafka_consumer_rebalance_total
kafka_producer_record_error_rate
kafka_server_replicamanager_underreplicatedpartitions
kafka_controller_kafkacontroller_offlinepartitionscount
kafka_network_requestmetrics_totaltimems
```

不同 exporter 指标名会不同，落地时以实际 scrape 结果为准。

## 告警建议

| 告警 | 条件 | 级别 |
| --- | --- | --- |
| DLT 增长 | 10 分钟内 DLT 或 failed counter 增加 | warning |
| consumer lag 持续上涨 | lag 连续 10 分钟上涨 | warning |
| consumer 停止 | group 无 active member | critical |
| producer 发送失败 | send failed rate > 0 持续 5 分钟 | warning |
| under replicated partitions | > 0 持续 1 分钟 | critical |
| offline partitions | > 0 | critical |
| broker 磁盘高 | > 85% | warning |
| rebalance 频繁 | rebalance rate 异常增加 | warning |

## 故障复盘模板

```text
故障标题：
发生时间：
恢复时间：
影响范围：
发现方式：

现象：
- lag：
- DLT：
- producer error：
- broker 指标：
- 下游指标：

根因：

时间线：
- T0：
- T1：
- T2：

处理动作：

为什么现有防护没有挡住：

后续改进：
- 代码：
- 配置：
- 监控：
- runbook：
- 演练：
```

## 面试总回答

> Kafka 排障我会先明确现象是生产失败、消费 lag、rebalance、DLT 还是 broker 故障。lag 按生产速率、消费耗时、partition skew、下游慢、rebalance、broker 资源拆；DLT 按失败分类、修复、限速重放处理；broker 层看 ISR、under replicated partitions、offline partitions 和磁盘网络。当前项目已有业务指标和 DLT 测试，生产级 broker/JMX/lag 面板是后续补充项。
