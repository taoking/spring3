# 消息队列后续计划

当前版本不实现 Kafka、RabbitMQ、RocketMQ，只保留后续学习路线。

## Kafka

适合高吞吐、日志流、事件流、数据管道和需要持久化顺序消费的场景。

后续建议学习：

- topic、partition、offset、consumer group。
- `spring-kafka` 的 producer、consumer、listener container。
- 手动提交 offset、重试、死信 topic。
- 顺序消费、消息键、幂等 producer。
- Micrometer 指标和 consumer lag 监控。

## RabbitMQ

适合业务异步解耦、路由灵活、延迟/重试/死信队列等场景。

后续建议学习：

- exchange、queue、binding、routing key。
- direct、topic、fanout、headers exchange。
- `spring-boot-starter-amqp`、`RabbitTemplate`、`@RabbitListener`。
- ack、nack、死信队列、延迟消息。
- 消息可靠性：publisher confirm、return callback、消费幂等。

## RocketMQ

适合事务消息、顺序消息、大规模业务消息和国内生态常见中间件场景。

后续建议学习：

- topic、tag、consumer group。
- 普通消息、顺序消息、延时消息、事务消息。
- Spring Boot RocketMQ starter。
- 消费重试、死信、消息轨迹。
- 与 Kafka、RabbitMQ 在业务语义和运维复杂度上的取舍。

## 建议实现顺序

1. RabbitMQ：先理解 exchange/queue/routing 的业务建模。
2. Kafka：再学习分区、消费组、offset 和流式处理。
3. RocketMQ：最后学习事务消息、顺序消息和国内生产实践。
