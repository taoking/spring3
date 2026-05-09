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

## 实施记录

- `catalog-service` 新增 `contract-test` Maven profile，引入 Spring Cloud Contract Verifier。
- `catalog-service/src/contract-test/resources/contracts/catalog/` 新增三个 provider 契约：商品查询成功、商品不存在、模拟失败。
- `CatalogContractBase` 使用 `MockMvc` 作为 provider 生成测试基类，不需要启动真实 HTTP 端口。
- Spring Cloud Contract Maven Plugin 显式执行 `generateTests`、`convert`、`generateStubs`，生成 provider 验证测试和 `catalog-service-*-stubs.jar`。
- `order-service` 新增 `contract-test` Maven profile，引入 Stub Runner，并用 `OrderCatalogContractStubTest` 加载本地 stubs 验证 consumer 行为。
- `order-service` 的契约 consumer 覆盖正常预览、商品不存在 fallback、catalog `500` fallback。
- 文档已补充运行命令、本地 stubs 使用方式、Spring Cloud Contract 与 MockWebServer 的区别。

## 验证命令

```bash
./mvnw -Pcontract-test -pl catalog-service -am clean install
./mvnw -Pcontract-test -pl order-service -am -Dtest=OrderCatalogContractStubTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw test
```

## 后续可选

- 在 CI 中把 provider 生成的 `stubs` classifier 发布到制品库。
- Consumer CI 使用固定 provider stubs 版本或版本范围做契约回归。
- 增加一个有意破坏字段的演示分支，用于面试时说明 provider breaking change 如何被契约测试拦截。

## 不做

- 不搭建远程契约仓库。
- 不替代所有单元测试。
