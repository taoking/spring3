# 06 自定义 Starter / Autoconfigure 计划

## 目标

新增一个演示型 starter，把自动配置原理从文档落到代码，覆盖条件装配、属性绑定、默认 Bean、用户覆盖 Bean。

## 任务 Prompt

```text
为当前项目新增自定义 Spring Boot starter/autoconfigure 示例。请先阅读 common 模块、LoggingAspect 和 docs/task-plans/06-autoconfigure-starter.md。

要求：
1. 新增 autoconfigure 模块和 starter 模块，命名保持清晰。
2. 将一个可复用能力迁移到 starter，例如 DemoLog AOP 或统一 Web 错误响应辅助能力。
3. 使用 @AutoConfiguration、@ConditionalOnClass、@ConditionalOnMissingBean、@ConfigurationProperties。
4. 提供 spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports。
5. 增加测试验证自动配置生效、禁用配置生效、用户自定义 Bean 可覆盖默认 Bean。
6. 更新文档，解释 starter、autoconfigure、starter 依赖聚合的职责差异。
```

## 示例内容

- `demo-observability-autoconfigure`
- `demo-observability-spring-boot-starter`
- `DemoLogProperties` 控制是否开启 AOP、慢调用阈值。
- 服务模块只引入 starter，不再复制相同配置。

## 实施要点

- starter 模块只做依赖聚合，不写业务代码。
- autoconfigure 模块提供默认配置，但允许业务方覆盖。
- 避免把服务私有逻辑放入公共 starter。
- 注意 Spring Boot 3 的自动配置注册方式。

## 验收标准

- `./mvnw test` 通过。
- 服务模块引入 starter 后功能保持不变。
- 关闭配置项后相关自动配置不生效。
- 自定义 Bean 存在时默认 Bean 不覆盖用户配置。
- 文档能说明自动配置触发条件和排查方式。

## 不做

- 不发布到远程 Maven 仓库。
- 不把所有公共代码都迁移到 starter。
