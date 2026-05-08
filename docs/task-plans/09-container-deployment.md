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
