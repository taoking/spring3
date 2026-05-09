# 11 Sentinel 计划

## 目标

补充阿里系微服务治理专题，演示 Sentinel 的限流、熔断、热点参数能力，并和 Resilience4j 做对比。

## 任务 Prompt

```text
为当前项目补充 Sentinel 可选专题。请先阅读 Resilience4j 配置、docs/interview-roadmap.md 和 docs/task-plans/11-sentinel.md。

要求：
1. 使用独立 sentinel profile，不影响默认 profile。
2. 执行前确认 Spring Boot/Spring Cloud/Spring Cloud Alibaba/Sentinel 版本兼容。
3. 增加 Sentinel Dashboard 或本地可验证配置方式。
4. 选择一个接口演示限流、一个接口演示熔断或热点参数。
5. 文档中明确对比 Sentinel 和 Resilience4j。
6. 增加测试或手工验收步骤。
```

## 示例内容

- `POST /api/orders/preview` 做 QPS 限流。
- `sku` 作为热点参数示例。
- catalog 失败率升高时触发 Sentinel 熔断。

## 实施要点

- Sentinel 适合流量治理和控制台规则管理，Resilience4j 更偏应用内库。
- profile 隔离，避免和现有 Resilience4j 配置互相干扰。
- 规则配置要能本地复现。

## 验收标准

- `./mvnw test` 默认通过。
- sentinel profile 可启动。
- 高频请求能触发限流，并返回清晰错误响应。
- 文档说明 Sentinel 与 Resilience4j 的适用边界。

## 不做

- 不把 Sentinel 设为默认治理方案。
- 不接入生产控制台。

## 实施记录

版本兼容确认：

- 官方 Spring Cloud Alibaba 2025.x 版本说明中，`2025.0.x` 适配 Spring Boot `3.5.x` 和 Spring Cloud `2025.0.x`。
- 组件版本表中，Spring Cloud Alibaba `2025.0.0.0` 对应 Sentinel `1.8.9`、Nacos `3.0.3`。
- 当前项目为 Spring Boot `3.5.14`、Spring Cloud `2025.0.2`、Spring Cloud Alibaba `2025.0.0.0`，Sentinel starter 使用 `com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel`。
- 参考文档：
  - https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/
  - https://sca.aliyun.com/en/docs/2025.x/user-guide/sentinel/quick-start/

已实现：

- `order-service/pom.xml` 新增 Maven `sentinel` profile，只在 `-Psentinel` 时引入 `spring-cloud-starter-alibaba-sentinel`。
- `sentinel` Maven profile 通过 `build-helper-maven-plugin` 加入 `src/sentinel/java` 和 `src/sentinel-test/java`，默认构建不编译 Sentinel 专题源码。
- `application-sentinel.yml` 新增本地规则参数和 Sentinel transport/log 配置。
- 主代码新增 `OrderTrafficGuard` 抽象和默认 `NoopOrderTrafficGuard`，避免默认 profile 依赖 Sentinel。
- `POST /api/orders/preview` 新增 `sentinelFlow`、`sentinelHotSku` 两个显式演示开关。
- `SentinelRuleConfig` 启动时加载：
  - `order-preview-flow`：QPS 限流。
  - `order-preview-hot-sku`：热点参数限流。
  - `order-catalog-degrade-probe`：慢调用比例熔断。
- `SentinelProbeController` 新增 `GET /api/orders/sentinel/degrade-probe?slow=true`，只在 `sentinel` profile 下存在。
- `GlobalExceptionHandler` 将 `SentinelBlockedException` 转换为 `429` ProblemDetail，并返回 `resource`、`strategy`。
- `OrderSentinelProfileTest` 覆盖 Sentinel profile 启动、正常业务路径、QPS 限流、热点参数限流和慢调用熔断探针。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

已验证：

```bash
./mvnw -Psentinel -pl order-service -am -Dtest=OrderSentinelProfileTest#degradeProbeOpensAfterSlowCalls -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -Psentinel -pl order-service -am -Dtest=OrderSentinelProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw test
./mvnw -Pnacos test
./mvnw package -DskipTests
./mvnw -Psentinel -pl order-service -am package -DskipTests
```

验证结果：

- `sentinel` profile 可启动，Sentinel starter 注册 WebMVC 拦截器。
- 默认业务请求不带 Sentinel 演示开关时仍能完成 order -> catalog 调用。
- `sentinelFlow=true` 连续请求可触发 `strategy=FLOW` 的 `429` ProblemDetail。
- `sentinelHotSku=true` 同一 `sku` 连续请求可触发 `strategy=HOT_PARAM` 的 `429` ProblemDetail。
- `/api/orders/sentinel/degrade-probe?slow=true` 连续慢调用可触发 `strategy=DEGRADE` 的 `429` ProblemDetail。
- 默认 profile、`nacos` profile、默认打包和 Sentinel profile 打包均通过。
