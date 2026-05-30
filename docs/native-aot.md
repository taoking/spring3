# Native Image / AOT 专题

## 目标

Native Image / AOT 是 Spring Boot 3 的重要升级点。本项目把它作为学习和面试专题保留，不作为默认构建、默认 CI 或默认发布路径。

当前策略：

- 优先用 `catalog-service` 做最小验证，避免一开始叠加 Feign、Gateway、Sentry、消息队列等复杂依赖。
- 使用 Spring Boot starter parent 已内置的 `native` Maven profile，不新增仓库自定义 native profile。
- 默认 `./mvnw test`、普通 jar 构建和 Docker Compose 部署不受影响。
- Native binary 构建只在本地有 GraalVM `native-image` 或使用 buildpacks 时手动执行。

## 当前验证结果

本地验证日期：2026-05-15。

| 项目 | 结果 |
| --- | --- |
| JDK | Temurin OpenJDK `21.0.10` |
| `native-image` | 未安装，`command -v native-image` 无输出 |
| Docker buildpacks native image | 通过，镜像 `spring3/catalog-service-native:local`，镜像 ID `ea8db41256a2` |
| Maven `native` profile | Spring Boot parent `3.5.14` 已提供 |
| `catalog-service` AOT 处理 | 通过 |
| `catalog-service` 本机 native binary 编译 | 已尝试，失败原因是本机缺少 `native-image` |
| `catalog-service` native 容器启动 | 通过，`/actuator/health` 返回 `UP` |

已通过命令：

```bash
./mvnw help:active-profiles -Pnative -pl catalog-service -am
./mvnw -pl catalog-service -am package -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local

docker run -d -p 18081:8081 \
  -e TRACING_SAMPLING_PROBABILITY=0.0 \
  --name spring3-catalog-native-test \
  spring3/catalog-service-native:local

curl -fsS http://localhost:18081/actuator/health
docker rm -f spring3-catalog-native-test
```

已尝试但当前本机失败：

```bash
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

失败核心原因：

```text
The 'native-image' tool was not found on your system.
```

这说明本机 GraalVM binary 仍需要安装 `native-image`；但 Docker buildpacks 路径已经完成从 AOT、native 编译、镜像生成到容器 health check 的闭环。

本次 native 验证暴露过 Hibernate Validator / JBoss Logging 运行期动态类问题，已通过 `CatalogNativeRuntimeHints` 补充：

- `org.hibernate.validator.internal.util.logging.Log_$logger`
- `org.hibernate.validator.internal.util.logging.Messages_$bundle`

容器启动日志中可见 `Started CatalogServiceApplication in 0.322 seconds` 左右。native 镜像生成阶段产物大小约 `129.89MB`，峰值 RSS 约 `5.19GB`，首次或冷缓存构建会更慢。

## 环境准备

检查 JDK：

```bash
java -version
```

检查 GraalVM native-image：

```bash
command -v native-image
native-image --version
```

如果本机没有 `native-image`，可选方案：

- 安装 GraalVM JDK 21，并设置 `JAVA_HOME` 或 `GRAALVM_HOME` 指向 GraalVM。
- 使用 Spring Boot buildpacks native 构建，需要 Docker Desktop。

再次确认 Spring Boot `native` profile：

```bash
./mvnw help:active-profiles -Pnative -pl catalog-service -am
```

## AOT 处理

先构建普通 jar，确认默认构建路径仍然正常：

```bash
./mvnw -pl catalog-service -am package -DskipTests
```

再只对 `catalog-service` 执行 AOT 处理：

```bash
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
```

注意不要直接把 `-am` 和 `spring-boot:process-aot` 组合使用。`common`、`demo-observability-autoconfigure`、`demo-observability-spring-boot-starter` 不是可启动应用，没有 main class，`spring-boot:process-aot` 跑到这些模块会失败。

如果在全新环境中只执行 `-pl catalog-service` 遇到 sibling module 依赖解析问题，可以先执行：

```bash
./mvnw -pl catalog-service -am install -DskipTests
```

然后再执行 `catalog-service` 的 AOT 或 native 命令。

## Native Binary 构建

本机有 GraalVM `native-image` 时执行：

```bash
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

构建成功后启动二进制：

```bash
TRACING_SAMPLING_PROBABILITY=0.0 catalog-service/target/catalog-service --server.port=8081
```

验证健康检查：

```bash
curl -fsS http://localhost:8081/actuator/health
```

第一次 native 构建通常耗时较长，取决于 CPU、内存、依赖数量和本地 GraalVM 版本。学习环境建议先只构建 `catalog-service`，不要一次性 native 化所有模块。

## Buildpacks Native 镜像

如果不想在本机安装 GraalVM，可以用 Spring Boot buildpacks 通过 Docker 构建 native image：

```bash
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local
```

运行镜像：

```bash
docker run -d -p 18081:8081 \
  -e TRACING_SAMPLING_PROBABILITY=0.0 \
  --name spring3-catalog-native-test \
  spring3/catalog-service-native:local
```

验证：

```bash
curl -fsS http://localhost:18081/actuator/health
docker rm -f spring3-catalog-native-test
```

Buildpacks 会拉取 builder/run image，并在容器里执行 native image 构建。这个过程比普通 jar 镜像构建慢，不建议放进默认 CI。

## 兼容注意事项

| 组件 | 注意点 | 当前建议 |
| --- | --- | --- |
| SpringDoc OpenAPI | 依赖反射、资源扫描和 swagger-ui 静态资源 | native 后必须验证 `/v3/api-docs` 和 `/swagger-ui.html` |
| Sentry | SDK transport、后台线程、环境变量和资源元数据需要验证 | 先验证无 DSN 启动，再用测试 DSN 验证事件上报 |
| OpenFeign | 声明式接口、动态代理、fallback factory、LoadBalancer 都会增加 native 复杂度 | 先 native 化 provider，再单独验证 `order-service` |
| Spring Cloud Gateway | WebFlux、Reactor Netty、DNS 和网络栈问题更复杂 | 放在后续阶段，不作为第一轮 native 目标 |
| Resilience4j | AOP、注解、fallback 方法匹配和 Micrometer 指标需要验证 | native 后覆盖失败、慢调用、限流和舱壁路径 |
| Hibernate Validator / JBoss Logging | validator 初始化时会通过动态类名查找 logger 和 bundle 实现 | 已补 `CatalogNativeRuntimeHints`，保留作为 RuntimeHints 示例 |
| 自定义 starter / AOP | `@DemoLog` 依赖 Spring AOP 代理 | 当前 `catalog-service` native health check 已通过，后续扩展接口路径时继续验证切面行为 |
| Jackson / ProblemDetail | record、枚举、错误扩展字段序列化要验证 | native 后至少验证正常响应、404 和校验失败 |
| 动态代理 / 反射 | native image 会提前封闭运行期可见类型 | 优先使用 Spring AOT 自动 hints，必要时再写显式 hints |

## 排障

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `native-image` not found | 当前 JDK 不是 GraalVM，或 `native-image` 不在 `PATH` | 安装 GraalVM JDK 21，设置 `JAVA_HOME` / `GRAALVM_HOME`，重新检查 `native-image --version` |
| 非应用模块提示找不到 main class | `spring-boot:process-aot` 跑到了 `common` 或 starter 模块 | 不要对 `spring-boot:process-aot` 使用 `-am`；先构建依赖，再只处理 app 模块 |
| 构建很慢或 OOM | native image 构建需要大量 CPU 和内存 | 关闭其他进程，增加 Docker Desktop 内存，或换更高配置机器 |
| 缺少 reflection/proxy/resource 配置 | 第三方库或自定义代码运行期动态行为未被 AOT 捕获 | 根据 native-image 报错补 Spring `RuntimeHints`，并加针对性测试 |
| `Invalid logger interface org.hibernate.validator.internal.util.logging.Log` | JBoss Logging 通过动态类名查找 `Log_$logger`，native 未自动保留 | 使用 `RuntimeHintsRegistrar` 注册 logger 实现类的 public constructor |
| `Invalid bundle interface org.hibernate.validator.internal.util.logging.Messages` | JBoss Logging 通过动态类名查找 `Messages_$bundle`，native 未自动保留 | 使用 `RuntimeHintsRegistrar` 注册 bundle 实现类字段和构造器 |
| Zipkin 未启动时出现导出失败日志 | trace 采样后 exporter 尝试连接默认 Zipkin endpoint | 本地 native 验证可设置 `TRACING_SAMPLING_PROBABILITY=0.0`，或启动 observability compose |
| buildpacks 拉镜像失败 | Docker 网络、代理或镜像源问题 | 先 `docker info`，再检查 Docker Desktop 网络和代理配置 |

## 面试复盘点

- AOT 是构建期分析和代码生成，Native Image 是把应用编译成本地二进制，两者不是同一个概念。
- Native Image 的收益主要是启动快、内存占用低、容器密度高。
- 代价是构建慢、诊断方式变化、反射和动态代理受限、第三方库兼容性需要验证。
- 微服务项目应先从依赖最少的 provider 验证，再逐步扩展到 consumer、gateway 和消息组件。
- 默认 CI 是否加入 native 构建要看团队收益和成本，学习项目不应让默认反馈链路变慢。
