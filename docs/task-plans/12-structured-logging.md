# 12 结构化日志计划

## 目标

把当前普通日志升级为可检索、可关联、可脱敏的结构化日志示例，支撑线上排障面试题。

## 任务 Prompt

```text
为当前项目补充结构化日志专题。请先阅读现有 LoggingAspect、GlobalExceptionHandler 和 docs/task-plans/12-structured-logging.md。

要求：
1. 新增 json-logging profile，输出 JSON 日志。
2. 增加 requestId/traceId/spanId/application/status/elapsedMs 等字段。
3. 增加请求日志过滤器或拦截器，记录关键元数据。
4. 明确敏感字段脱敏规则，不记录密码、token、Authorization 原文。
5. 更新文档，说明普通日志和 JSON 日志切换方式。
6. 增加测试验证 requestId 生成和敏感头不被原样输出。
```

## 示例内容

- 请求进入时生成 `X-Request-Id`。
- 响应头返回 `X-Request-Id`。
- 错误日志包含 `errorCode` 和 `problemDetail.status`。

## 实施要点

- 优先兼容 tracing，traceId/spanId 如果不存在也不应报错。
- 日志字段名称保持稳定。
- 不在 INFO 日志输出完整请求体。

## 验收标准

- `./mvnw test` 通过。
- 默认 profile 文本日志不受影响。
- json-logging profile 输出合法 JSON。
- Authorization、password、token 等敏感值不会原样出现。
- 文档包含启动和查看日志命令。

## 不做

- 不接入 Elasticsearch/Loki 生产集群。
- 不记录大体积请求体。
