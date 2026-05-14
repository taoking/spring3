# 消息队列专题路线

当前项目已完成 RabbitMQ 和 Kafka 可选基线：默认 profile 不引入消息队列依赖，只有同时使用对应 Maven profile 和 Spring profile 时才启用。

## 当前 RabbitMQ 示例

围绕订单预览事件演示消息队列核心链路：

| 能力 | 当前实现 |
| --- | --- |
| 生产 | `RabbitOrderPreviewEventPublisher` 监听 `OrderPreviewCreatedEvent`，发布 `OrderPreviewMessage` |
| 消费 | `RabbitOrderPreviewConsumer` 使用 `@RabbitListener` 消费主队列 |
| 路由 | direct exchange + routing key + durable queue |
| 幂等 | 使用 `eventId`，当前以 `orderId` 作为事件 ID，重复消息跳过 |
| 重试 | Spring AMQP listener retry，默认最大 3 次 |
| 死信 | 主队列配置 DLX/DLQ，重试耗尽后进入死信队列 |
| 指标 | 发布、消费、重复、失败分别有 Micrometer Counter |
| 测试 | `OrderRabbitMqProfileIT` 使用 Testcontainers `rabbitmq:3.13-management` |

关键文件：

- `order-service/src/main/resources/application-rabbitmq.yml`
- `order-service/src/rabbitmq/java/com/taoking/spring3/order/messaging/rabbitmq/`
- `order-service/src/rabbitmq-test/java/com/taoking/spring3/order/messaging/rabbitmq/OrderRabbitMqProfileIT.java`
- `platform/rabbitmq/docker-compose.yml`

本地运行：

```bash
docker compose -f platform/rabbitmq/docker-compose.yml up -d
./mvnw -pl catalog-service spring-boot:run
SPRING_PROFILES_ACTIVE=rabbitmq ./mvnw -Prabbitmq -pl order-service spring-boot:run
```

自动化验证：

```bash
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

## Kafka

适合高吞吐、日志流、事件流、数据管道和需要持久化顺序消费的场景。

详细执行计划、任务 prompt、场景拆分、事件设计、测试验收和面试追问见 [Kafka 专题计划](task-plans/18-kafka.md)，本地运行和面试复盘见 [Kafka 使用与面试专题](kafka-playbook.md)。

当前 Kafka 示例：

| 能力 | 当前实现 |
| --- | --- |
| 生产 | `KafkaOrderPreviewEventPublisher` 监听 `OrderPreviewCreatedEvent`，通过 `KafkaTemplate` 发布 `OrderPreviewKafkaEvent` |
| 消费 | `KafkaOrderPreviewConsumer` 使用 `@KafkaListener` 消费订单预览 topic |
| 分区顺序 | 使用 `orderId` / `partitionKey` 作为 message key，同一 key 落到同一 partition |
| Offset | 关闭 auto commit，listener 使用 manual ack，业务处理成功后提交 |
| 幂等 | `ProcessedKafkaEventStore` 基于 eventId 做内存去重，重复消息跳过并 ack |
| 重试/DLT | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`，消费失败重试后进入 DLT |
| 指标 | 发布、发送失败、消费成功、重复、失败分别有 Micrometer Counter |
| 测试 | `OrderKafkaProfileIT` 使用 Testcontainers Kafka 覆盖生产消费、幂等、同 key 顺序和 DLT |

后续建议学习：

- producer transaction、read-process-write exactly-once 边界。
- retry topic 和 blocking retry 的取舍。
- consumer lag 面板、rebalance 排查和 partition skew 诊断。
- Schema Registry、Avro/Protobuf/JSON Schema 的长期兼容治理。

建议后续示例：

- 增加 Kafka producer transaction 的最小 read-process-write 示例。
- 使用 retry topic 替换当前 blocking retry，比较两种方案对 partition 后续消息的影响。
- 增加 consumer lag 查询命令和 Grafana 面板。

## RabbitMQ

适合业务异步解耦、路由灵活、延迟/重试/死信队列等场景。

已覆盖：

- `spring-boot-starter-amqp`、`RabbitTemplate`、`@RabbitListener`。
- direct exchange、queue、binding、routing key。
- listener retry、dead letter exchange、dead letter queue。
- 消费端基于 `eventId` 的幂等。
- Testcontainers 集成测试。

后续可继续补：

- publisher confirm 和 return callback 的自动化验证。
- 手动 ack/nack 与 `AcknowledgeMode.MANUAL`。
- topic/fanout exchange 对比示例。
- 延迟消息插件或 TTL + DLX 延迟队列。
- 多消费者并发、prefetch、消费限速和堆积排查。

## RocketMQ

适合事务消息、顺序消息、大规模业务消息和国内生态常见中间件场景。

后续建议学习：

- topic、tag、consumer group。
- 普通消息、顺序消息、延时消息、事务消息。
- Spring Boot RocketMQ starter。
- 消费重试、死信、消息轨迹。
- 与 Kafka、RabbitMQ 在业务语义和运维复杂度上的取舍。

建议后续示例：

- 使用 tag 区分 `OrderPreviewCreated` 和后续订单状态变更。
- 演示顺序消息和同一业务 key 绑定队列。
- 文档补充事务半消息与本项目无数据库边界的取舍。

## 选型对比

| 维度 | RabbitMQ | Kafka | RocketMQ |
| --- | --- | --- | --- |
| 核心模型 | exchange、queue、binding、routing key | topic、partition、offset、consumer group | topic、tag、consumer group、queue |
| 优势 | 业务路由灵活、ack/nack 明确、DLQ 容易理解 | 高吞吐、持久化日志、生态成熟 | 事务消息、顺序消息、延迟消息、国内生态常见 |
| 顺序 | 单队列内有序，并发后需业务设计 | partition 内有序 | 支持顺序消息，依赖队列选择和消费模型 |
| 重试/DLQ | listener retry + DLX/DLQ | retry topic / DLT 或框架封装 | Broker 原生重试和 DLQ 语义较强 |
| 幂等重点 | 消费端 eventId 去重 | producer 幂等、offset 和消费端去重 | 消费端业务 key 去重、事务消息状态回查 |
| 面试高频 | exchange 类型、ack/nack、DLX、publisher confirm | offset 提交、rebalance、consumer lag、exactly-once 边界 | tag 过滤、延迟级别、事务半消息、顺序消费 |

## 建议实现顺序

1. RabbitMQ：已完成基线，用于理解 exchange/queue/routing、ack、retry、DLQ 和幂等。
2. Kafka：下一步学习分区、消费组、offset、consumer lag 和流式处理。
3. RocketMQ：最后学习事务消息、顺序消息和国内生产实践。
