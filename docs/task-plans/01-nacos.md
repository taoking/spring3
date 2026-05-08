# 01 Nacos 实装计划

## 目标

把 Nacos 从“文档专题”推进到“可选运行专题”：在 `nacos` profile 下启用服务注册发现和配置中心，默认 profile 继续不依赖 Nacos。

## 任务 Prompt

```text
基于当前 Spring Boot 3 多模块项目实现 Nacos 可选 profile。请先阅读 README.md、docs/USAGE.md、docs/nacos-playbook.md 和 docs/task-plans/01-nacos.md。

要求：
1. 默认 profile 下不依赖 Nacos，./mvnw test 必须继续通过。
2. 增加 Spring Cloud Alibaba Nacos dependency management 和 nacos Maven/profile 配置，执行前确认与当前 Spring Boot/Spring Cloud 版本兼容。
3. catalog-service 和 order-service 在 nacos profile 下注册到 Nacos。
4. order-service 在 nacos profile 下通过服务名 catalog-service 调用 provider，不再依赖固定 localhost URL。
5. 增加 application-nacos.yml，使用 spring.config.import 接入 Nacos Config。
6. 更新 docs/USAGE.md 和 docs/nacos-playbook.md，写清启动、停止、验证命令。
7. 增加必要测试，确保默认 profile 不被 Nacos 破坏。
```

## 示例内容

- `platform/nacos/docker-compose.yml` 启动 Nacos。
- `catalog-service` 注册服务名 `catalog-service`。
- `order-service` 使用 Feign + LoadBalancer 发现 `catalog-service`。
- Nacos Config 中维护 `demo.order.currency=CNY`、`demo.catalog.slow-delay=2s` 等示例配置。

## 实施要点

- 父 POM 引入 Spring Cloud Alibaba BOM，版本执行时再确认。
- Nacos 依赖只在 `nacos` Maven profile 或清晰隔离的可选配置里启用。
- `@FeignClient` 需要兼容默认 URL 模式和 Nacos 服务发现模式。
- Nacos Config 使用 `spring.config.import`，避免旧式 `bootstrap.yml`。
- Nacos 健康检查不要影响 liveness，避免 Nacos 短暂不可用导致应用被错误重启。

## 验收标准

- `./mvnw test` 通过。
- `docker compose -f platform/nacos/docker-compose.yml up -d` 可启动 Nacos。
- `SPRING_PROFILES_ACTIVE=nacos` 启动两个服务后，Nacos 控制台能看到 `catalog-service` 和 `order-service`。
- `order-service` 在不配置固定 URL 的情况下能成功调用 `catalog-service`。
- 修改 Nacos 配置后，应用能按计划读取配置；若支持动态刷新，需要有明确验证命令。
- README 或使用说明包含 Nacos 启停、注册验证、配置验证命令。

## 不做

- 不把 Nacos 变成默认启动依赖。
- 不接入外部生产 Nacos。
- 不提交真实账号、token 或生产 namespace。
