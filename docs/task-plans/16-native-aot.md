# 16 Native Image / AOT 计划

## 目标

补充 Spring Boot 3 AOT 和 Native Image 学习材料，了解收益、限制、构建成本和第三方库兼容问题。

## 任务 Prompt

```text
为当前项目补充 Native Image / AOT 专题。请先阅读 pom.xml、两个服务的依赖和 docs/task-plans/16-native-aot.md。

要求：
1. 增加 native Maven profile 或文档化 Spring Boot buildpacks native 构建方式。
2. 优先选择 catalog-service 做最小 native 验证。
3. 记录 Sentry、SpringDoc、Feign、反射、动态代理等库的兼容注意事项。
4. 不把 native 构建放入默认 CI。
5. 更新文档，说明构建命令、预计耗时、失败排查。
```

## 示例内容

- `./mvnw -pl catalog-service -Pnative native:compile`
- 或 `./mvnw -pl catalog-service spring-boot:build-image -Pnative`
- 启动 native 二进制后访问 `/actuator/health`。

## 实施要点

- Native 构建耗时长，对本地环境要求高。
- 先验证简单 provider，再考虑 order-service。
- 对反射和动态代理问题要记录而不是硬绕。

## 验收标准

- 默认 `./mvnw test` 通过。
- native profile 不影响普通 jar 构建。
- 至少 catalog-service 有明确 native 构建尝试结果。
- 文档记录成功命令或失败原因和后续处理建议。

## 不做

- 不要求所有模块都 native 化。
- 不把 native 构建作为默认发布路径。
