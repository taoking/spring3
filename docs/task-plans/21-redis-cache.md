# 21 Redis 与缓存治理计划

## 目标

在当前只使用 Caffeine、不接 Redis 的项目边界下，补齐 Redis 与缓存治理的资深面试专题，覆盖缓存模式、一致性、穿透/击穿/雪崩、分布式锁、热点 key、分布式限流和 Redis 作为短期幂等存储的边界。

本专题是设计型专题，不改变默认运行路径，不新增 Redis 依赖。

## 任务 Prompt

```text
为当前项目补充 Redis 与缓存治理专题。项目默认仍不接 Redis。请先阅读：

- README.md
- docs/IMPLEMENTATION.md
- docs/USAGE.md
- docs/interview-roadmap.md
- docs/task-plans/19-interview-expansion.md
- docs/redis-cache-playbook.md
- order-service 的 CatalogLookupService 和缓存配置
- gateway-service 的 LocalRateLimitGlobalFilter

目标：
1. 解释当前项目 Caffeine 本地缓存的适用边界和多实例问题。
2. 补充 Redis 在分布式缓存、限流、锁、短期幂等中的生产使用方式。
3. 覆盖缓存穿透、击穿、雪崩、热点 key、大 key、缓存污染和缓存一致性。
4. 说明 Cache Aside、延迟双删、CDC 缓存失效和多级缓存的取舍。
5. 说明 Redis 分布式锁的正确释放方式、续期风险、fencing token 和业务幂等边界。
6. 保持默认 profile 轻量，不引入 Redis 运行依赖。
7. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或相关 task plan。
8. 记录实施过程到本地日志文件。

验收：
1. 文档有当前项目结合点，不是泛泛 Redis 笔记。
2. 文档能说明 Caffeine 与 Redis 的差异。
3. 文档覆盖缓存穿透、击穿、雪崩、大 key、hot key、分布式锁和分布式限流。
4. 文档明确 Redis 幂等只适合短期去重，不能替代数据库唯一约束或 inbox。
5. 如没有代码变更，至少运行 `git diff --check`。
```

## 当前实施结果

已新增 [Redis 与缓存治理专题](../redis-cache-playbook.md)，覆盖：

- 当前项目 Caffeine、本地限流、消息幂等与 Redis 的结合点。
- Caffeine 与 Redis 的定位对比。
- Cache Aside、Read Through、Write Through、Write Behind。
- 缓存穿透、击穿、雪崩、热点 key、大 key、缓存污染和不一致。
- 延迟双删、CDC 失效和多级缓存。
- Redis 分布式锁、Lua 释放、watchdog、fencing token 和失效场景。
- Redis 分布式限流算法和故障策略。
- Redis 作为短期幂等存储的边界。
- Redis 运维指标和排障方向。
- 15 个资深面试追问和回答要点。

## 场景清单

| 场景 | 当前项目 | Redis 生产化方案 |
| --- | --- | --- |
| 商品详情缓存 | Caffeine `@Cacheable` | Caffeine + Redis 二级缓存 |
| 商品不存在频繁查询 | 当前回源 catalog | 空值缓存、布隆过滤器 |
| 热点 SKU 过期 | 当前单 JVM 回源 | single flight、逻辑过期、本地缓存 |
| 多实例限流 | 当前 Gateway 本地内存限流 | Redis Lua 共享计数 |
| MQ 短期去重 | 当前内存 eventId store | Redis `SET NX PX` 短期去重 |
| 防重复任务 | 当前无分布式任务锁 | Redis 锁 + fencing token + 业务幂等 |
| 缓存一致性 | 当前无跨实例失效 | 删除缓存、延迟双删、CDC 失效 |

## 验收标准

- 能说明为什么当前项目不直接接 Redis。
- 能说明 Caffeine、本地缓存、Redis、二级缓存的取舍。
- 能解释缓存穿透、击穿、雪崩的区别和处理方式。
- 能解释 Cache Aside 并发不一致窗口。
- 能解释延迟双删和 CDC 缓存失效的边界。
- 能写出 Redis 分布式锁的 `SET NX PX` 和 Lua 删除思路。
- 能说明分布式锁为什么不能替代业务幂等。
- 能说明 Redis 限流的 key 维度、TTL、Lua 原子性和 fail-open/fail-closed。
- 能回答至少 10 个 Redis 与缓存治理追问。

## 验收命令

```bash
git diff --check
```

如果后续加入代码或配置，再按变更范围补充：

```bash
./mvnw test
```

## 不做

- 不引入 Redis 依赖。
- 不新增 Redis Docker Compose。
- 不实现 Spring Cache Redis manager。
- 不实现 Redisson 或分布式锁代码。
- 不把 Redis 作为生产级永久幂等存储。
