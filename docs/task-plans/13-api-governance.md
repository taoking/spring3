# 13 API 治理计划

## 目标

补充接口长期演进能力，覆盖统一错误码、API versioning、OpenAPI 分组、兼容性和废弃策略。

## 任务 Prompt

```text
为当前项目补充 API 治理专题。请先阅读 Controller、GlobalExceptionHandler、OpenApiConfig 和 docs/task-plans/13-api-governance.md。

要求：
1. 在 ProblemDetail 基础上增加稳定 errorCode、requestId、timestamp。
2. 增加 API versioning 示例，例如 /api/v1/orders 和 /api/v2/orders。
3. OpenAPI 按服务或版本分组。
4. 增加接口废弃示例，返回 Deprecation 或 Sunset 相关响应头。
5. 更新测试，覆盖错误码、版本路由和 OpenAPI 分组。
6. 更新文档，说明兼容策略和错误码规范。
```

## 示例内容

- `ORDER_VALIDATION_FAILED`
- `CATALOG_PRODUCT_NOT_FOUND`
- `CATALOG_FALLBACK_USED`
- `/api/v1/orders/preview` 与 `/api/v2/orders/preview`

## 实施要点

- 错误码要稳定，不随错误文案变化。
- v2 示例可以只做轻量差异，不需要复制大量业务逻辑。
- OpenAPI 分组要便于阅读，而不是制造重复。

## 验收标准

- `./mvnw test` 通过。
- 错误响应包含稳定 errorCode。
- v1/v2 接口都可访问，且文档中说明差异。
- Swagger UI 可查看分组。
- 废弃接口返回明确提示。

## 不做

- 不做复杂 API 网关管理平台。
- 不引入数据库存储错误码。
