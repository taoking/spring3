# 10 Java 21 虚拟线程计划

## 目标

增加虚拟线程专题，演示 Spring Boot 3 在 Java 21 下启用虚拟线程后的行为，并和当前线程池配置做对比。

## 任务 Prompt

```text
为当前项目补充 Java 21 虚拟线程示例。请先阅读 order-service 的 AsyncConfig、DemoHeartbeatJob 和 docs/task-plans/10-virtual-threads.md。

要求：
1. 新增 virtual-thread profile。
2. 使用 spring.threads.virtual.enabled 或显式 TaskExecutor 配置演示虚拟线程。
3. 保留默认 profile 的传统线程池配置。
4. 增加一个 I/O 等待型示例接口，用于观察虚拟线程和平台线程差异。
5. 更新文档，说明适用场景、限制和排查方式。
6. 增加测试，确保 profile 切换不影响业务行为。
```

## 示例内容

- `SPRING_PROFILES_ACTIVE=virtual-thread ./mvnw -pl order-service spring-boot:run`
- `/api/orders/preview?slowCatalog=true`
- 日志输出当前线程名，便于观察虚拟线程。

## 实施要点

- 虚拟线程适合阻塞 I/O 场景，不是 CPU 密集型加速器。
- 注意 MDC、ThreadLocal、线程池隔离策略的影响。
- 不要为了演示删除现有 `@Async` 配置。

## 验收标准

- `./mvnw test` 通过。
- 默认 profile 行为不变。
- virtual-thread profile 可启动并处理请求。
- 文档包含启动命令、观察点、适用场景和限制。

## 不做

- 不做复杂压测平台。
- 不把所有线程池配置永久替换为虚拟线程。

## 实施记录

- 已新增 `order-service/src/main/resources/application-virtual-thread.yml`，设置 `spring.threads.virtual.enabled=true`。
- 已调整 `AsyncConfig`：默认 profile 继续使用 `ThreadPoolTaskExecutor`，`virtual-thread` profile 使用虚拟线程 `SimpleAsyncTaskExecutor`，线程名前缀为 `demo-vt-`。
- 已新增 `ThreadProbeService` 和 `/api/orders/thread-probe`，支持请求线程和 `@Async` 线程两种 I/O 等待观察模式。
- 已增强 `NotificationService` 和 `OrderPreviewEventListener` 日志，输出当前线程名和 `virtual` 标识。
- 已新增 `OrderVirtualThreadProfileTest`，覆盖 `virtual-thread` profile 下订单预览正常路径、请求线程探针和异步虚拟线程探针。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

已验证：

```bash
./mvnw -pl order-service -am -Dtest=OrderVirtualThreadProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw test
./mvnw -Pnacos test
./mvnw package -DskipTests
```

验证结果：

- `virtual-thread` profile 能加载并启动 `order-service` 测试上下文。
- 订单预览正常返回，MockWebServer 收到 catalog 请求。
- `/api/orders/thread-probe?delayMs=1` 返回请求线程元信息。
- `/api/orders/thread-probe?async=true&delayMs=1` 返回 `threadName=demo-vt-*` 且 `virtual=true`。
- 默认 profile、`nacos` profile 和跳过测试打包均通过，profile 切换未影响现有业务测试。
