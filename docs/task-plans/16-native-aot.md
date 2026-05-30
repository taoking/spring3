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

## 当前实施结果

- 已新增专题文档：[Native Image / AOT 专题](../native-aot.md)。
- 未新增仓库自定义 native Maven profile，因为 Spring Boot starter parent `3.5.14` 已内置 `native` profile。
- 已选择 `catalog-service` 作为最小验证目标。
- 已确认 `catalog-service` 普通 jar 构建不受影响。
- 已完成 `catalog-service` Spring AOT 处理验证。
- 已尝试 `catalog-service` 本机 native binary 编译，当前本机失败原因是未安装 GraalVM `native-image`。
- 已通过 Docker buildpacks 完成 `catalog-service` native 镜像构建、容器启动和 `/actuator/health` 验证。
- 已补充 `CatalogNativeRuntimeHints`，覆盖 Hibernate Validator / JBoss Logging 在 native 运行期动态查找的 `Log_$logger` 和 `Messages_$bundle`。
- 未把 native 构建加入默认 CI。

## 已执行验证

通过：

```bash
./mvnw help:active-profiles -Pnative -pl catalog-service -am
./mvnw -pl catalog-service -am package -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local
```

buildpacks native 镜像启动验证：

```bash
docker run -d -p 18081:8081 \
  -e TRACING_SAMPLING_PROBABILITY=0.0 \
  --name spring3-catalog-native-test \
  spring3/catalog-service-native:local

curl -i http://localhost:18081/actuator/health
docker rm -f spring3-catalog-native-test
```

已尝试但本机失败：

```bash
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

失败原因：

```text
The 'native-image' tool was not found on your system.
```

后续如果要继续验证本机 native binary，需要先安装 GraalVM JDK 21 并确保 `native-image` 在 `PATH` 中。

## 实施要点

- Native 构建耗时长，对本地环境要求高。
- 先验证简单 provider，再考虑 order-service。
- 对反射和动态代理问题要记录原因，优先用 Spring RuntimeHints 显式表达。

## 验收标准

- 默认 `./mvnw test` 通过。
- native profile 不影响普通 jar 构建。
- 至少 catalog-service 有明确 native 构建尝试结果和 buildpacks 可运行结果。
- 文档记录成功命令或失败原因和后续处理建议。
- README、使用说明、实施文档和面试路线包含专题入口。

## 不做

- 不要求所有模块都 native 化。
- 不把 native 构建作为默认发布路径。
