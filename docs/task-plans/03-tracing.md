# 03 链路追踪计划

## 目标

在现有 Prometheus/Grafana 指标基础上补齐 tracing，让一次 `order-service -> catalog-service` 调用可以在 trace 系统中串起来，并在日志中看到 traceId/spanId。

## 任务 Prompt

```text
为当前项目补充链路追踪专题。请先阅读 README.md、docs/USAGE.md、observability/ 和 docs/task-plans/03-tracing.md。

要求：
1. 引入 Micrometer Tracing，并选择 Zipkin、Tempo 或 OpenTelemetry Collector 作为本地后端。
2. 扩展 observability Docker Compose，新增追踪后端和必要配置。
3. order-service 调用 catalog-service 时能传播 trace context。
4. 日志输出 traceId/spanId，并保留已有 Prometheus 指标。
5. 更新 docs/USAGE.md，写明启动、访问、查询 trace 的命令和 URL。
6. 增加最小测试或手工验收脚本，验证 trace header 传播。
```

## 示例内容

- 一次订单预览请求产生一条完整 trace。
- trace 中至少包含 gateway/order/catalog 或 order/catalog 两段 span。
- 日志格式包含 `traceId`、`spanId`、`application`。

## 实施要点

- 保持 metrics、logs、traces 三者职责清晰。
- 不要为了 tracing 改动业务 DTO。
- Feign/HTTP client 需要自动传播 W3C trace context。
- Grafana 如果接入 Tempo，需要配置 datasource。

## 验收标准

- `./mvnw test` 通过。
- `docker compose -f observability/docker-compose.yml up -d` 能启动新增追踪后端。
- 发起订单预览请求后，可在 trace UI 或 API 查询到完整链路。
- order/catalog 日志中能看到相同 traceId。
- Prometheus 原有 targets 仍为 `up`。

## 不做

- 不接入外部 SaaS APM。
- 不采集敏感请求体。
- 不要求生产级采样策略。
