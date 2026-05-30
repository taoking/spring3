# 22 可观测性生产化计划

## 目标

在现有 Actuator、Prometheus、Grafana、Zipkin、JSON 日志和 Sentry 基线之上，补齐生产化可观测性专题，覆盖 PromQL、告警规则、SLO、label 基数、trace 采样、日志字段规范和故障排查 runbook。

## 任务 Prompt

```text
深化当前 Prometheus/Grafana/Zipkin/JSON logging/Sentry 基线。请先阅读：

- README.md
- docs/USAGE.md
- docs/IMPLEMENTATION.md
- docs/interview-roadmap.md
- docs/task-plans/19-interview-expansion.md
- docs/observability-production-playbook.md
- observability/docker-compose.yml
- observability/prometheus/prometheus.yml

目标：
1. 增加 PromQL 查询样例和告警规则草案。
2. 补充 SLO、错误预算和告警分级说明。
3. 说明 metrics/logs/traces/Sentry 的职责边界和排查顺序。
4. 补充 label 基数控制、trace sampling 和 OTel Collector 生产化位置。
5. 给出订单接口慢、catalog 失败、Kafka lag、Gateway 429 的 runbook。
6. 保持默认服务启动不依赖观测后端。
7. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或相关 task plan。
8. 记录实施过程到本地日志文件。

验收：
1. 至少包含 5 条 PromQL 查询。
2. 至少包含 3 条告警规则。
3. 文档能解释 high cardinality 风险。
4. 文档能说明 trace 采样策略和 OTel Collector 的价值。
5. `docker compose -f observability/docker-compose.yml config` 通过。
```

## 当前实施结果

- 新增 [可观测性生产化专题](../observability-production-playbook.md)。
- 新增 `observability/prometheus/alert-rules.yml`。
- 更新 `observability/prometheus/prometheus.yml`，通过 `rule_files` 加载告警规则。
- 更新 `observability/docker-compose.yml`，挂载告警规则文件。

## 告警规则

当前规则草案：

- `Spring3ServiceDown`
- `Spring3HighHttpServerErrorRate`
- `Spring3HighHttpP95Latency`
- `Spring3OrderFallbackRatioHigh`
- `Spring3Resilience4jCircuitBreakerOpen`
- `Spring3JvmMemoryPressureHigh`

## 验收标准

- 能说明 metrics/logs/traces/Sentry 各自定位。
- 能写出 HTTP 错误率、p95、fallback ratio、JVM 内存、熔断器状态等 PromQL。
- 能解释 Prometheus label 基数为什么不能放 requestId、traceId、userId。
- 能说明固定比例采样、错误优先采样和尾部采样。
- 能按 runbook 排查订单接口慢、catalog 失败、Kafka lag 和 Gateway 429。
- Compose 配置可解析。

## 验收命令

```bash
docker compose -f observability/docker-compose.yml config
git diff --check
```

## 不做

- 不引入生产 Alertmanager。
- 不强制接入 Loki、Tempo 或 OpenTelemetry Collector 容器。
- 不改变应用默认启动路径。
- 不提交真实告警 webhook 或外部平台 token。
