# Kafka 专题实施日志

## 2026-05-14

- 开始基于 `docs/task-plans/18-kafka.md` 执行 Kafka 专题开发。
- 确认当前已有未提交的 Kafka 计划文档，本次开发以该文档为输入继续推进。
- 审查 `order-service` 的 RabbitMQ 可选 profile，实现模式为独立 Maven profile、独立源码目录、独立 Spring profile 和 Testcontainers 集成测试。
- 决定 Kafka 专题沿用同样边界：默认 `./mvnw test` 不引入 Kafka，只有 `-Pkafka` 和 `SPRING_PROFILES_ACTIVE=kafka` 同时启用时才加载 Kafka 示例。
- 新增 `order-service` 的 `kafka` Maven profile，使用 `build-helper-maven-plugin` 隔离 `src/kafka/java` 和 `src/kafka-test/java`。
- 新增 `application-kafka.yml`，配置 producer idempotence、`acks=all`、manual ack、JSON 序列化、topic、DLT 和演示 poison SKU。
- 新增 Kafka 业务代码：事件模型、topic 声明、error handler、publisher、consumer 和内存幂等 store。
- 新增 `platform/kafka/docker-compose.yml`，提供本地 Kafka KRaft 单节点和 Kafka UI。
- 执行 `./mvnw -Pkafka -pl order-service -am test -DskipTests`，Kafka profile 编译通过。
- 新增 `OrderKafkaProfileIT`，覆盖生产消费、requestId/traceId 事件字段、重复 eventId 幂等、同 key 顺序和 DLT。
- 更新 README、消息队列路线和 CI，补充 Kafka 运行入口与 Testcontainers 命令。
- 首次执行 Kafka IT 失败两项：traceId 优先取到了 Spring 当前 span 而非入站 `traceparent`，DLT 顶层异常为 `ListenerExecutionFailedException`。已修正为优先解析入站 `traceparent`，并在测试中同时校验 DLT wrapper 和 cause header。
- 重新执行 `./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify`，4 个 Kafka IT 全部通过。
- 执行默认 `./mvnw test`，全部通过，确认 Kafka 可选 profile 未影响默认构建。
- 执行 `git diff --check` 和 `docker ps`，空白检查通过，Testcontainers 未留下运行中的容器。
- 执行 `docker compose -f platform/kafka/docker-compose.yml config`，本地 Kafka Compose 配置校验通过。
- 执行 `docker compose -f platform/kafka/docker-compose.yml up -d`，`spring3-kafka` 进入 healthy 状态，`spring3-kafka-ui` 正常启动。
- 执行 `docker compose -f platform/kafka/docker-compose.yml down`，移除本项目 Kafka/Kafka UI 容器与网络。
- 执行 `docker ps --filter 'name=spring3'`，确认本项目服务未留下运行中的容器；当前 Docker 中仍有其他项目的 `laravel-kafka-1`，未做处理。
- 补齐 Kafka producer/consumer 成功、重复和失败日志中的 `requestId`、`traceId`、topic、partition、offset 和 consumer group 字段。
- 再次执行 `./mvnw -Pkafka -pl order-service -am test -DskipTests`，Kafka profile 编译通过。
- 再次执行 `./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify`，4 个 Kafka IT 全部通过。
- 再次执行 `./mvnw test`，默认 profile 全部通过。
