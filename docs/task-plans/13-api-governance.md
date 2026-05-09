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

## 实施记录

已实现：

- 已在 `common` 增加 `ApiErrorCodes` 和 `ApiHeaders`，统一维护稳定错误码和通用治理响应头。
- `catalog-service` 和 `order-service` 的 `ProblemDetail` 已补充 `errorCode`、`requestId`、`timestamp`、`path`。
- 错误响应会优先复用入站 `X-Request-Id`，没有则生成 UUID，并把最终 requestId 写回响应头。
- `order-service` 已新增 `/api/v1/orders/preview` 和 `/api/v2/orders/preview`。
- v1 复用原订单预览响应结构，v2 演示轻量结构变化：`apiVersion`、`data`、`links`。
- 旧 `/api/orders/preview` 保持可用，但返回 `Deprecation=true`、`Sunset=Thu, 31 Dec 2026 23:59:59 GMT`、successor `Link` 和废弃原因。
- `order-service` 已新增 `orders-v1`、`orders-v2`、`orders-ops` OpenAPI 分组。
- `catalog-service` 已新增 `catalog-public`、`catalog-admin` OpenAPI 分组。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

当前错误码：

| 错误码 | 场景 |
| --- | --- |
| `ORDER_VALIDATION_FAILED` | 订单请求参数校验失败 |
| `ORDER_SENTINEL_BLOCKED` | Sentinel 流控或熔断拦截 |
| `CATALOG_PRODUCT_NOT_FOUND` | 商品不存在 |
| `CATALOG_VALIDATION_FAILED` | Catalog 请求参数校验失败 |
| `CATALOG_SIMULATED_FAILURE` | Catalog 演示异常 |
| `SECURITY_ACCESS_DENIED` | 已认证但权限不足 |
| `SYSTEM_INTERNAL_ERROR` | 未预期服务端异常 |

已验证：

```bash
./mvnw -pl catalog-service,order-service -am -Dtest=CatalogControllerTest,OrderControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw test
./mvnw -Pnacos test
./mvnw -Psentinel -pl order-service -am -Dtest=OrderSentinelProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw package -DskipTests
./mvnw -Psentinel -pl order-service -am package -DskipTests
```

验证结果：

- `CatalogControllerTest` 覆盖 `CATALOG_PRODUCT_NOT_FOUND`、`requestId` 回写、`timestamp` 和 Catalog OpenAPI 分组。
- `OrderControllerTest` 覆盖 `ORDER_VALIDATION_FAILED`、`requestId` 回写、旧接口废弃头、v1/v2 版本路由和订单 OpenAPI 分组。
