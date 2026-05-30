# 数据一致性与事务边界专题

## 定位

当前项目明确不接入数据库和 Redis，但资深 Java 面试很容易从 Kafka/RabbitMQ 幂等、接口重复提交、`@Transactional`、outbox/inbox 和分布式事务继续追问。

本专题用于补齐设计能力和面试表达，不改变默认运行路径，不新增数据库依赖。

## 当前项目结合点

| 现有能力 | 相关文件 | 一致性边界 |
| --- | --- | --- |
| 订单预览同步接口 | `order-service/src/main/java/com/taoking/spring3/order/web/OrderController.java` | 当前只做计算和下游查询，没有持久化事务 |
| 订单预览领域事件 | `OrderPreviewCreatedEvent` | 事件在进程内发布，默认不保证跨进程持久化 |
| RabbitMQ 示例 | `order-service/src/rabbitmq/java/...` | 使用内存 eventId 幂等，只适合学习和测试 |
| Kafka 示例 | `order-service/src/kafka/java/...` | manual ack + 内存去重，不能替代生产级 inbox/outbox |
| Resilience4j fallback | `CatalogGovernanceService` | 降级只解决可用性，不解决写入一致性 |
| Caffeine 缓存 | `CatalogLookupService` | 本地缓存只影响读路径，不负责分布式一致性 |

当前项目的消息幂等是演示级：服务重启后内存去重状态会丢失，多实例部署时每个实例也只知道自己的内存状态。生产系统必须使用数据库唯一键、inbox 表、outbox 表、Redis、compact topic 或其他持久化机制完成去重和补偿。

## 本地事务基础

### `@Transactional` 常见失效原因

| 场景 | 原因 | 处理方式 |
| --- | --- | --- |
| 类内部 self-invocation | 调用没有经过 Spring 代理 | 拆到另一个 Bean，或通过代理调用 |
| 方法不是 public | Spring 默认代理 public 方法 | 把事务边界放到 public service 方法 |
| 异常被吞掉 | 事务拦截器看不到异常 | 不吞异常，或显式设置 rollback-only |
| 默认只回滚 unchecked exception | checked exception 默认不回滚 | `rollbackFor = Exception.class` |
| `@Async` 新线程 | 事务上下文不跨线程传播 | 异步侧重新开启事务，或先提交再投递任务 |
| `final` 类/方法 | 代理无法覆盖 | 避免 final，或调整代理方式 |

### 事务传播行为

| 传播行为 | 面试表达 | 常见用途 |
| --- | --- | --- |
| `REQUIRED` | 有事务就加入，没有就新建 | 默认业务写操作 |
| `REQUIRES_NEW` | 挂起外部事务，开启独立事务 | 审计日志、失败记录、outbox 独立写入要谨慎 |
| `NESTED` | 保存点回滚，依赖数据库支持 | 局部回滚 |
| `SUPPORTS` | 有事务就加入，没有就非事务 | 读操作或工具方法 |
| `MANDATORY` | 必须存在事务 | 强约束内部方法 |
| `NOT_SUPPORTED` | 挂起事务非事务执行 | 长耗时外部调用不占事务 |
| `NEVER` | 有事务就报错 | 明确禁止事务 |

面试重点：事务边界应该尽量短，不能把慢 HTTP 调用、消息等待、长时间计算放进事务里。

### 隔离级别

| 隔离级别 | 能解决 | 仍可能存在 | 面试重点 |
| --- | --- | --- | --- |
| Read Uncommitted | 基本不用 | 脏读、不可重复读、幻读 | 很少用于业务 |
| Read Committed | 脏读 | 不可重复读、幻读 | Oracle/PostgreSQL 常见默认 |
| Repeatable Read | 脏读、不可重复读 | 幻读视数据库实现而定 | MySQL InnoDB 默认，MVCC + next-key lock |
| Serializable | 最强隔离 | 性能和锁冲突 | 只用于极高一致性小范围场景 |

资深回答要点：隔离级别不是越高越好，要结合锁冲突、吞吐和业务一致性要求选择。

## 幂等设计

### 幂等键来源

| 来源 | 适用场景 | 风险 |
| --- | --- | --- |
| 客户端 `Idempotency-Key` | 创建订单、支付、发券 | 客户端必须保证重试复用同一个 key |
| 业务唯一键 | 用户 + 活动 + 优惠券 | 唯一键设计不完整会误杀合法请求 |
| 事件 `eventId` | MQ 消费去重 | eventId 生成必须稳定且全局唯一 |
| 状态机版本号 | 订单状态流转 | 需要清晰的状态迁移规则 |
| 请求摘要 hash | 幂等查询或弱幂等写 | 字段变更会影响 hash，一般只作辅助 |

### 幂等表草图

当前项目不落库，下面是生产设计草图：

```sql
create table idempotency_record (
    id bigint primary key,
    idempotency_key varchar(128) not null,
    business_type varchar(64) not null,
    business_id varchar(128),
    request_hash varchar(128),
    status varchar(32) not null,
    response_snapshot text,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique (business_type, idempotency_key)
);
```

处理策略：

1. 收到请求后先插入 `PROCESSING`。
2. 唯一键冲突时读取已有记录。
3. 已成功则返回 `response_snapshot`。
4. 处理中则返回 409、排队等待或短轮询。
5. 失败可按业务允许重试，重试前必须判断是否产生了外部副作用。

## Outbox / Inbox

### Outbox 表草图

```sql
create table outbox_event (
    id bigint primary key,
    event_id varchar(128) not null,
    aggregate_type varchar(64) not null,
    aggregate_id varchar(128) not null,
    event_type varchar(128) not null,
    event_version int not null,
    payload text not null,
    headers text,
    status varchar(32) not null,
    retry_count int not null default 0,
    next_retry_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique (event_id)
);
```

核心思想：业务数据和 outbox event 在同一个本地事务中提交。后台 relay 任务扫描 outbox，把事件投递到 Kafka/RabbitMQ，投递成功后标记 `SENT`。

### Inbox 表草图

```sql
create table inbox_event (
    id bigint primary key,
    event_id varchar(128) not null,
    consumer_group varchar(128) not null,
    source varchar(128) not null,
    status varchar(32) not null,
    error_message text,
    consumed_at timestamp,
    created_at timestamp not null,
    unique (event_id, consumer_group)
);
```

核心思想：消费端先插入 inbox 记录，唯一键保证同一个 consumer group 对同一 event 只处理一次。业务副作用和 inbox 状态更新应在同一个本地事务内完成。

## 端到端时序

### 推荐生产时序

```text
Client
  |
  | POST /orders with Idempotency-Key
  v
Order Service
  |
  | begin tx
  | insert/update business data
  | insert idempotency_record
  | insert outbox_event
  | commit tx
  v
Outbox Relay
  |
  | publish event with eventId
  | mark outbox_event SENT
  v
Kafka / RabbitMQ
  |
  v
Consumer
  |
  | begin tx
  | insert inbox_event unique(eventId, consumerGroup)
  | execute side effect
  | mark inbox_event CONSUMED
  | commit tx
  | ack / commit offset
```

### 当前项目时序

```text
POST /api/orders/preview
  |
  | call catalog-service by Feign/RestClient
  | build OrderPreviewResponse
  | publish in-process OrderPreviewCreatedEvent
  v
RabbitMQ/Kafka optional profile
  |
  | publish message/event
  | consume message/event
  | in-memory eventId dedupe
```

当前时序适合学习消息链路，不适合宣称生产级 exactly-once。

## 故障矩阵

| 场景 | 当前项目行为 | 生产风险 | 推荐生产处理 |
| --- | --- | --- | --- |
| HTTP 成功，进程内事件监听失败 | HTTP 已返回，事件可能丢失 | 用户看到成功但异步副作用缺失 | outbox 与业务数据同事务提交 |
| 业务事务提交成功，消息发送失败 | 当前无事务写入 | 消息丢失 | outbox relay 重试 |
| 消息发送成功，生产者没收到 ack | Kafka/RabbitMQ 可能重试 | broker 可能已有消息，重发导致重复 | producer 幂等 + consumer 幂等 |
| 消费者处理成功，ack/offset 失败 | 当前消费者可能再次收到消息 | 重复消费 | inbox/eventId 唯一约束 |
| 消费者处理失败 | 当前进入 DLQ/DLT | 后续消息可能阻塞或失败堆积 | retry 分类、DLQ/DLT、告警和重放 |
| DLQ/DLT 修复后重放 | 当前内存幂等可能已丢 | 重放导致重复副作用 | 重放前检查 inbox/业务状态 |
| 服务重启 | 内存去重清空 | 重复消息无法识别 | 持久化幂等记录 |
| 多实例消费 | 每个实例独立内存 | A 实例处理过，B 实例不知道 | 共享 inbox 或业务唯一键 |
| 下游 HTTP 调用成功但响应超时 | 调用方以为失败可能重试 | 外部副作用重复 | 外部接口幂等键 + 状态查询 |
| 补偿任务重复执行 | 可能重复补偿 | 二次退款、二次发券 | 补偿动作也必须幂等 |

## Kafka / RabbitMQ 的 exactly-once 边界

### Kafka

Kafka producer idempotence 主要避免 producer 重试导致同一 producer session 内 broker 端重复写入。Kafka transaction 能覆盖 Kafka 内部 read-process-write 流程，例如消费 topic A 后写 topic B 并提交 offset。

它不能自动覆盖：

- 数据库写入。
- HTTP 调用。
- 发送短信、邮件、推送。
- 调用第三方支付或发券接口。

结论：Kafka exactly-once 不是业务 exactly-once。业务 exactly-once 仍需要本地事务、outbox/inbox、幂等键和补偿设计。

### RabbitMQ

RabbitMQ publisher confirm 表示 broker 接收了消息，consumer ack 表示消费者确认处理成功。两者解决的问题不同：

- publisher confirm：生产者确认消息是否到 broker。
- consumer ack：broker 确认消息是否可从队列删除。

confirm 成功不代表消费者成功，consumer ack 成功也不代表生产侧业务事务一定正确。

## 对账与补偿

生产系统不能只依赖实时链路，还需要离线或准实时对账：

| 对账对象 | 检查内容 | 修复动作 |
| --- | --- | --- |
| business table vs outbox | 已成功业务是否都有 outbox event | 补写 outbox 或人工排查 |
| outbox vs broker | outbox `NEW/FAILED` 是否堆积 | relay 重试或告警 |
| broker vs inbox | 消息是否被目标 consumer 处理 | 重放消息或修复 consumer |
| inbox vs side effect | inbox 成功但外部副作用是否真实完成 | 查询外部系统并补偿 |
| DLQ/DLT | 死信是否按 SLA 清理 | 修复数据、发布重放、归档 |

补偿原则：

- 补偿也要幂等。
- 补偿记录要可审计。
- 不能无限自动重试不可恢复错误。
- 人工修复入口必须有权限、审计和灰度。

## 面试追问与回答要点

| 追问 | 回答要点 |
| --- | --- |
| `@Transactional` 为什么会失效？ | Spring AOP 代理机制、self-invocation、非 public、异常被吞、checked exception 默认不回滚、异步线程不传播事务 |
| 事务传播怎么选？ | 默认 `REQUIRED`；审计/独立失败记录可用 `REQUIRES_NEW` 但要警惕外部事务回滚后的孤儿记录；长耗时外部调用不要放事务内 |
| 隔离级别怎么选？ | 先按数据库默认，结合业务一致性和锁冲突评估；高隔离降低并发，不是越高越好 |
| 接口重复提交怎么办？ | 客户端幂等键、业务唯一键、幂等表、响应快照、状态机 |
| HTTP 成功但消息发送失败怎么办？ | outbox：业务数据和事件同事务提交，后台 relay 投递 |
| 消费成功但 ack/offset 失败怎么办？ | at-least-once 下会重复消费，消费端必须 inbox 或业务唯一键幂等 |
| Kafka exactly-once 能保证数据库 exactly-once 吗？ | 不能。Kafka EOS 只覆盖 Kafka 内事务边界，外部数据库/HTTP 仍需业务幂等或 outbox |
| RabbitMQ confirm 和 ack 的区别？ | confirm 是 producer 到 broker，ack 是 consumer 到 broker，关注点不同 |
| DLT/DLQ 怎么重放？ | 先修复数据或代码，按 eventId/inbox 做幂等检查，限速重放，记录审计 |
| 内存幂等为什么不够？ | 重启丢失、多实例不共享、无法支撑重放和审计 |
| TCC/Saga/2PC 怎么选？ | 2PC 强一致但耦合和可用性成本高；TCC 需要业务 Try/Confirm/Cancel；Saga 适合长事务和最终一致 |
| 对账怎么设计？ | 按业务表、outbox、broker、inbox、外部副作用做多层校验和补偿 |
| 事务里能不能调 HTTP？ | 不建议。HTTP 慢且不可控，会拉长锁持有时间；如果必须调用，外部接口必须幂等且有超时 |
| 失败重试会不会造成重复？ | 会。重试只能提高成功率，不能替代幂等 |
| 补偿和回滚有什么区别？ | 回滚是事务内撤销，补偿是事务提交后用反向业务动作修复结果 |

## 自检清单

- 能说明当前项目为什么不宣称业务 exactly-once。
- 能把 RabbitMQ/Kafka 示例里的内存幂等升级为 inbox/outbox 设计。
- 能解释 `@Transactional` 失效和传播行为。
- 能画出 outbox relay 的时序。
- 能列出 DLQ/DLT 重放前必须检查的条件。
- 能说明补偿任务为什么也必须幂等。
