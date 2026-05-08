# 14 Spring Cloud Contract 计划

## 目标

补充 provider/consumer 契约测试，降低 `catalog-service` 和 `order-service` 间接口变更风险。

## 任务 Prompt

```text
为当前项目补充 Spring Cloud Contract 契约测试。请先阅读 catalog-service Controller、order-service Feign 测试和 docs/task-plans/14-contract-testing.md。

要求：
1. catalog-service 作为 provider，定义商品查询接口契约。
2. order-service 作为 consumer，使用生成的 stub 或契约验证调用。
3. 覆盖成功查询、商品不存在、模拟失败至少三类契约。
4. 契约测试不要依赖真实外部服务。
5. 更新 docs/USAGE.md，说明如何运行契约测试。
6. 文档说明契约测试和 MockWebServer 测试的区别。
```

## 示例内容

- `GET /api/catalog/products/SKU-1001` 返回 `ProductResponse`。
- `GET /api/catalog/products/UNKNOWN` 返回 `404 ProblemDetail`。
- `fail=true` 返回模拟失败。

## 实施要点

- provider 契约应该表达 HTTP API，不绑定 Java 实现细节。
- consumer 测试验证客户端能处理契约定义的响应。
- 避免生成物提交过多，按项目规范处理。

## 验收标准

- `./mvnw test` 通过。
- 契约测试命令可单独运行。
- provider 破坏响应字段时契约测试会失败。
- 文档说明契约发布或本地 stub 使用方式。

## 不做

- 不搭建远程契约仓库。
- 不替代所有单元测试。
