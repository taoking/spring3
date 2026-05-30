# 24 消息队列生产语义计划

## 目标

在当前 RabbitMQ 和 Kafka 可选 profile 基线上，补齐资深面试需要的生产语义、故障处理、排障流程和组件选型。默认 profile 继续不引入消息队列依赖。

## 任务 Prompt

```text
基于当前 Spring Boot 3 学习项目，深化消息队列生产语义专题。请先阅读：

- docs/messaging-roadmap.md
- docs/kafka-playbook.md
- docs/task-plans/18-kafka.md
- docs/task-plans/19-interview-expansion.md
- order-service/src/kafka/java
- order-service/src/rabbitmq/java

目标：
1. 基于订单预览事件梳理 Kafka、RabbitMQ、RocketMQ 的生产语义。
2. Kafka 侧补 producer reliability、partition 顺序、manual ack、consumer lag、rebalance、retry topic、DLT、producer transaction 边界。
3. RabbitMQ 侧补 publisher confirm、return callback、manual ack/nack、prefetch、DLQ、队列堆积排查。
4. RocketMQ 侧补 tag、顺序消息、延迟消息、事务半消息设计说明。
5. 明确当前内存幂等只是 demo，不宣称业务 exactly-once。
6. 输出面试追问、失败矩阵和验收清单。
7. 更新 README、docs/USAGE.md、docs/messaging-roadmap.md、docs/interview-roadmap.md 或 task plan 索引。

验收：
1. 文档能回答 Kafka/RabbitMQ/RocketMQ 的核心区别和选型。
2. 文档能解释幂等、顺序、重试、DLT/DLQ、lag/堆积、rebalance、事务边界。
3. 至少给出 Kafka consumer lag 和 RabbitMQ queue backlog 的本地排查命令。
4. 不改变默认 profile，不引入新的强制外部依赖。
```

## 当前实施结果

- 新增 [消息队列生产语义专题](../messaging-production-playbook.md)。
- Kafka 部分补充可靠生产、顺序性、manual ack、幂等、阻塞重试/retry topic、DLT、consumer lag 和事务边界。
- RabbitMQ 部分补充 publisher confirm、return callback、manual ack/nack、prefetch 和队列堆积排查。
- RocketMQ 部分补充 tag、顺序消息、延迟消息、事务半消息和选型边界。
- 明确当前项目不接数据库和 Redis，因此生产级幂等、outbox/inbox、Redis 去重只做设计说明。

## 场景矩阵

| 场景 | Kafka | RabbitMQ | RocketMQ |
| --- | --- | --- | --- |
| 可靠生产 | `acks=all`、幂等 producer、callback | publisher confirm、return callback | send result、事务半消息 |
| 顺序 | partition 内有序 | 单 queue 内有序 | 顺序消息 |
| 消费确认 | manual offset commit | manual ack/nack | broker offset + retry |
| 重试 | blocking retry、retry topic | retry queue、TTL + DLX | broker retry |
| 死信 | DLT | DLQ | DLQ |
| 堆积排查 | consumer lag、rebalance | ready/unacked/consumers | 消费堆积、消息轨迹 |
| 事务边界 | Kafka 内 read-process-write | 不覆盖外部副作用 | 事务半消息 + 回查 |

## 验收命令

```bash
docker compose -f platform/kafka/docker-compose.yml config
docker compose -f platform/rabbitmq/docker-compose.yml config
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

## 不做

- 不接入真实 RocketMQ broker。
- 不引入数据库或 Redis 做生产级幂等。
- 不把 Kafka transaction 描述成业务 exactly-once。
- 不把 MQ 作为同步主链路的必要依赖。
