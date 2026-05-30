# Redis 与缓存治理专题

## 定位

当前项目默认不接 Redis，只使用 Caffeine 演示本地缓存。资深 Java 面试中，缓存治理常从“为什么不用 Redis”“本地缓存多实例问题”“缓存和数据库一致性”“分布式锁是否可靠”“热点 key 如何处理”继续追问。

本专题补齐 Redis 与缓存治理的设计能力，不改变默认运行路径，不新增 Redis 依赖。

## 当前项目结合点

| 现有能力 | 相关文件 | 当前边界 |
| --- | --- | --- |
| 本地缓存 | `CatalogLookupService` 的 `@Cacheable` | 只在单 JVM 内生效，多实例不共享 |
| 缓存配置 | `order-service/src/main/resources/application.yml` | 使用 Caffeine，适合演示读路径优化 |
| 下游调用 | OpenFeign / RestClient | 缓存用于减少 catalog-service 查询 |
| Gateway 本地限流 | `LocalRateLimitGlobalFilter` | 单实例内存计数，生产分布式限流需要共享存储 |
| Kafka/RabbitMQ 幂等 | 内存 eventId store | Redis 可作为轻量去重存储，但当前项目不接入 |

当前 Caffeine 示例适合讲清本地缓存、TTL、最大容量和 fallback 不缓存；它不能解决多实例缓存一致性、分布式锁、跨实例限流和消息幂等持久化。

## Caffeine 与 Redis 对比

| 维度 | Caffeine | Redis |
| --- | --- | --- |
| 部署位置 | 应用进程内 | 独立服务/集群 |
| 延迟 | 极低，无网络开销 | 低，但有网络开销 |
| 数据共享 | 单 JVM | 多实例共享 |
| 容量 | 受应用内存限制 | 独立内存，可扩展 |
| 一致性 | 每个实例独立 | 中心化更容易控制 |
| 故障影响 | 应用重启缓存丢失 | Redis 故障影响所有依赖方 |
| 适合 | 热点小数据、本地加速、降级兜底 | 分布式缓存、限流、锁、会话、排行榜、轻量幂等 |

资深回答要点：Caffeine 和 Redis 不是替代关系。高性能读路径常见组合是本地缓存 + Redis + DB/下游服务的多级缓存，但复杂度和一致性风险也会增加。

## 典型缓存模式

### Cache Aside

最常见模式：

```text
read:
  cache hit  -> return
  cache miss -> query source -> set cache -> return

write:
  update source
  delete cache
```

关键点：

- 写后通常删除缓存，而不是直接更新缓存。
- 删除失败要重试或异步补偿。
- 对强一致要求高的业务，不应只靠缓存保证正确。

### Read Through / Write Through

缓存组件封装数据源访问，应用只访问缓存层。适合缓存平台能力强的场景，但业务透明度较低。

### Write Behind

先写缓存，异步刷新数据源。吞吐高，但一致性风险大，只适合能接受数据延迟和丢失补偿的场景。

## 缓存问题矩阵

| 问题 | 表现 | 常见处理 | 风险 |
| --- | --- | --- | --- |
| 缓存穿透 | 不存在的数据频繁打到下游 | 空值缓存、布隆过滤器、参数校验 | 空值 TTL 过长会影响新数据可见性 |
| 缓存击穿 | 热点 key 过期瞬间大量请求回源 | 互斥锁、single flight、逻辑过期、预热 | 锁实现错误会放大延迟 |
| 缓存雪崩 | 大量 key 同时过期或 Redis 故障 | TTL 抖动、多级缓存、限流降级、熔断 | 降级策略必须提前设计 |
| 热点 key | 单 key QPS 极高 | 本地缓存、拆 key、读副本、限流 | 热点迁移可能造成新热点 |
| 大 key | value 过大或集合过大 | 拆分、分页、压缩、异步删除 | 删除大 key 可能阻塞 |
| 缓存污染 | 冷数据挤出热数据 | 合理 TTL、最大容量、访问频率策略 | 过度缓存浪费内存 |
| 缓存不一致 | 读到旧值 | 删除缓存、版本号、消息失效、短 TTL | 强一致不能只靠缓存 |

## 缓存一致性

### 常见写路径

```text
update database/source
delete cache
```

为什么不是先删缓存再更新数据库？

```text
T1 delete cache
T2 read cache miss -> read old DB -> set old cache
T1 update DB
```

结果：缓存里可能长期保留旧值。

### 延迟双删

```text
update database/source
delete cache
sleep small delay
delete cache again
```

作用：降低并发读把旧值写回缓存的概率。

边界：

- 不是强一致。
- 延迟时间难选。
- 第二次删除失败仍需要重试或补偿。

### binlog / CDC 失效

由数据变更事件驱动缓存删除：

```text
DB change -> binlog/CDC -> cache invalidation consumer -> delete Redis/local cache
```

优点：业务代码侵入少，适合多服务共享缓存失效。

风险：消费延迟、消息丢失、顺序问题、重放幂等。

## 多级缓存设计

适合商品详情这类高频读场景：

```text
request
  |
  | local Caffeine hit
  v
return

local miss
  |
  | Redis hit
  | put local cache
  v
return

Redis miss
  |
  | query catalog-service / DB
  | set Redis with TTL jitter
  | set local cache
  v
return
```

治理点：

- 本地缓存 TTL 应短于 Redis TTL。
- 本地缓存失效可由消息广播或短 TTL 兜底。
- Redis 故障时，本地缓存可作为降级读能力，但要限制陈旧数据范围。
- 不同实例本地缓存不一致是常态，要看业务能否接受。

## 分布式锁

### 基础命令

```text
SET lock:order:123 random-token NX PX 30000
```

释放锁必须校验 token，避免误删别人的锁：

```lua
if redis.call("get", KEYS[1]) == ARGV[1] then
  return redis.call("del", KEYS[1])
else
  return 0
end
```

### 常见风险

| 风险 | 说明 | 处理 |
| --- | --- | --- |
| 锁过期但业务未完成 | 其他线程拿到锁并发执行 | 合理 TTL、续期、业务幂等 |
| 删除别人锁 | 过期后新 owner 已获得锁 | token + Lua 原子校验删除 |
| Redis 主从切换 | 锁写入未同步即故障 | 接受风险或使用更强一致协调组件 |
| 客户端长 GC | 持锁线程暂停，锁已过期 | fencing token、业务版本校验 |
| 锁粒度过大 | 降低并发 | 按业务 key 缩小粒度 |

面试重点：分布式锁不是一致性的最终答案。真正的防重复仍应依赖数据库唯一键、状态机版本或业务幂等。

## Redis 分布式限流

当前 Gateway 是单实例本地限流。生产多实例常用 Redis 做共享计数。

| 算法 | 特点 | 适用 |
| --- | --- | --- |
| 固定窗口 | 简单，边界突刺明显 | 低成本粗粒度限流 |
| 滑动窗口 | 更平滑，存储更多请求记录 | 用户/API 精细限流 |
| 令牌桶 | 允许短暂突发 | 网关入口限流 |
| 漏桶 | 平滑出流量 | 下游保护 |

设计要点：

- key 维度：IP、用户、租户、接口、应用。
- TTL 必须和窗口一致，防止 key 泄漏。
- Lua 保证计数和过期设置原子性。
- Redis 故障时要决定 fail-open 还是 fail-closed。
- 限流命中要有指标和结构化日志。

## Redis 作为幂等存储

Redis 可用于短期幂等：

```text
SET idem:order:{idempotencyKey} PROCESSING NX PX 600000
```

适合：

- 短时间重复提交控制。
- MQ 消费短期去重。
- 防止同一任务重复调度。

不适合：

- 财务级永久幂等。
- 需要审计和对账的长期幂等。
- Redis 淘汰策略可能删除幂等记录的场景。

生产建议：重要业务使用数据库唯一约束作为最终防线，Redis 只做前置削峰。

## 当前项目如果生产化接入 Redis

建议接入边界：

| 场景 | Redis 用途 | 仍需注意 |
| --- | --- | --- |
| 商品查询 | catalog 产品二级缓存 | 价格/库存强一致不能只靠缓存 |
| Gateway 限流 | 多实例共享计数 | Redis 故障策略、限流指标 |
| MQ 幂等 | 短期 eventId 去重 | 重要副作用仍要持久化 inbox |
| OAuth2 opaque token | token introspection 缓存 | token 吊销延迟 |
| 分布式锁 | 防重复任务执行 | 业务仍需幂等 |

本项目现阶段不接 Redis，是为了保持学习路径聚焦和默认启动轻量。Redis 生产化可以作为独立 `redis` profile 或独立专题推进。

## 运维与排障

| 问题 | 排查方向 |
| --- | --- |
| Redis 慢 | `SLOWLOG`、大 key、网络延迟、CPU、持久化 fork |
| 内存上涨 | key 数量、TTL 缺失、大 key、淘汰策略 |
| 命中率下降 | TTL 过短、key 设计变化、缓存污染、热点迁移 |
| QPS 高 | 热点 key、本地缓存、读写分离、限流 |
| 连接数高 | 连接池配置、客户端泄漏、短连接 |
| 主从延迟 | 写入压力、网络、慢命令、大 key |

关键指标：

- `connected_clients`
- `used_memory`
- `evicted_keys`
- `expired_keys`
- `keyspace_hits` / `keyspace_misses`
- `instantaneous_ops_per_sec`
- `blocked_clients`
- `master_repl_offset` / replica lag

## 面试追问与回答要点

| 追问 | 回答要点 |
| --- | --- |
| Caffeine 和 Redis 怎么选？ | Caffeine 是进程内低延迟，本地加速；Redis 是跨实例共享，适合分布式缓存/锁/限流 |
| 多实例下本地缓存有什么问题？ | 每个实例数据独立，更新不一致，重启丢失；用短 TTL、消息失效或 Redis 二级缓存缓解 |
| 缓存穿透怎么解决？ | 参数校验、空值缓存、布隆过滤器；注意空值 TTL 和误判 |
| 缓存击穿怎么解决？ | single flight、互斥锁、逻辑过期、预热；热点 key 要配合本地缓存 |
| 缓存雪崩怎么解决？ | TTL 抖动、分批预热、多级缓存、限流、熔断、降级 |
| 数据库和缓存不一致怎么办？ | Cache Aside 删除缓存、重试、延迟双删、CDC 失效、短 TTL；强一致不要只靠缓存 |
| 为什么写后删除缓存，不是更新缓存？ | 删除更简单，避免并发下写入旧值；更新缓存容易遗漏字段和并发覆盖 |
| 分布式锁可靠吗？ | 只能降低并发执行概率，必须 token + Lua + TTL + 幂等；强一致依赖业务唯一约束 |
| Redisson watchdog 解决什么？ | 自动续期避免业务未完成锁过期；但长 GC、网络分区和业务幂等仍要考虑 |
| hot key 怎么治理？ | 识别热点、本地缓存、拆 key、读副本、限流、预热 |
| big key 有什么危害？ | 内存不均、网络大包、阻塞删除、复制延迟；需要拆分和异步删除 |
| Redis 限流故障时 fail-open 还是 fail-closed？ | 看业务风险。核心交易偏 fail-closed 或降级，普通读流量可 fail-open 并告警 |
| Redis 能做 MQ 幂等吗？ | 可以做短期去重，但财务级长期幂等应使用数据库唯一约束/inbox |
| 布隆过滤器误判怎么办？ | 误判会把不存在当可能存在，不会把存在当不存在；仍需回源确认 |
| Redis Cluster 扩容有什么风险？ | slot 迁移、热点 key、客户端重定向、pipeline 和 Lua 跨 slot 限制 |

## 自检清单

- 能说明当前项目为什么只用 Caffeine。
- 能说明本地缓存多实例不一致问题。
- 能画出 Caffeine + Redis + 下游服务的多级缓存链路。
- 能解释缓存穿透、击穿、雪崩的差异。
- 能说明 Cache Aside 的并发不一致窗口。
- 能说明 Redis 分布式锁的失效场景和 fencing token。
- 能说明 Redis 限流的 key 设计和故障策略。
- 能说明 Redis 幂等只适合短期去重，不能替代长期审计。
