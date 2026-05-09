# 08 Testcontainers / CI 计划

## 目标

补充可重复的集成测试和 GitHub Actions CI，让项目具备资深面试中常见的工程质量证明。

## 任务 Prompt

```text
为当前项目补充 Testcontainers 和 GitHub Actions CI。请先阅读现有测试、docs/USAGE.md 和 docs/task-plans/08-testcontainers-ci.md。

要求：
1. 保留现有单元测试和 MockWebServer 测试。
2. 新增 integration-test profile，使用 Testcontainers 验证一个外部依赖专题，例如 Nacos、Gateway 下游服务或追踪后端。
3. 集成测试默认不拖慢普通 ./mvnw test，可通过 profile 显式运行。
4. 新增 GitHub Actions workflow，至少执行 ./mvnw test。
5. 如果集成测试依赖 Docker，在 workflow 中明确启用或单独 job。
6. 更新 docs/USAGE.md，写明本地和 CI 运行方式。
```

## 示例内容

- `.github/workflows/ci.yml`
- `./mvnw test`
- `./mvnw -Pintegration-test verify`
- Testcontainers 管理临时 Nacos 或 mock HTTP server 容器。

## 实施要点

- 普通测试要快，集成测试要可选择。
- 容器镜像版本要固定，避免 latest 引起 CI 不稳定。
- CI 缓存 Maven 依赖。
- 对 Docker 不可用时给出清晰跳过或失败信息。

## 验收标准

- `./mvnw test` 本地通过。
- `./mvnw -Pintegration-test verify` 在 Docker 可用时通过。
- GitHub Actions workflow 语法正确。
- README 显示 CI 状态或说明 CI 命令。
- 集成测试失败时错误信息能定位到外部依赖问题。

## 不做

- 不强制所有开发机器都运行集成测试。
- 不引入数据库或 Redis 作为测试依赖。

## 实施记录

- 已新增父工程 `integration-test` Maven profile，使用 Maven Failsafe 运行 `**/*IT.java`。
- 已在 `gateway-service` 加入 Testcontainers JUnit Jupiter 测试依赖。
- 已新增 `GatewayNginxContainerIT`，使用固定镜像 `nginx:1.27.3-alpine` 作为真实下游容器，验证 Gateway `/catalog/**` 路由。
- 已新增 `gateway-service/src/test/resources/testcontainers/nginx-default.conf`，提供容器内测试响应和 health endpoint。
- 已新增 `.github/workflows/ci.yml`，包含 `unit-tests` 和 `integration-tests` 两个 job，均使用 JDK 21 和 Maven cache。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

已验证：

```bash
./mvnw -pl gateway-service -am test
./mvnw -pl gateway-service -am -Pintegration-test verify
./mvnw test
./mvnw -Pintegration-test verify
./mvnw -Pnacos test
./mvnw package -DskipTests
```
