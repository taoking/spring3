# 07 Resilience4j 深化计划

## 目标

把当前熔断 fallback 扩展为完整的服务治理示例，覆盖 Retry、RateLimiter、Bulkhead、TimeLimiter、CircuitBreaker 及其指标。

## 任务 Prompt

```text
深化当前 Resilience4j 示例。请先阅读 order-service 中 Feign、CatalogLookupService、application.yml 和 docs/task-plans/07-resilience4j.md。

要求：
1. 保留现有 CircuitBreaker + fallback。
2. 新增 Retry、RateLimiter、Bulkhead、TimeLimiter 示例。
3. 提供可触发 slow、failure、rate limit、bulkhead full 的接口或参数。
4. 将配置集中在 application.yml，并补充注释或文档说明。
5. 暴露并验证 Resilience4j Micrometer 指标。
6. 增加测试覆盖至少三类治理策略。
```

## 示例内容

- `?slowCatalog=true` 触发 TimeLimiter。
- `?failCatalog=true` 触发 Retry 和 CircuitBreaker。
- 短时间高频请求触发 RateLimiter。
- 并发请求超过阈值触发 Bulkhead。

## 实施要点

- Retry 不应盲目重试非幂等请求。
- TimeLimiter、Feign timeout、Tomcat timeout 的边界要说明清楚。
- Bulkhead 类型和线程池大小要适合本地演示。
- fallback 响应要让调用方知道使用了降级。

## 验收标准

- `./mvnw test` 通过。
- 每种治理策略都有一个可复现触发方式。
- Prometheus 能查询到对应 Resilience4j 指标。
- 文档说明每种策略适合解决什么问题、不适合解决什么问题。
- 原有订单预览正常路径不受影响。

## 不做

- 不接入生产流量控制平台。
- 不把所有异常都吞掉后返回成功。

## 实施记录

- 已新增 `CatalogGovernanceService`，在 `OrderService` 和 `CatalogLookupService` 之间承载治理策略。
- 已保留现有 Feign / RestClient fallback，并统一复用 `CatalogFallbackSupport` 返回明确降级商品。
- 已新增 Retry + CircuitBreaker：`failCatalog=true` 会触发下游失败、重试和熔断指标。
- 已新增 TimeLimiter：`slowCatalog=true` 走异步治理路径，`timeout-duration` 小于 Feign read timeout，优先演示 Resilience4j 超时。
- 已新增 RateLimiter：`rateLimit=true` 连续调用可触发限流 fallback。
- 已新增 Bulkhead：`bulkhead=true&holdBulkhead=true` 并发调用可触发 bulkhead full fallback。
- 已新增 `demo.resilience.catalog.async-pool-size` 与 `demo.resilience.catalog.bulkhead-hold-duration` 配置。
- 已新增 `OrderResilience4jTest`，覆盖 Retry、CircuitBreaker、TimeLimiter、RateLimiter、Bulkhead，并验证 Prometheus 指标名称。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

已验证：

```bash
./mvnw -pl order-service -am -Dtest=OrderResilience4jTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -pl order-service -am test
./mvnw test
./mvnw -Pnacos test
./mvnw package -DskipTests
```
