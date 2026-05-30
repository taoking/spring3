# 可观测性生产化专题

## 定位

当前项目已经具备 Actuator、Micrometer、Prometheus、Grafana、Zipkin、JSON 日志和 Sentry。资深面试会继续追问：线上接口慢如何定位、哪些指标应该告警、trace 采样怎么选、日志如何检索、Prometheus label 基数为什么危险。

本专题把现有观测基线补成生产化排障 playbook。

## 当前项目观测链路

| 能力 | 当前实现 | 生产化补齐 |
| --- | --- | --- |
| Metrics | Actuator + Prometheus registry | PromQL、告警规则、SLO |
| Dashboard | Grafana dashboard | 按故障场景拆面板 |
| Tracing | Micrometer Tracing + Zipkin | OTel Collector、采样策略、Tempo/Jaeger 选型 |
| Logs | 文本日志 + `json-logging` profile | Loki/ELK 查询样例、字段规范 |
| Error reporting | Sentry starter | traceId/requestId 关联、告警分级 |
| Messaging metrics | RabbitMQ/Kafka 自定义 counter | lag、DLQ/DLT、retry 观测 |

## 三件套排查顺序

```text
Alert fires
  |
  | metrics: 确认影响范围、错误率、延迟、吞吐、资源
  v
logs: 用 requestId/traceId/errorCode 定位具体错误和业务上下文
  |
  v
traces: 找到慢 span、下游依赖、重试、fallback、网关耗时
  |
  v
action: 降级、限流、扩容、回滚、修复数据、重放消息
```

原则：

- 指标用于发现和定界。
- 日志用于解释发生了什么。
- Trace 用于串联调用链和定位慢点。
- Sentry 用于聚合异常和影响面。

## PromQL 查询

### 服务存活

```promql
up{job=~"order-service|catalog-service|gateway-service"}
```

### HTTP 5xx 比例

```promql
sum by (job) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
clamp_min(sum by (job) (rate(http_server_requests_seconds_count[5m])), 0.001)
```

### HTTP p95 延迟

```promql
histogram_quantile(
  0.95,
  sum by (job, le) (rate(http_server_requests_seconds_bucket[5m]))
)
```

### 订单 fallback 比例

```promql
sum(rate(orders_preview_fallback_total[5m]))
/
clamp_min(sum(rate(orders_preview_total[5m])), 0.001)
```

### Resilience4j 熔断状态

```promql
resilience4j_circuitbreaker_state{state="open"}
```

### JVM heap 使用率

```promql
sum by (job) (jvm_memory_used_bytes{area="heap"})
/
sum by (job) (jvm_memory_max_bytes{area="heap"})
```

### Kafka 消费失败增长

```promql
rate(orders_preview_kafka_failed_total[5m])
```

### RabbitMQ 消费失败增长

```promql
rate(orders_preview_rabbitmq_failed_total[5m])
```

## 告警规则

当前仓库已新增 `observability/prometheus/alert-rules.yml`，并在 `observability/prometheus/prometheus.yml` 中通过 `rule_files` 加载。规则草案包括：

- `Spring3ServiceDown`
- `Spring3HighHttpServerErrorRate`
- `Spring3HighHttpP95Latency`
- `Spring3OrderFallbackRatioHigh`
- `Spring3Resilience4jCircuitBreakerOpen`
- `Spring3JvmMemoryPressureHigh`

本地 Prometheus 未接 Alertmanager，规则主要用于学习和面试复盘。生产环境要继续补：

- 告警路由和分组。
- 静默和抑制。
- 值班升级策略。
- runbook 链接。
- 告警恢复条件。

## SLO 草案

| 服务 | 指标 | 目标 |
| --- | --- | --- |
| Gateway | availability | 99.9% |
| Order preview | availability | 99.5% |
| Order preview | p95 latency | < 500ms |
| Catalog lookup | availability | 99.5% |
| Async messaging | DLT/DLQ backlog | 0 持续超过 15 分钟告警 |

错误预算示例：

```text
30 天 99.9% availability 允许约 43.2 分钟不可用。
如果 7 天内错误预算消耗超过 30%，冻结高风险发布，优先修复稳定性。
```

## Label 基数控制

高基数 label 会让 Prometheus 内存和查询成本快速上升。

禁止作为 label：

- `userId`
- `orderId`
- `requestId`
- `traceId`
- 完整 URL query string
- exception message
- SKU 等高变化业务字段

适合作为 label：

- service/job
- endpoint 模板，例如 `/api/orders/preview`
- HTTP method
- status code
- exception class
- resilience4j instance name

面试表达：高基数字段放日志和 trace，不放 metrics label。

## Trace 采样

| 策略 | 优点 | 风险 |
| --- | --- | --- |
| 全量采样 | 本地学习最简单 | 生产成本高 |
| 固定比例采样 | 成本可控 | 低频错误可能采不到 |
| 错误优先采样 | 保留异常链路 | 需要采样器和后端支持 |
| 尾部采样 | 根据结果决定保留 | 依赖 OTel Collector 等中间层 |

建议生产路径：

```text
Application -> OpenTelemetry Collector -> Tempo/Jaeger/Zipkin
```

Collector 负责批量、重试、脱敏、采样和多后端路由。应用不应直接耦合过多后端。

## 日志字段规范

当前 `json-logging` profile 已包含 requestId、traceId、spanId、status、elapsedMs 和认证头脱敏。

建议字段：

| 字段 | 用途 |
| --- | --- |
| `timestamp` | 时间排序 |
| `level` | 严重程度 |
| `application` | 服务名 |
| `traceId` / `spanId` | trace 关联 |
| `requestId` | 用户请求关联 |
| `method` / `path` | HTTP 入口 |
| `status` | 响应状态 |
| `elapsedMs` | 请求耗时 |
| `errorCode` | 稳定业务错误码 |
| `eventId` | MQ 事件关联 |
| `topic` / `queue` | 消息排障 |

不要记录：

- 明文 Authorization。
- token、cookie、password。
- 证件号、手机号等敏感信息。

## 故障排查 Runbook

### 订单预览 p95 突增

1. 看 Gateway 和 order-service p95，确认慢在入口还是下游。
2. 看 `resilience4j_timelimiter_calls_total`、retry、bulkhead 指标。
3. 查 Zipkin trace，确认 catalog span 是否变慢。
4. 查 order-service 日志的 `fallbackUsed` 和 requestId。
5. 如果下游慢，临时降低超时、开启降级或扩容 catalog。

### catalog 500 增加但 order 仍返回 200

1. 看 `catalog_product_simulated_failure_total` 和 HTTP 5xx。
2. 看 order fallback 比例是否上升。
3. 看 Sentry 是否有 catalog 异常聚合。
4. 判断 fallback 是否满足业务语义，必要时告警而不是只看 order 成功率。

### Kafka consumer lag 上涨

1. 看生产速率是否突增。
2. 看 consumer 处理失败和 DLT 指标。
3. 看下游依赖是否变慢。
4. 看 partition skew 和 rebalance。
5. 扩 consumer 前确认 partition 数量和热点 key。

### Gateway 429 增加

1. 看限流配置和命中日志。
2. 区分恶意流量、突发活动和正常增长。
3. 按 IP、用户、接口维度拆分日志。
4. 必要时调整限流阈值或开启更细粒度限流。

## 面试追问与回答要点

| 追问 | 回答要点 |
| --- | --- |
| metrics/logs/traces 分别解决什么？ | metrics 定界，logs 解释，traces 串链路 |
| 接口慢怎么查？ | 先看 p95 和错误率，再看 trace 慢 span，再用日志关联 requestId |
| Prometheus pull 模型有什么优缺点？ | 服务暴露指标，Prometheus 拉取；简单可靠，但短生命周期任务和 NAT 场景要特殊处理 |
| label 基数为什么危险？ | 每个 label 组合都是时间序列，高基数导致内存、存储、查询成本飙升 |
| trace 采样 10% 能排查错误吗？ | 不一定。要配合错误优先或尾部采样，关键错误可以全量保留 |
| Sentry 和日志有什么区别？ | Sentry 聚合异常和影响面，日志保存上下文流水，两者用 traceId/requestId 关联 |
| 为什么只看 HTTP 200 不够？ | fallback 可能掩盖下游失败，要看 fallback ratio 和业务结果 |
| 告警怎么避免噪音？ | 分级、for 持续时间、抑制、静默、runbook、错误预算 |
| JVM 内存高怎么查？ | heap 使用率、GC、对象分布、jmap/JFR，结合发布和流量变化 |
| Kafka lag 告警怎么设计？ | lag 数量、增长速度、持续时间、消费失败和 DLT 结合判断 |

## 自检清单

- 能写出至少 5 条 PromQL。
- 能解释 3 条以上告警规则为什么这样设置。
- 能说明 high cardinality 风险。
- 能说明 trace 采样策略和 OTel Collector 的位置。
- 能用 requestId/traceId 把日志、trace、错误响应关联起来。
- 能对订单接口慢、catalog 错误、Kafka lag、Gateway 429 给出排查路径。
