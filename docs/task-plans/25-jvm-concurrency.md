# 25 JVM、并发和 Java 21 诊断计划

## 目标

在当前 `virtual-thread` profile 基线上，补齐资深 Java 面试常问的 JVM、线程池、虚拟线程、`CompletableFuture`、MDC/trace context、JFR、jcmd、jstack、jmap 和 GC 排障能力。

## 任务 Prompt

```text
基于当前 Spring Boot 3 学习项目，补充 JVM、并发和 Java 21 诊断专题。请先阅读：

- docs/task-plans/10-virtual-threads.md
- docs/USAGE.md
- order-service/src/main/java/com/taoking/spring3/order/config/AsyncConfig.java
- order-service/src/main/java/com/taoking/spring3/order/service/ThreadProbeService.java
- order-service/src/main/resources/application-virtual-thread.yml

目标：
1. 基于现有 virtual-thread profile，说明平台线程、虚拟线程、WebFlux event loop 和隔离线程池的边界。
2. 补充线程池参数、队列、拒绝策略和隔离池设计。
3. 补充 CompletableFuture 编排、异常处理、超时和 executor 选择。
4. 补充 MDC/trace context 在线程切换中的传播问题。
5. 补充 pinned thread 诊断方式。
6. 补充 JFR、jcmd、jstack、jmap、GC log 的常用命令。
7. 输出 CPU 高、线程阻塞、内存上涨、接口超时四类排查路径。
8. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或 task plan 索引。

验收：
1. 文档能支撑资深面试追问，不只介绍虚拟线程概念。
2. 至少给出 4 类故障排查路径。
3. 至少给出 5 条 JVM 诊断命令。
4. 不改变默认 profile 行为。
```

## 当前实施结果

- 新增 [JVM、并发和 Java 21 诊断专题](../jvm-concurrency-playbook.md)。
- 基于现有 `virtual-thread` profile 说明平台线程、虚拟线程、WebFlux event loop 和隔离线程池边界。
- 补充线程池参数、`CompletableFuture`、MDC/trace context、pinned thread、JFR、jcmd、jstack、jmap、GC log 和四类故障排查路径。

## 验收命令

```bash
./mvnw -pl order-service -am -Dtest=OrderVirtualThreadProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

## 不做

- 不引入复杂压测平台。
- 不把默认 profile 强制切换为虚拟线程。
- 不新增真实 CPU 或内存压测接口，避免误用。
