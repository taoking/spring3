# 09 Docker 镜像与部署计划

## 目标

补充应用容器化能力，覆盖镜像构建、运行、健康检查、优雅停机和完整本地 Compose。

## 任务 Prompt

```text
为当前项目补充 Docker 镜像与部署示例。请先阅读 docs/USAGE.md、observability/docker-compose.yml 和 docs/task-plans/09-container-deployment.md。

要求：
1. 为 catalog-service、order-service 增加 Dockerfile 或 Spring Boot build image 方案。
2. 新增 app Docker Compose，能启动两个服务和 observability 组件。
3. 容器内服务间调用使用 Docker network 服务名，不使用 host.docker.internal。
4. 配置 readiness/liveness、优雅停机和 JVM 参数示例。
5. Prometheus 在容器网络中抓取服务名。
6. 更新 docs/USAGE.md，补充镜像构建、启动、停止、日志、排障命令。
```

## 示例内容

- `docker build -t spring3-order-service ./order-service`
- `docker compose -f deployment/docker-compose.yml up -d`
- Prometheus targets 指向 `order-service:8080` 和 `catalog-service:8081`。

## 实施要点

- 镜像不要包含本地 `target/run.log`。
- 使用非 root 用户运行应用。
- 通过环境变量覆盖配置。
- 关闭服务时验证 graceful shutdown。

## 验收标准

- `./mvnw package -DskipTests` 通过。
- 两个服务镜像可构建。
- Compose 启动后通过容器网络完成 order -> catalog 调用。
- Prometheus targets 为 `up`。
- `docker compose down` 能完整清理容器和网络。

## 不做

- 不推送镜像到远程 registry。
- 不引入 Kubernetes 作为本任务范围。

## 实施记录

- 已新增 `catalog-service/Dockerfile` 和 `order-service/Dockerfile`，使用 JDK 21 JRE Alpine、非 root 用户、`JAVA_OPTS` 和模块内 jar 构建镜像。
- 已新增两个模块的 `.dockerignore`，镜像上下文只包含 Dockerfile 和目标 jar，避免带入本地日志或多余构建产物。
- 已在 `catalog-service`、`order-service` 中配置 `server.shutdown` 和 `spring.lifecycle.timeout-per-shutdown-phase`，支持优雅停机。
- 已新增 `deployment/docker-compose.yml`，启动 `catalog-service`、`order-service`、Prometheus、Grafana、Zipkin。
- 已新增 `deployment/prometheus/prometheus.yml`，Prometheus 在容器网络中通过 `catalog-service:8081` 和 `order-service:8080` 抓取指标。
- 已更新 `README.md`、`docs/USAGE.md`、`docs/IMPLEMENTATION.md`、`docs/interview-roadmap.md`。

已验证：

```bash
./mvnw package -DskipTests
docker compose -f deployment/docker-compose.yml config
docker compose -f deployment/docker-compose.yml build
docker compose -f deployment/docker-compose.yml up -d
curl -u user:user123 -H 'Content-Type: application/json' -d '{"sku":"SKU-1001","quantity":2}' http://localhost:8080/api/orders/preview
curl -fsS 'http://localhost:9090/api/v1/query?query=up'
docker compose -f deployment/docker-compose.yml down
./mvnw test
./mvnw -Pnacos test
```

验证结果：

- 两个业务服务镜像均构建成功：`spring3/catalog-service:local`、`spring3/order-service:local`。
- Compose 启动后 `catalog-service`、`order-service` healthcheck 均为 `healthy`。
- readiness/liveness endpoint 均返回 `{"status":"UP"}`。
- 订单预览接口返回 `fallbackUsed=false`，日志显示 Feign 调用 `http://catalog-service:8081`。
- Prometheus active targets 为 `catalog-service:8081`、`order-service:8080`，查询 `up` 均为 `1`。
- `docker compose down` 已移除容器和 `spring3-deployment_default` 网络。
