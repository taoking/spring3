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

## 实施记录

已实现：

- 已新增三个服务的 `application-json-logging.yml`，使用 Spring Boot 3.5 内建 `logging.structured.format.console=logstash` 输出 JSON 日志。
- 已在 `demo-observability-autoconfigure` 中新增 `DemoHttpRequestLoggingAutoConfiguration`、`DemoHttpRequestLoggingFilter` 和 `DemoHttpRequestLoggingProperties`。
- Servlet 服务在 `demo.observability.http-logging.enabled=true` 时生成或透传 `X-Request-Id`，写入响应头和 MDC。
- 请求日志通过 SLF4J fluent key-value 记录 `event`、`method`、`path`、`status`、`elapsedMs`、`authScheme`、`traceAvailable` 等字段。
- JSON profile 通过 `logging.structured.json.context.include=true` 输出 MDC 中的 `requestId`、`traceId`、`spanId`。
- 已调整 Gateway 审计日志为 key-value 结构化字段，便于 `json-logging` profile 下检索。
- 已新增 `OrderJsonLoggingProfileTest`，覆盖 JSON profile 启动、请求 ID 生成、请求日志 JSON 解析、关键字段和敏感认证头不原样输出。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

脱敏规则：

- 不记录请求体。
- 不记录 query string，避免 password、token、secret 等参数落日志。
- 不记录 `Authorization` 原文，只记录 `authScheme=Basic/Bearer/present`。
- 测试验证日志中不包含 `Authorization` 原文和 Basic 密码。

已验证：

```bash
./mvnw -pl order-service -am -Dtest=OrderJsonLoggingProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw test
./mvnw -Pnacos test
./mvnw -Psentinel -pl order-service -am -Dtest=OrderSentinelProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw package -DskipTests
./mvnw -Psentinel -pl order-service -am package -DskipTests
```

验证结果：

- `json-logging` profile 输出的请求日志为合法 JSON。
- 响应头包含自动生成的 `X-Request-Id`。
- 请求日志包含 `application=order-service`、`requestId`、`status=200`、`elapsedMs` 和 `authScheme=Basic`。
- 日志输出不包含 `Authorization` 原文和 `user123`。
