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
