# 04 RestClient 与 Http Interface 计划

## 目标

在 OpenFeign 之外补充 Spring 原生 HTTP client 示例，用 RestClient 或 `@HttpExchange` 演示服务调用，并沉淀选型对比。

## 任务 Prompt

```text
为当前项目新增 RestClient / @HttpExchange 调用示例。请先阅读 order-service 的 Feign 实现和 docs/task-plans/04-http-clients.md。

要求：
1. 保留现有 OpenFeign 调用。
2. 新增 RestClient 或 @HttpExchange 方式调用 catalog-service。
3. 通过配置切换调用模式，例如 demo.clients.catalog.mode=feign/restclient/http-exchange。
4. 两种调用方式都要复用超时、认证、错误处理和 fallback 设计。
5. 增加测试覆盖正常调用、500 错误、超时或 fallback。
6. 在 docs/USAGE.md 或新文档中说明 OpenFeign、RestClient、WebClient、@HttpExchange 的差异。
```

## 示例内容

- `CatalogClient` 保持 Feign。
- 新增 `CatalogRestClient` 或 `CatalogHttpExchangeClient`。
- `CatalogLookupService` 根据配置选择调用实现。
- 文档中用表格对比声明式调用、响应式能力、生态、测试难度。

## 实施要点

- 不要把三套 client 逻辑写成大量重复代码。
- 错误映射应统一为现有 fallback 行为。
- 超时配置要能在 YAML 中调整。
- 如果加入 `WebClient`，注意它是响应式 client，不要为简单同步调用引入过重模型。

## 验收标准

- `./mvnw test` 通过。
- `demo.clients.catalog.mode=feign` 原有行为不变。
- `demo.clients.catalog.mode=restclient` 或 `http-exchange` 能完成同样业务调用。
- catalog 返回 500 时，order 仍能按预期 fallback。
- 文档包含选型对比和 curl 验证命令。

## 实施记录

- 已新增 `CatalogProductClient` 抽象，默认 `FeignCatalogProductClient` 保持原 OpenFeign 行为。
- 已新增 `RestClientCatalogProductClient`，通过 `demo.clients.catalog.mode=restclient` 条件启用。
- 已把 catalog 调用模式、连接超时和读取超时加入 `demo.clients.catalog.*` 配置。
- 已抽取 `CatalogFallbackSupport`，Feign fallback 与 RestClient fallback 返回同一类降级结果。
- 已在 `CatalogLookupService` 按调用模式选择实现，并把模式加入 Caffeine 缓存 key。
- 已在 admin stats 中输出 `catalogClientMode`，方便运行时确认当前模式。
- 已新增 `OrderRestClientModeTest`，覆盖 RestClient 正常调用、Basic Auth 出站、500 fallback 和读超时 fallback。
- 已在 `docs/USAGE.md` 写入 OpenFeign、RestClient、WebClient、`@HttpExchange` 选型对比和 curl 验证命令。

已验证：

```bash
./mvnw -pl order-service -am test
./mvnw test
./mvnw -Pnacos test
./mvnw package -DskipTests
```

待后续扩展：

- `@HttpExchange` 声明式接口示例可作为后续小任务补充，但当前任务已选择 RestClient 作为 Spring 原生 HTTP client 基线。

## 不做

- 不移除 OpenFeign。
- 不强制全项目切换到响应式栈。
