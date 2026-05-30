# JVM、并发和 Java 21 诊断专题

## 定位

当前项目已有 Java 21 虚拟线程 profile、线程观察接口和 `@Async` 示例。本专题把它扩展成资深 Java 面试需要的 JVM 和并发诊断能力：线程池、虚拟线程、`CompletableFuture`、上下文传播、锁竞争、JFR、jcmd、jstack、jmap、GC log 和线上故障排查路径。

## 当前项目结合点

| 能力 | 当前实现 | 面试价值 |
| --- | --- | --- |
| 默认异步线程池 | `AsyncConfig` 默认 `ThreadPoolTaskExecutor` | 线程池参数、队列、拒绝策略 |
| 虚拟线程 profile | `application-virtual-thread.yml` + `SimpleAsyncTaskExecutor#setVirtualThreads(true)` | Java 21、I/O 阻塞、pinned thread |
| 线程观察接口 | `/api/orders/thread-probe` | 请求线程、`@Async` 线程、虚拟线程标识 |
| 下游调用治理 | Resilience4j TimeLimiter 独立 executor | 业务线程和治理隔离线程池边界 |
| 日志/trace | traceId/spanId、requestId、异步日志 | MDC / trace context 传播追问 |

## 线程模型

| 模型 | 适用 | 风险 |
| --- | --- | --- |
| 平台线程 | CPU 计算、有限并发、传统线程池隔离 | 线程数高时内存和上下文切换成本高 |
| 虚拟线程 | 阻塞 I/O、多并发等待、短生命周期任务 | pinned thread、ThreadLocal 滥用、底层库兼容 |
| Reactor / WebFlux | 非阻塞 I/O、事件循环 | 阻塞调用会拖垮 event loop |
| 专用隔离线程池 | 下游依赖隔离、舱壁保护 | 参数过小会拒绝，过大会放大资源竞争 |

面试回答底线：虚拟线程不是让 CPU 密集任务变快。它主要降低阻塞 I/O 场景下“每请求一线程”的成本。

## 线程池参数

默认 `demoTaskExecutor`：

```java
corePoolSize = 2
maxPoolSize = 4
queueCapacity = 50
threadNamePrefix = "demo-async-"
```

需要能解释：

| 参数 | 作用 | 追问 |
| --- | --- | --- |
| corePoolSize | 常驻核心线程 | 核心线程是否会回收 |
| maxPoolSize | 最大线程数 | 队列满前是否会扩容 |
| queueCapacity | 等待队列 | 无界队列为什么危险 |
| rejection policy | 拒绝策略 | 是丢弃、抛错、调用方执行还是降级 |
| threadNamePrefix | 诊断标识 | 线程 dump 如何定位业务池 |

生产建议：

- 按下游依赖隔离线程池，不把所有异步任务塞到一个池。
- 队列不要无界，拒绝要可观测。
- 线程池指标至少包含 active、pool size、queue size、completed、rejected。
- 对短 I/O 阻塞任务可以评估虚拟线程；对 CPU 密集任务仍要控制并发。

## CompletableFuture 要点

常见追问：

| 问题 | 回答要点 |
| --- | --- |
| `thenApply` 和 `thenCompose` 区别 | 前者转换结果，后者扁平化异步任务 |
| `thenApply` 和 `thenApplyAsync` 区别 | 是否切换 executor |
| 如何处理超时 | `orTimeout`、`completeOnTimeout`、外层 TimeLimiter |
| 如何处理异常 | `exceptionally`、`handle`、`whenComplete` |
| 默认 executor 是什么 | async 方法不指定 executor 时通常用 common pool |

生产建议：

- 显式传入业务 executor，避免误用 common pool。
- 每个外部调用都要有超时。
- 聚合多个异步任务时要考虑局部失败、超时和取消。
- trace/MDC 不会自动跨任意 executor 传播，需要包装或使用框架支持。

## 虚拟线程诊断

启动虚拟线程 profile：

```bash
SPRING_PROFILES_ACTIVE=virtual-thread ./mvnw -pl order-service spring-boot:run
```

观察请求线程：

```bash
curl -u user:user123 'http://localhost:8080/api/orders/thread-probe?delayMs=100'
```

观察 `@Async` 虚拟线程：

```bash
curl -u user:user123 'http://localhost:8080/api/orders/thread-probe?async=true&delayMs=100'
```

Pinned thread 诊断：

```bash
JAVA_TOOL_OPTIONS='-Djdk.tracePinnedThreads=full' \
SPRING_PROFILES_ACTIVE=virtual-thread \
./mvnw -pl order-service spring-boot:run
```

常见 pinned 原因：

- 虚拟线程在 `synchronized` 块里执行长时间阻塞。
- native 方法或部分底层库阻塞。
- 旧版 JDBC、文件、网络库行为不兼容或没有释放 carrier thread。
- 大量 ThreadLocal/MDC 使用导致生命周期和内存不可控。

面试回答底线：发现 pinned 后不是“禁用虚拟线程”，而是定位阻塞点，缩小同步块，替换锁或隔离不兼容库。

## JVM 诊断命令

查找 Java 进程：

```bash
jcmd
```

线程 dump：

```bash
jcmd <pid> Thread.print -l > order-service/target/thread-dump.txt
jstack -l <pid> > order-service/target/jstack.txt
```

JFR 采样：

```bash
jcmd <pid> JFR.start name=spring3 settings=profile duration=60s filename=order-service/target/spring3.jfr
```

堆信息：

```bash
jcmd <pid> GC.heap_info
jmap -histo:live <pid> | head -40
```

GC 日志启动参数：

```bash
JAVA_OPTS='-Xlog:gc*:file=order-service/target/gc.log:time,uptime,level,tags' \
./mvnw -pl order-service spring-boot:run
```

容器内建议：

- 配置 `-XX:MaxRAMPercentage`，不要只依赖默认堆大小。
- OOM 时使用 `-XX:+HeapDumpOnOutOfMemoryError` 和 `-XX:HeapDumpPath=/tmp/heap.hprof`。
- 生产开启 JFR 时控制时长、采样级别和落盘路径。

## 故障排查路径

### CPU 高

1. `top` 或容器指标确认 CPU 高的 Java 进程。
2. `top -H -p <pid>` 找高 CPU 线程。
3. 把线程 ID 转十六进制，在 `jstack` 中找对应 `nid`。
4. 判断是业务循环、JSON 序列化、日志过量、GC 线程还是加密/压缩。
5. 用 JFR 采样确认热点方法。

### 线程阻塞

1. 看接口 p95/p99 和线程池 active/queue。
2. `jcmd <pid> Thread.print -l` 查 `BLOCKED`、`WAITING`、锁持有者。
3. 检查是否下游 HTTP 慢、连接池耗尽或锁粒度过大。
4. 虚拟线程场景开启 `-Djdk.tracePinnedThreads=full`。
5. 对下游依赖加超时、bulkhead 和隔离池。

### 内存上涨

1. 看 heap、non-heap、direct memory、metaspace。
2. `jmap -histo:live` 查看对象数量和大小。
3. 对比两次 histo，判断缓存、集合、队列或 ThreadLocal 是否增长。
4. 查 Caffeine、MQ listener backlog、异步队列和日志缓冲。
5. 必要时导 heap dump，用 MAT/IDE 分析 GC roots。

### 接口超时

1. 先看 Gateway / order / catalog 三层 p95 和 5xx。
2. 用 traceId 找链路，确认慢在网关、业务、Feign/RestClient 还是下游。
3. 看 Resilience4j timeout、bulkhead、circuit breaker 指标。
4. 看线程池队列、HTTP client 连接池、DNS、TLS 和下游错误率。
5. 临时降级、限流或熔断，恢复后复盘根因。

## 面试追问清单

| 追问 | 回答要点 |
| --- | --- |
| 虚拟线程适合什么场景？ | 阻塞 I/O、高并发等待、短任务；不适合 CPU 密集加速 |
| 什么是 pinned thread？ | 虚拟线程阻塞时无法释放 carrier thread，常见于 synchronized/native |
| 线程池队列为什么不能无界？ | 会隐藏压力、增加延迟、最终 OOM |
| `CompletableFuture` 默认线程池是什么？ | common pool，生产建议显式 executor |
| MDC 为什么异步后丢失？ | ThreadLocal 不自动跨线程传播 |
| CPU 高怎么定位？ | top-H、nid、jstack、JFR |
| 内存泄漏怎么定位？ | heap 指标、histo、heap dump、GC roots |
| GC 日志看什么？ | 频率、暂停、回收前后大小、晋升、full GC |
| WebFlux 为什么不能阻塞？ | event loop 被阻塞后少量线程拖垮大量连接 |
| TimeLimiter 和线程池隔离关系？ | TimeLimiter 控制等待时间，隔离池控制并发资源 |

## 验收清单

- 能启动 virtual-thread profile 并观察 `thread.isVirtual()`。
- 能解释默认线程池和虚拟线程 executor 的差异。
- 能说明 pinned thread 的原因和定位方式。
- 能给出 CPU 高、线程阻塞、内存上涨、接口超时四类排查路径。
- 能解释 MDC/trace context 在线程切换中的传播问题。
- 能把线程池、Resilience4j bulkhead、HTTP client timeout 和 MQ 消费并发联系起来。
