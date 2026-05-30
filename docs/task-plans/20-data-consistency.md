# 20 数据一致性与事务边界计划

## 目标

在不接入数据库和 Redis 的前提下，补齐资深 Java 面试中高频的数据一致性、事务边界、幂等、outbox/inbox、消息最终一致性、补偿和对账专题。

本专题是设计型专题，不改变默认运行路径，不新增数据库、Redis 或分布式事务组件。

## 任务 Prompt

```text
为当前项目补充数据一致性与事务边界专题。项目默认仍不接数据库和 Redis。请先阅读：

- README.md
- docs/IMPLEMENTATION.md
- docs/USAGE.md
- docs/interview-roadmap.md
- docs/task-plans/19-interview-expansion.md
- docs/data-consistency-playbook.md
- docs/kafka-playbook.md
- docs/messaging-roadmap.md

目标：
1. 解释当前项目为什么只做演示级消息幂等，不能宣称生产级 exactly-once。
2. 补充本地事务、`@Transactional` 失效、事务传播、隔离级别、锁和 MVCC 的面试要点。
3. 结合当前 Kafka/RabbitMQ 示例，设计 outbox/inbox、幂等表、DLQ/DLT 重放和补偿对账方案。
4. 输出故障矩阵，覆盖 HTTP 成功但消息失败、消息成功但消费失败、消费成功但 ack/offset 失败、服务重启导致内存幂等丢失等场景。
5. 保持默认 profile 轻量，不引入 DB/Redis/MQ 之外的新运行依赖。
6. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或相关 task plan。
7. 记录实施过程到本地日志文件。

验收：
1. 文档有当前项目结合点，不是泛泛的事务资料。
2. 文档包含 outbox/inbox 表结构草图和端到端时序。
3. 文档包含至少 10 个资深面试追问和回答要点。
4. 文档明确 Kafka exactly-once 与业务 exactly-once 的边界。
5. 如没有代码变更，至少运行 `git diff --check`。
```

## 当前实施结果

已新增 [数据一致性与事务边界专题](../data-consistency-playbook.md)，覆盖：

- 当前项目结合点和演示级幂等边界。
- `@Transactional` 常见失效原因。
- 事务传播行为、隔离级别和事务边界取舍。
- 幂等键、幂等表、outbox 表、inbox 表设计草图。
- 当前项目时序与推荐生产时序对比。
- HTTP、MQ、消费、ack/offset、DLQ/DLT、服务重启、多实例等故障矩阵。
- Kafka/RabbitMQ exactly-once 和 confirm/ack 边界。
- 对账、补偿和 DLT/DLQ 重放策略。
- 15 个资深面试追问和回答要点。

## 场景清单

| 场景 | 当前项目 | 生产补齐 |
| --- | --- | --- |
| 重复 HTTP 请求 | 当前没有持久化幂等表 | `Idempotency-Key` + 幂等记录 |
| HTTP 成功但消息失败 | 进程内事件或 MQ publish 可能失败 | outbox 与业务数据同事务 |
| producer 重试 | Kafka/RabbitMQ 可能产生重复投递 | producer confirm/idempotence + consumer 幂等 |
| 消费成功但 ack/offset 失败 | 可能重复消费 | inbox 或业务唯一键 |
| DLT/DLQ 重放 | 内存去重不可靠 | 重放前查 inbox/业务状态 |
| 服务重启 | 内存幂等丢失 | 持久化幂等记录 |
| 多实例消费 | 内存去重不共享 | 共享 inbox 或唯一约束 |
| 下游 HTTP 超时 | 调用方可能重试 | 外部幂等键 + 状态查询 |

## 验收标准

- 能说明当前项目 RabbitMQ/Kafka 的内存去重为什么不是生产级幂等。
- 能说明 `@Transactional` 失效的代理原因和典型解决方式。
- 能说明事务传播和隔离级别的选型取舍。
- 能给出 outbox/inbox/幂等表结构草图。
- 能画出从 HTTP 请求到 outbox、broker、inbox、ack/offset 的时序。
- 能解释 Kafka EOS、producer idempotence、RabbitMQ confirm、consumer ack 的边界。
- 能设计 DLT/DLQ 重放前的校验和补偿流程。
- 能回答至少 10 个数据一致性资深追问。

## 验收命令

```bash
git diff --check
```

如果后续加入代码或配置，再按变更范围补充：

```bash
./mvnw test
```

## 不做

- 不引入数据库。
- 不引入 Redis。
- 不实现生产级 outbox relay 代码。
- 不实现 Seata、TCC、Saga 编排器或 2PC。
- 不宣称当前 Kafka/RabbitMQ 示例具备业务 exactly-once。
