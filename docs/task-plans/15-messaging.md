# 15 消息队列计划

## 目标

在不污染默认运行路径的前提下，补充 Kafka、RabbitMQ、RocketMQ 的学习路线和可选示例，重点理解投递语义、幂等、重试和死信。

## 任务 Prompt

```text
为当前项目补充消息队列专题。请先阅读 docs/messaging-roadmap.md 和 docs/task-plans/15-messaging.md。

要求：
1. 不把任何消息队列变成默认运行依赖。
2. 优先选择一个消息队列做可执行示例，其他队列保留文档对比。
3. 使用独立 profile 和独立 Docker Compose。
4. 示例围绕 order preview event，演示生产、消费、重试、死信或幂等。
5. 增加测试或 Testcontainers 集成测试。
6. 更新 docs/messaging-roadmap.md 和 docs/USAGE.md。
```

## 示例内容

- 订单预览成功后发布 `OrderPreviewCreated` 消息。
- 消费者异步处理通知。
- 消费失败进入重试或死信队列。
- 使用 eventId 做幂等。

## 实施要点

- Kafka 重点：partition、consumer group、offset、顺序和幂等。
- RabbitMQ 重点：exchange、routing key、ack、dead letter。
- RocketMQ 重点：tag、consumer group、延迟消息、顺序消息。
- 示例不要同时实现三套，优先一套跑通。

## 验收标准

- 默认 `./mvnw test` 通过。
- 消息队列 profile 可启动并完成生产消费。
- 消费失败能触发重试或死信。
- 文档能比较 Kafka/RabbitMQ/RocketMQ 的典型适用场景。

## 不做

- 不接入数据库事务消息。
- 不把 MQ 作为核心业务强依赖。
