# 18 Kafka 专题计划

## 目标

基于当前 Spring Boot 3 学习项目补充 Kafka 常用场景专题，用于学习、实战演练和资深 Java 面试准备。

本专题已完成 Kafka 基线实现，并保留后续深化 prompt。实现必须持续保持默认 profile 轻量，不接入数据库和 Redis，不影响已经完成的 RabbitMQ 示例。

## 任务 Prompt

```text
为当前 Spring Boot 3 多模块项目补充 Kafka 专题模块。请先阅读：

- docs/task-plans/18-kafka.md
- docs/task-plans/15-messaging.md
- docs/messaging-roadmap.md
- docs/USAGE.md
- docs/IMPLEMENTATION.md

目标：
1. 在不污染默认运行路径的前提下，增加 Kafka 学习和实战示例。
2. 使用独立 Maven profile `kafka` 和独立 Spring profile `kafka`。
3. 默认 `./mvnw test` 不需要 Kafka，不编译 Kafka 专题源码，不启动 Kafka 容器。
4. 复用当前 `order-service` 的订单预览事件语义，围绕 `OrderPreviewCreated` 设计 Kafka event。
5. 覆盖生产、消费、分区顺序、消费组、offset、幂等、重试、DLT、失败分类和观测指标。
6. 使用 Testcontainers Kafka 做自动化集成测试，使用固定镜像版本，不能使用 `latest`。
7. 增加本地 Docker Compose：`platform/kafka/docker-compose.yml`。
8. 更新 README、docs/USAGE.md、docs/messaging-roadmap.md 和本 task plan 的验收记录。

实现边界：
1. 不接入数据库、Redis、Schema Registry、Flink、Kafka Streams 或真实外部系统。
2. 不宣称业务 exactly-once。可以演示 Kafka producer 幂等和 Kafka 内部事务边界，但必须说明外部副作用仍需要业务幂等、inbox/outbox 或持久化去重。
3. 不把 Kafka 作为 order preview 主链路的必需依赖。Kafka 只在 `-Pkafka` + `SPRING_PROFILES_ACTIVE=kafka` 下启用。
4. 不提交真实密码、SASL 配置、证书或生产 bootstrap servers。
5. 不删除 RabbitMQ 现有实现和文档。

建议文件范围：
1. `order-service/pom.xml`：新增 `kafka` profile，引入 `spring-kafka`、`spring-kafka-test`、Testcontainers Kafka，并用 build-helper 加入 `src/kafka/java` 和 `src/kafka-test/java`。
2. `order-service/src/main/resources/application-kafka.yml`：Kafka bootstrap、topic、producer、consumer、retry、DLT、manual ack、metrics 配置。
3. `order-service/src/kafka/java/com/taoking/spring3/order/messaging/kafka/`：Kafka 专题代码。
4. `order-service/src/kafka-test/java/com/taoking/spring3/order/messaging/kafka/`：Testcontainers 集成测试。
5. `platform/kafka/docker-compose.yml`：本地 Kafka KRaft 单节点环境和可选 Kafka UI。
6. `docs/kafka-playbook.md`：运行方式、场景说明、面试复盘。
7. `.github/workflows/ci.yml`：如果执行成本可接受，Docker job 增加 Kafka IT；如果成本较高，先文档化本地命令并保留后续 CI 扩展说明。

验收命令至少包含：

./mvnw test
./mvnw -Pkafka -pl order-service -am test -DskipTests
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
docker compose -f platform/kafka/docker-compose.yml config
```

## 当前实现

- `order-service` 新增 `kafka` Maven profile，默认构建和运行不引入 Kafka 依赖。
- `order-service/src/main/resources/application-kafka.yml` 配置 Kafka producer/consumer、topic、manual ack、重试、DLT 和演示参数。
- `order-service/src/kafka/java` 隔离 Kafka 代码：
  - `KafkaOrderPreviewEventPublisher`：监听 `OrderPreviewCreatedEvent`，发送 `OrderPreviewKafkaEvent`。
  - `KafkaOrderPreviewConsumer`：消费订单预览事件，使用 manual ack，成功后提交 offset。
  - `KafkaOrderMessagingConfig`：声明主 topic、retry topic、DLT、listener container factory 和 DLT error handler。
  - `ProcessedKafkaEventStore` / `InMemoryProcessedKafkaEventStore`：基于 eventId 做演示级幂等。
  - `OrderPreviewKafkaEvent`：封装 eventId、eventVersion、partitionKey、requestId、traceId 和 payload。
- `platform/kafka/docker-compose.yml` 提供本地 Kafka + Kafka UI。
- `OrderKafkaProfileIT` 使用 Testcontainers `confluentinc/cp-kafka:7.6.1` 验证生产消费、请求/链路字段、幂等、同 key 顺序和 DLT。
- `.github/workflows/ci.yml` 在 Docker job 中增加 Kafka IT 命令。
- [Kafka 使用与面试专题](../kafka-playbook.md) 记录本地运行、事件设计、验收命令和面试复盘。

## 场景类型总览

| 场景 | 学习目标 | 核心功能 | 必须覆盖的风险 |
| --- | --- | --- | --- |
| 基础生产消费 | 理解 topic、producer、consumer、listener | `KafkaTemplate`、`@KafkaListener`、JSON 序列化 | 发送失败、反序列化失败、消费者未启动 |
| 分区和顺序 | 理解 partition 内有序、跨 partition 无全局顺序 | message key、partition count、listener concurrency | key 选择错误、扩分区后 key 重新映射、并发破坏业务顺序 |
| 消费组和 offset | 理解 consumer group、rebalance、offset commit | group id、manual ack、commit after success | 处理成功但提交失败导致重复、提交过早导致丢消息 |
| 幂等消费 | 接受 at-least-once 下重复消息是常态 | eventId、dedupe store 抽象、重复跳过 | demo 只能内存去重，生产必须持久化 |
| 失败重试和 DLT | 区分瞬时失败、业务失败和 poison message | retry topic / DLT、错误分类、`DeadLetterPublishingRecoverer` | 阻塞重试拖慢 partition、DLT 不是自动修复 |
| 生产可靠性 | 理解 acks、retries、idempotent producer | `acks=all`、`enable.idempotence=true`、send callback | broker ack 丢失、重复发送、吞吐和可靠性取舍 |
| 观测和排障 | 看到 lag、吞吐、失败、重复 | Micrometer、Actuator、日志字段、consumer lag | 只看应用日志不看 broker/consumer lag |
| 事务和 exactly-once 边界 | 澄清 Kafka EOS 和业务 EOS 的区别 | producer transaction、read-process-write 边界 | 把 Kafka exactly-once 误认为外部系统 exactly-once |
| Schema 演进 | 学习事件版本兼容 | eventVersion、可选字段、向后兼容测试 | 删除字段、重命名字段、消费者升级不同步 |

## 事件设计

### Topic 设计

本专题建议先使用三个 topic：

| Topic | 用途 | 分区 | 复制因子 | 说明 |
| --- | --- | --- | --- | --- |
| `spring3.order-preview.events.v1` | 订单预览创建事件主 topic | 3 | 本地 1 | 用于生产消费、顺序和消费组演示 |
| `spring3.order-preview.retry.v1` | 可选重试 topic | 3 | 本地 1 | 用于非阻塞重试，避免阻塞主 topic |
| `spring3.order-preview.dlt.v1` | 死信 topic | 3 | 本地 1 | 保存 poison message、反序列化失败或重试耗尽消息 |

本地学习环境复制因子可以是 1。面试复盘时必须能说明生产环境需要多 broker、副本、ISR 和 `min.insync.replicas` 配套。

### Event 结构

建议事件名：`OrderPreviewCreatedV1`。

```json
{
  "eventId": "preview-8f53f2a1-2c2c-45f2-a3ff-0f7f5a20d7a2",
  "eventType": "OrderPreviewCreated",
  "eventVersion": 1,
  "source": "order-service",
  "occurredAt": "2026-05-14T10:00:00Z",
  "aggregateType": "ORDER_PREVIEW",
  "aggregateId": "preview-8f53f2a1-2c2c-45f2-a3ff-0f7f5a20d7a2",
  "partitionKey": "preview-8f53f2a1-2c2c-45f2-a3ff-0f7f5a20d7a2",
  "requestId": "9bd7c1f7-f1a0-4816-918f-e1d0dfc855d4",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "payload": {
    "orderId": "preview-8f53f2a1-2c2c-45f2-a3ff-0f7f5a20d7a2",
    "sku": "SKU-1001",
    "quantity": 2,
    "fallbackUsed": false
  }
}
```

### Header 设计

| Header | 用途 |
| --- | --- |
| `eventId` | 幂等键，消费端去重 |
| `eventType` | 消费端路由和排障 |
| `eventVersion` | 兼容升级 |
| `traceparent` | 链路追踪透传 |
| `X-Request-Id` | 日志关联 |
| `source` | 事件来源服务 |

### Key 设计

默认使用 `orderId` 或 `event.aggregateId` 作为 Kafka key。

原因：

- 同一个订单预览相关事件进入同一 partition，保证 partition 内顺序。
- key 分布通常比固定业务类型更均匀。
- 面试时可以讨论：如果业务要求同一 `sku` 维度严格有序，可以切换为 `sku`，但热点 SKU 会造成 partition skew。

## 分阶段实施计划

### 阶段 1：依赖、配置和本地 Kafka

核心功能：

- 新增 Maven `kafka` profile。
- 新增 Spring `application-kafka.yml`。
- 新增 `platform/kafka/docker-compose.yml`，建议使用 KRaft 单节点，固定镜像版本。
- 增加 `KafkaTopicsProperties`、`KafkaOrderMessagingProperties` 等配置绑定类。
- 使用 `KafkaAdmin` 或 `NewTopic` 声明 topic。

验收：

- `./mvnw test` 不受影响。
- `./mvnw -Pkafka -pl order-service -am test -DskipTests` 可编译 Kafka 专题源码。
- `docker compose -f platform/kafka/docker-compose.yml config` 通过。

### 阶段 2：基础生产消费

核心功能：

- 监听当前应用内 `OrderPreviewCreatedEvent`。
- 使用 `KafkaTemplate<String, OrderPreviewCreatedV1>` 发送事件。
- 发送时设置 key、headers 和 send callback。
- 使用 `@KafkaListener` 消费主 topic，记录消费成功指标。

失败处理：

- producer send callback 记录 topic、partition、offset 或异常。
- consumer 反序列化失败必须进入 DLT 或被错误处理器捕获。
- 业务消费者不能吞异常后直接 ack。

验收：

- 调用 `/api/orders/preview` 后能发送 Kafka 事件。
- 消费者能收到事件并记录 eventId、partition、offset。
- 日志和指标能看到 produced/consumed counter。

### 阶段 3：分区、顺序和 consumer group

核心功能：

- 主 topic 设置 3 个 partition。
- 使用 key 让同一 `orderId` 的事件进入同一 partition。
- listener 配置 concurrency，演示并发消费和 partition 分配。
- 增加一个只用于测试的多事件发送入口或测试 helper，连续发送同一 key 的事件序列。

失败处理：

- 明确只保证同一 partition 内有序。
- 不在单个 partition 内对同一 key 做无序异步处理。
- 文档说明扩分区会影响 key 到 partition 的映射，新旧消息跨 partition 时不能再假设绝对有序。

验收：

- Testcontainers 测试验证同一 key 的多条消息按发送顺序消费。
- 不同 key 可以分布到不同 partition。
- consumer group 内同一个 partition 同一时刻只被一个 consumer 实例消费。

### 阶段 4：offset、manual ack 和重复消费

核心功能：

- 关闭 auto commit。
- listener 使用 manual ack，业务处理成功后再 ack。
- 记录消费前后的 topic、partition、offset。
- 增加 `ProcessedKafkaEventStore` 抽象，demo 实现为内存 Set。

失败处理：

- 处理成功但 ack/commit 失败，重启或 rebalance 后可能重复消费。
- 处理失败不 ack，由错误处理器接管 retry/DLT。
- demo 的内存 Set 只用于学习，生产必须用数据库、Redis、compact topic 或外部幂等服务做持久化去重。本项目不接入数据库和 Redis，因此只做边界说明。

验收：

- 同一 `eventId` 重复投递两次，业务 side effect 只执行一次。
- 重复消息必须 ack，避免一直重试。
- 文档能解释 at-least-once 为什么要求消费端幂等。

### 阶段 5：重试、失败分类和 DLT

核心功能：

- 定义错误分类：
  - 瞬时异常：网络超时、临时不可用，可以 retry。
  - 业务不可重试异常：非法事件状态、字段缺失，可以直接 DLT 或跳过。
  - poison message：反序列化失败、schema 不兼容，进入 DLT。
- 选择一种 Spring Kafka 实现：
  - 简单路线：`DefaultErrorHandler` + `DeadLetterPublishingRecoverer`。
  - 进阶路线：`@RetryableTopic` 生成 retry topic 和 DLT。
- DLT 消息保留原始 topic、partition、offset、exception、eventId 等 header。

失败处理：

- 阻塞重试会阻塞同一 partition 后续消息，适合短暂、少量重试。
- retry topic 可以释放主 topic，但会引入更多 topic 和延迟调度复杂度。
- DLT 是隔离失败消息，不是自动补偿。必须有人工排查或重放策略。

验收：

- poison SKU 触发消费失败，重试耗尽后进入 DLT。
- DLT 里可以看到原始 eventId 和异常类型。
- retry 次数、DLT 计数有 Micrometer 指标。

### 阶段 6：生产可靠性和 Kafka 事务边界

核心功能：

- producer 配置 `acks=all`、`retries`、`delivery.timeout.ms`、`request.timeout.ms`。
- 启用 idempotent producer。
- 可选演示 Kafka transaction，仅用于 read-process-write 到 Kafka 的内部一致性。

失败处理：

- producer 幂等只能解决 producer 重试导致的 broker 端重复写入问题，不能解决消费者外部副作用重复。
- Kafka transaction 不能自动覆盖 HTTP 调用、邮件、第三方 API 或数据库写入。
- 没有数据库时不实现 outbox，只在文档和面试覆盖中说明生产方案。

验收：

- producer 成功日志包含 topic、partition、offset。
- send failure 记录异常并增加失败指标。
- 文档能解释 Kafka exactly-once 的适用边界。

### 阶段 7：观测、排障和运维视角

核心功能：

- 通过 Micrometer 暴露 produced、consumed、duplicate、retry、dlt、sendFailure counters。
- 日志包含 eventId、topic、partition、offset、consumerGroup、traceId、requestId。
- 在 `docs/kafka-playbook.md` 中记录 consumer lag、rebalance、partition skew 排查步骤。

验收：

- `/actuator/prometheus` 能看到 Kafka 专题自定义指标。
- 文档能说明 lag 增长的常见原因和处理顺序。

## 测试计划

### 单元测试

- Event mapper：从 `OrderPreviewCreatedEvent` 正确生成 `OrderPreviewCreatedV1`。
- Key selector：同一 orderId 产生相同 key。
- 幂等 store：重复 eventId 只处理一次。
- 错误分类：瞬时异常、不可重试异常、poison message 分类正确。
- header builder：eventId、eventType、eventVersion、traceparent、requestId 不缺失。

### Spring Profile 测试

- 默认 profile 不存在 Kafka listener/container 相关 Bean。
- `kafka` profile 下 Kafka 配置 Bean、topic Bean、producer、consumer 可以装配。
- `application-kafka.yml` 的默认 topic、group、retry、DLT 配置可以绑定。

### Testcontainers 集成测试

建议测试类：`OrderKafkaProfileIT`。

必须覆盖：

1. 启动 KafkaContainer，动态注入 `spring.kafka.bootstrap-servers`。
2. 调用订单预览接口后，主 topic 收到 `OrderPreviewCreatedV1`。
3. Kafka listener 消费成功，消费指标递增。
4. 同一 key 连续消息保持消费顺序。
5. 重复 eventId 被跳过，业务 side effect 只执行一次。
6. poison SKU 触发 retry，最终进入 DLT。
7. DLT 消息保留原始 eventId、topic、partition、offset 和异常 header。
8. 消费成功后再 ack，测试中能观察 offset 推进。

可选覆盖：

- 两个 consumer group 都能各自收到消息，说明 Kafka 可以同时支持多订阅视角。
- 同一 group 多实例时，partition 被分配给不同 consumer。
- 反序列化失败进入 DLT。

## 验收标准

- 默认 `./mvnw test` 通过，且默认 profile 不需要 Kafka。
- `-Pkafka` 可以编译 Kafka 专题源码。
- `kafka` Spring profile 可以启动 `order-service`，并完成订单预览事件生产消费。
- Testcontainers Kafka 集成测试通过，覆盖生产、消费、顺序、幂等、重试和 DLT。
- Docker Compose 配置可解析，服务命名和端口不与 RabbitMQ/Nacos/观测栈冲突。
- README、docs/USAGE.md、docs/messaging-roadmap.md 和 `docs/kafka-playbook.md` 已更新。
- 文档明确说明本项目不接入数据库和 Redis，因此 Kafka 幂等示例是内存级演示，不是生产级持久化去重。
- 文档能对比 Kafka、RabbitMQ、RocketMQ 的模型差异和选型取舍。

## 验收命令

```bash
./mvnw test
./mvnw -Pkafka -pl order-service -am test -DskipTests
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
docker compose -f platform/kafka/docker-compose.yml config
```

如果后续把 Kafka IT 加入 CI，还需要确认 `.github/workflows/ci.yml` 的 Docker job 总耗时可接受。

## 验收记录

- `./mvnw -Pkafka -pl order-service -am test -DskipTests`：通过，验证 Kafka profile 源码和测试源码可编译。
- `docker compose -f platform/kafka/docker-compose.yml config`：通过，验证本地 Kafka Compose 配置可解析。
- `./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify`：通过，4 个 Kafka IT 覆盖生产消费、requestId/traceId、幂等、同 key 顺序和 DLT。
- `./mvnw test`：通过，验证默认 profile 不依赖 Kafka。
- `git diff --check`：通过。

## 面试覆盖

### Kafka 介绍

需要能用简洁语言讲清楚：

- Kafka 是分布式事件流平台，不只是传统消息队列。
- 核心抽象是 topic、partition、offset、producer、consumer、consumer group。
- partition 是并行度、顺序性和吞吐的基本单位。
- Kafka 通过追加写日志、顺序 I/O、page cache、批量发送和零拷贝等机制获得高吞吐。
- 消费者自己维护 offset，所以同一份消息可以被不同 consumer group 独立消费。
- Kafka 更适合高吞吐事件流、日志流、数据管道和多订阅场景；RabbitMQ 更适合复杂业务路由和传统队列语义；RocketMQ 在事务消息、延时消息、国内业务消息生态里常见。

### 高频重难点

| 主题 | 面试官想确认什么 | 回答要点 |
| --- | --- | --- |
| 顺序性 | 是否知道 Kafka 没有全局顺序 | partition 内有序；同一业务 key 进同一 partition；跨 partition 无序 |
| 消费组 | 是否理解竞争消费和广播效果 | 同 group 内分摊 partition；不同 group 各自消费完整消息流 |
| Offset | 是否知道提交时机影响丢失/重复 | 处理后提交是 at-least-once；处理前提交可能丢消息 |
| 幂等 | 是否接受重复消息不可避免 | eventId/business key 去重；生产要持久化去重 |
| Retry/DLT | 是否能设计失败隔离 | 区分瞬时失败和 poison message；retry topic 或 error handler；DLT 后要可观测可重放 |
| Rebalance | 是否知道消费抖动来源 | consumer 加减、超时、partition 变化会触发；期间可能暂停消费或重复 |
| Lag | 是否能排查堆积 | 看生产速率、消费耗时、partition skew、下游慢、rebalance、broker 状态 |
| Producer 可靠性 | 是否理解 acks 和 ISR | `acks=all`、副本、ISR、`min.insync.replicas`、retries、idempotence |
| Exactly-once | 是否能澄清边界 | Kafka EOS 主要覆盖 Kafka 内 read-process-write；外部系统仍需幂等/outbox |
| Schema 演进 | 是否能做长期兼容 | version、只加可选字段、保留语义、消费者先兼容后生产升级 |
| 扩分区 | 是否知道扩容副作用 | 提升并行度但可能改变 key 到 partition 的映射，影响严格顺序假设 |
| Spring Kafka | 是否有工程实践 | `KafkaTemplate`、`@KafkaListener`、manual ack、ErrorHandlingDeserializer、DLT |

### 追问清单

1. Kafka 为什么吞吐高？
   - 重点：顺序追加写、批量、压缩、page cache、零拷贝、partition 并行。

2. Kafka 如何保证消息不丢？
   - 重点：producer `acks=all`、retries、idempotence；broker 副本和 ISR；consumer 处理成功后提交 offset。仍要区分不丢和不重复。

3. Kafka 如何保证顺序？
   - 重点：只保证同一 partition 内顺序；同一业务 key 进入同一 partition；消费者不能在同一 key 上并发乱序处理。

4. 消费者处理成功但提交 offset 失败会怎样？
   - 重点：可能重复消费，所以业务必须幂等。

5. 消费者先提交 offset 再处理业务会怎样？
   - 重点：处理失败会造成消息丢失，通常不适合作为可靠消费方案。

6. 什么情况下会重复消息？
   - 重点：producer retry ack 丢失、consumer 处理成功但 commit 失败、rebalance、应用重启、DLT 重放。

7. 如何设计幂等？
   - 重点：eventId、业务唯一键、幂等表、inbox、唯一约束、状态机版本号。当前项目无 DB/Redis，只做内存演示。

8. Retry topic 和阻塞重试怎么选？
   - 重点：阻塞重试简单但会阻塞 partition；retry topic 释放主消费但增加 topic 和延迟复杂度。

9. DLT 里的消息怎么处理？
   - 重点：告警、排查、修复数据或代码、可控重放、避免无限循环。

10. Kafka 的 exactly-once 是不是业务 exactly-once？
    - 重点：不是。Kafka EOS 对 Kafka 内部读处理写有效；外部数据库、HTTP、邮件等副作用需要业务幂等或事务/outbox。

11. Consumer lag 一直上涨怎么排查？
    - 重点：看生产速率是否突增、消费耗时、下游依赖、partition skew、consumer 数量、rebalance、GC、broker 磁盘/网络。

12. 一个 consumer group 里 consumer 数量超过 partition 数会怎样？
    - 重点：多出来的 consumer 空闲，partition 同一时刻只能分配给 group 内一个 consumer。

13. 扩 partition 有什么风险？
    - 重点：提高并行度，但 key 到 partition 的映射可能改变，严格顺序场景要谨慎。

14. Kafka 和 RabbitMQ 怎么选？
    - 重点：Kafka 偏事件流、高吞吐、多订阅、保留日志；RabbitMQ 偏业务路由、传统队列、ack/nack、DLQ 简洁。

15. Kafka 和 RocketMQ 怎么选？
    - 重点：Kafka 生态和流处理强；RocketMQ 的事务消息、延时消息、业务消息语义和国内生态常见。

16. Spring Kafka 里如何处理反序列化失败？
    - 重点：`ErrorHandlingDeserializer`，否则 listener 可能拿不到消息；配合 DLT 保存原始错误。

17. 如何避免消费者长时间处理触发 rebalance？
    - 重点：调整 `max.poll.interval.ms`、减小单批处理、异步处理谨慎 ack、优化下游耗时。

18. 如何设计消息字段兼容？
    - 重点：事件版本、只加字段不随意删改字段、消费者容忍未知字段、灰度发布顺序。

## 和当前项目的结合点

- 生产端：复用 `OrderPreviewCreatedEvent`，不改变同步 HTTP 返回语义。
- 消费端：先做 `OrderPreviewNotificationConsumer`，模拟异步通知处理。
- 幂等：复用 RabbitMQ 示例里的 eventId 思路，但 Kafka 计划中需要抽象成通用事件去重接口，便于面试说明 MQ 无关。
- 观测：复用现有 Micrometer、Prometheus、traceId/requestId 日志体系。
- 失败演示：复用 poison SKU 思路，例如 `SKU-KAFKA-FAIL` 触发消费失败和 DLT。
- 文档：在 `docs/messaging-roadmap.md` 中对比 RabbitMQ 已完成基线和 Kafka 待实现专题。

## 不做

- 不接入数据库、Redis、Schema Registry、Kafka Streams、Flink。
- 不做生产级 outbox/inbox，只解释设计方式。
- 不做 SASL/TLS 安全接入，只保留面试和生产化说明。
- 不删除 RabbitMQ 示例。
- 不把 Kafka 加入默认 profile、默认测试或默认启动链路。
