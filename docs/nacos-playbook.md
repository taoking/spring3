# Nacos 补充专题

## 定位

Nacos 是 Spring Cloud Alibaba 体系里高频出现的注册中心和配置中心。当前项目默认运行路径仍然保持轻量，不把 Nacos 加入必需依赖；Nacos 作为可选专题补充，适合后续用独立 profile 演示。

## 版本基线

- 当前项目：Spring Boot `3.5.14`，Spring Cloud `2025.0.2`。
- 官方 Spring Cloud Alibaba 2025.0.x 文档说明该分支适配 Spring Boot `3.5.x` 和 Spring Cloud `2025.0.x`。
- Spring Cloud Alibaba `2025.0.0.0` 对应 Nacos `3.0.3`。
- Spring Cloud Alibaba 2025.x 接入 Nacos Config 时应使用 `spring.config.import`，不要继续依赖旧的 `bootstrap.yml` 方式。

参考：

- https://sca.aliyun.com/en/docs/2025.x/overview/version-explain/
- https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/quick-start/
- https://sca.aliyun.com/en/docs/2025.x/user-guide/nacos/advanced-guide/
- https://nacos.io/docs/latest/quickstart/quick-start-docker/

## 本地 Nacos

仓库提供了 Nacos 3.0.3 单机 Derby 版 Docker Compose 配置：

```bash
docker compose -f platform/nacos/docker-compose.yml up -d
docker logs -f spring3-nacos
```

访问：

- 控制台：`http://localhost:8847`
- 客户端 API：`http://localhost:8848`
- gRPC：`localhost:9848`

Nacos 3 控制台首次访问会要求初始化管理员用户 `nacos` 的密码。该 Compose 配置只用于本地学习，不用于生产。

## 后续代码接入计划

1. 在父 POM 中引入 `spring-cloud-alibaba-dependencies:2025.0.0.0`。
2. 在 `catalog-service`、`order-service` 中通过 `nacos` Maven profile 引入：
   - `spring-cloud-starter-alibaba-nacos-discovery`
   - `spring-cloud-starter-alibaba-nacos-config`
3. 新增 `application-nacos.yml`，只在 `SPRING_PROFILES_ACTIVE=nacos` 时启用：
   - `spring.cloud.nacos.discovery.server-addr`
   - `spring.cloud.nacos.config.server-addr`
   - `spring.config.import=optional:nacos:order-service.yml?refreshEnabled=true`
   - `spring.config.import=optional:nacos:catalog-service.yml?refreshEnabled=true`
4. `catalog-service` 注册到 Nacos，验证服务列表里出现 `catalog-service`。
5. `order-service` 把 Feign 调用从固定 URL 扩展为发现优先：
   - 默认 profile 继续使用 `demo.clients.catalog.base-url=http://localhost:8081`。
   - `nacos` profile 使用服务名 `catalog-service` + Spring Cloud LoadBalancer。
6. 暴露并验证 Nacos 相关 actuator 信息，避免把 Nacos 健康检查直接绑定到 Kubernetes liveness。

## 验收标准

- 默认 profile 下 `./mvnw test` 仍然通过，不需要启动 Nacos。
- `docker compose -f platform/nacos/docker-compose.yml config` 通过。
- Nacos 启动后，控制台可以访问，客户端 API 端口可连通。
- `nacos` profile 下两个服务能注册到 Nacos。
- `order-service` 在不配置固定 URL 时能通过 Nacos 发现并调用 `catalog-service`。
- 在 Nacos 中修改配置后，应用可通过 `spring.config.import` 拉取配置；需要动态刷新时，明确验证刷新路径。

## 面试重点

- 注册中心和配置中心分别解决什么问题，和 DNS、环境变量、配置文件的边界在哪里。
- 服务注册流程：实例注册、心跳、健康状态、摘除、临时实例和持久实例。
- 服务发现流程：服务名、namespace、group、cluster、metadata、权重、负载均衡。
- 配置中心流程：dataId、group、namespace、配置优先级、动态刷新、灰度发布。
- Spring Cloud Alibaba 2025.x 的接入变化：`spring.config.import`、Nacos Server 3.x、HealthIndicator 默认关闭。
- 可用性设计：注册中心故障时客户端缓存、本地容灾、配置拉取失败的启动策略。
- 安全：控制台密码、token/identity key、内网部署，不把 Nacos 暴露到公网。
- 排障：端口 `8848/9848/9849`、Docker 网络、namespace ID 写错、服务名和 group 不一致。
