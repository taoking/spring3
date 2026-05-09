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

## 当前实现

已选择 RabbitMQ 作为第一套可执行示例，Kafka 和 RocketMQ 保留路线对比。

实现范围：

- `order-service` 新增 `rabbitmq` Maven profile，默认构建和运行不引入 AMQP starter。
- `order-service/src/main/resources/application-rabbitmq.yml` 配置 RabbitMQ 连接、publisher confirm、listener retry、主队列和 DLQ。
- `order-service/src/rabbitmq/java` 隔离 RabbitMQ 代码：
  - `RabbitOrderPreviewEventPublisher`：监听 `OrderPreviewCreatedEvent` 并发布 `OrderPreviewMessage`。
  - `RabbitOrderPreviewConsumer`：消费订单预览消息，使用 `eventId` 做幂等，指定 poison SKU 时模拟失败。
  - `RabbitOrderMessagingConfig`：声明 exchange、queue、binding、DLX、DLQ 和 JSON message converter。
  - `RabbitOrderMessagingProperties`：绑定 `demo.messaging.rabbitmq.*` 配置。
- `platform/rabbitmq/docker-compose.yml` 提供本地 RabbitMQ + Management UI。
- `OrderRabbitMqProfileIT` 使用 Testcontainers `rabbitmq:3.13-management` 验证生产/消费、幂等和 DLQ。
- `.github/workflows/ci.yml` 在 Docker job 中增加 RabbitMQ IT 命令。

## 实施要点

- Kafka 重点：partition、consumer group、offset、顺序和幂等。
- RabbitMQ 重点：exchange、routing key、ack、dead letter。
- RocketMQ 重点：tag、consumer group、延迟消息、顺序消息。
- 示例不要同时实现三套，优先一套跑通。

## 验收标准

- 默认 `./mvnw test` 通过。
- `rabbitmq` profile 可启动并完成生产消费。
- 消费失败能触发重试并进入死信队列。
- 文档能比较 Kafka/RabbitMQ/RocketMQ 的典型适用场景。

## 验证命令

```bash
./mvnw -Prabbitmq -pl order-service -am test -DskipTests
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

最终提交前还需要跑默认回归：

```bash
./mvnw test
```

## 不做

- 不接入数据库事务消息。
- 不把 MQ 作为核心业务强依赖。
