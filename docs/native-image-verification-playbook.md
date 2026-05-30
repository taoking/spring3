# Native Image 完整验证专题

## 目标

完成 `catalog-service` 从 Spring AOT 到 buildpacks native 镜像、容器启动和 health check 的闭环，并沉淀 RuntimeHints、构建成本、运行期限制和面试追问。

默认构建、默认 CI、普通 jar 部署不引入 native 构建，native 只作为手动专题验证。

## 当前结论

| 项目 | 结果 |
| --- | --- |
| 本机 JDK | Temurin OpenJDK `21.0.10` |
| 本机 `native-image` | 未安装，`native:compile` 不通过 |
| Docker buildpacks native image | 通过 |
| native 镜像 | `spring3/catalog-service-native:local` |
| 最新镜像 ID | `ea8db41256a2` |
| native health check | `HTTP 200`，`{"status":"UP","groups":["liveness","readiness"]}` |
| 启动耗时 | 日志显示约 `0.322s` |
| native 可执行产物规模 | native-image 阶段报告约 `129.89MB` |
| 构建资源峰值 | native-image 阶段 peak RSS 约 `5.19GB` |

## 代码改动

`catalog-service` 增加 `CatalogNativeRuntimeHints`，用于保留 Hibernate Validator 通过 JBoss Logging 动态查找的实现类：

- `org.hibernate.validator.internal.util.logging.Log_$logger`
- `org.hibernate.validator.internal.util.logging.Messages_$bundle`

触发原因：

- `@Validated @ConfigurationProperties` 会初始化 Hibernate Validator。
- Hibernate Validator 内部 logger 和 message bundle 使用 JBoss Logging 生成类。
- 这些类名通过约定动态拼接，Native Image 不一定能自动推断，需要显式 RuntimeHints。

## 验证命令

普通构建与测试：

```bash
./mvnw -pl catalog-service -am test
```

AOT 处理：

```bash
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
```

确认 hints 已进入反射配置：

```bash
rg 'Log_\$logger|Messages_\$bundle' \
  catalog-service/target/classes/META-INF/native-image/com.taoking.spring3/catalog-service/reflect-config.json
```

本机 GraalVM native binary 编译：

```bash
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

当前本机失败原因：

```text
The 'native-image' tool was not found on your system.
```

Docker buildpacks native 镜像：

```bash
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local
```

启动并验证：

```bash
docker run -d -p 18081:8081 \
  -e TRACING_SAMPLING_PROBABILITY=0.0 \
  --name spring3-catalog-native-test \
  spring3/catalog-service-native:local

curl -i http://localhost:18081/actuator/health
docker logs --tail 80 spring3-catalog-native-test
docker rm -f spring3-catalog-native-test
```

## 失败复盘

第一轮 native 镜像能够构建，但容器启动失败：

```text
Invalid logger interface org.hibernate.validator.internal.util.logging.Log (implementation not found)
```

补充 `Log_$logger` 后，失败点前进为：

```text
Invalid bundle interface org.hibernate.validator.internal.util.logging.Messages (implementation not found)
```

继续补充 `Messages_$bundle` 后，容器启动成功并通过 health check。

这类问题的面试价值在于：JVM 模式通过不代表 native 模式通过，AOT 处理通过也不代表 native 运行期所有动态路径都已覆盖。

## 运行注意

- native 构建慢，适合夜间或手动任务，不进入默认 CI。
- Docker Desktop 需要可用，buildpacks 会拉取 builder/run image。
- `TRACING_SAMPLING_PROBABILITY=0.0` 用于本地 native 验证时避免 Zipkin 未启动产生 exporter 连接日志。
- 启动日志里的 Logback `DefaultJoranConfigurator` / `BasicConfigurator` class not found 是 native 环境下的初始化提示，本次验证不影响应用启动和 health check。
- `JvmGcMetrics` 在 native 环境下提示部分 GC notification 不可用，属于 Native Image 监控差异，需要在生产可观测性方案中单独评估。

## 后续扩展

| 方向 | 内容 | 验收 |
| --- | --- | --- |
| catalog 接口路径 | 验证 `/api/catalog/products`、404、校验失败、OpenAPI JSON | curl 返回符合 JVM 模式预期 |
| Sentry | 无 DSN 启动、测试 DSN 上报、transport 线程关闭 | 事件可观测，未配置 DSN 无异常 |
| SpringDoc | `/v3/api-docs`、`/swagger-ui.html` | JSON 和静态资源可访问 |
| order-service | Feign、RestClient、Resilience4j、JWT profile native 验证 | health 和订单预览通过 |
| gateway-service | WebFlux Gateway native 验证 | 静态路由、fallback、CORS、灰度路由通过 |
| CI 策略 | 手动 workflow 或 nightly native job | 不拖慢默认 PR 反馈 |

## 面试覆盖

- AOT 与 Native Image 的区别是什么？
- 为什么 AOT 通过不等于 native image 运行成功？
- Native Image 的收益和成本分别是什么？
- 反射、动态代理、资源扫描在 native 下为什么容易出问题？
- Spring Boot 3 的 `RuntimeHintsRegistrar` 解决什么问题？
- 为什么不把 native 构建放进默认 CI？
- 如何选择第一个 native 化的服务？
- 如何对比 JVM jar、JVM container 和 native container 的启动耗时、内存、镜像大小？
