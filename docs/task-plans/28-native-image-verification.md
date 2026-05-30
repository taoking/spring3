# 28 Native Image 完整验证计划

## 目标

把 `catalog-service` 的 Spring AOT / Native Image 从文档基线推进到可运行闭环：AOT 处理、native 镜像构建、容器启动、health check、RuntimeHints 复盘和面试追问。

## 任务 Prompt

```text
基于当前 Spring Boot 3 学习项目，完成 Native Image 完整验证专题。请先阅读：

- README.md
- docs/native-aot.md
- docs/task-plans/16-native-aot.md
- docs/task-plans/19-interview-expansion.md

要求：
1. 以 catalog-service 为最小 native 验证目标，不影响默认 jar 构建和默认 CI。
2. 检查本机 native-image；如果本机没有 GraalVM native-image，则使用 Docker buildpacks 构建 native 镜像。
3. 启动 native 镜像并验证 /actuator/health。
4. 如遇反射、动态代理、资源、第三方库初始化问题，优先用 Spring RuntimeHints 解决，并记录失败原因。
5. 记录构建耗时、启动耗时、镜像/可执行体规模、限制和后续扩展点。
6. 更新 README、USAGE、native 专题、面试路线和实施日志。
```

## 当前实施结果

- 已新增专题文档：[Native Image 完整验证专题](../native-image-verification-playbook.md)。
- `catalog-service` 增加 `CatalogNativeRuntimeHints`，覆盖 Hibernate Validator / JBoss Logging 在 native 运行期动态查找的类。
- Docker buildpacks native 镜像构建通过，镜像为 `spring3/catalog-service-native:local`。
- native 容器启动通过，`/actuator/health` 返回 `UP`。
- 本机 `native:compile` 仍受限于没有安装 GraalVM `native-image`。
- 未把 native 构建加入默认 CI。

## 已执行验证

通过：

```bash
./mvnw -pl catalog-service -am test
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local

docker run -d -p 18081:8081 \
  -e TRACING_SAMPLING_PROBABILITY=0.0 \
  --name spring3-catalog-native-test \
  spring3/catalog-service-native:local

curl -i http://localhost:18081/actuator/health
docker rm -f spring3-catalog-native-test
```

本机工具链限制：

```bash
command -v native-image
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

结果：

```text
The 'native-image' tool was not found on your system.
```

## 示例内容

- RuntimeHints 示例：保留 Hibernate Validator 的 `Log_$logger` 和 `Messages_$bundle`。
- buildpacks 示例：不安装本机 GraalVM，用 Docker 完成 native image。
- 验证示例：容器端口映射到 `18081`，访问 `/actuator/health`。
- 排障示例：从 `Invalid logger interface` 追到 `Invalid bundle interface`，逐步补 hints。

## 验收标准

- 默认 `./mvnw -pl catalog-service -am test` 通过。
- `spring-boot:process-aot` 通过。
- native 镜像可构建。
- native 容器可启动。
- `/actuator/health` 返回 `UP`。
- 文档记录本机 `native-image` 不存在时的替代路径。
- 文档能解释 RuntimeHints 的必要性、AOT 与 native 的区别和 CI 取舍。

## 不做

- 不要求所有服务 native 化。
- 不把 native 构建加入默认 PR CI。
- 不接入真实 Sentry DSN。
- 不承诺 SpringDoc、Sentry、Feign、Gateway 已全部完成 native 生产级验证。
