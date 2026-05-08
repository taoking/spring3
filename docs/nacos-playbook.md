# Nacos 补充专题

## 定位

Nacos 是 Spring Cloud Alibaba 体系里高频出现的注册中心和配置中心。当前项目默认运行路径仍然保持轻量，不把 Nacos 加入必需依赖；Nacos 作为可选专题补充，通过 Maven `nacos` profile 和 Spring `nacos` profile 启用。

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

仓库提供了 Nacos 3.0.3 单机 Derby 版 Docker Compose 配置，本地学习环境显式关闭鉴权。Compose 中的 `NACOS_AUTH_TOKEN` 是镜像启动脚本要求的本地占位值，不是生产密钥：

```bash
docker compose -f platform/nacos/docker-compose.yml up -d
docker logs -f spring3-nacos
```

访问：

- 控制台：`http://localhost:8847`
- 客户端 API：`http://localhost:8848`
- gRPC：`localhost:9848`

该 Compose 配置只用于本地学习，不用于生产。生产或不可信网络必须启用鉴权，不要直接复用本地配置。

## 代码接入方式

当前已实现：

1. 父 POM 引入 `spring-cloud-alibaba-dependencies:2025.0.0.0`。
2. `catalog-service`、`order-service` 中通过 `nacos` Maven profile 引入：
   - `spring-cloud-starter-alibaba-nacos-discovery`
   - `spring-cloud-starter-alibaba-nacos-config`
   - `spring-cloud-starter-loadbalancer`，仅 `order-service` 需要
3. 新增 `application-nacos.yml`，只在 `SPRING_PROFILES_ACTIVE=nacos` 时启用：
   - `spring.cloud.nacos.server-addr`
   - `spring.config.import=optional:nacos:order-service.yml?refreshEnabled=true`
   - `spring.config.import=optional:nacos:catalog-service.yml?refreshEnabled=true`
4. 默认 `application.yml` 显式关闭 Nacos config/discovery，避免只启用 Maven profile 时影响默认测试。
5. `catalog-service` 注册到 Nacos，验证服务列表里出现 `catalog-service`。
6. `order-service` 把 Feign 调用从固定 URL 扩展为发现优先：
   - 默认 profile 继续使用 `demo.clients.catalog.base-url=http://localhost:8081`。
   - `nacos` profile 使用服务名 `catalog-service` + Spring Cloud LoadBalancer。

## 启动与验证

启动 Nacos：

```bash
docker compose -f platform/nacos/docker-compose.yml up -d
```

写入示例配置：

```bash
curl -fsS -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=order-service.yml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'content=demo:
  order:
    currency: NCS'

curl -fsS -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=catalog-service.yml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'content=demo:
  catalog:
    slow-delay: 1s'
```

打包并启动服务：

```bash
./mvnw -Pnacos package -DskipTests

SPRING_PROFILES_ACTIVE=nacos java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar
SPRING_PROFILES_ACTIVE=nacos java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

验证注册发现：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=catalog-service'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=order-service'
```

验证配置读取和 Feign 服务名调用：

```bash
curl -u admin:admin123 http://localhost:8080/api/orders/admin/stats
curl -u admin:admin123 http://localhost:8081/api/catalog/admin/stats

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

## 验收标准

- 默认 profile 下 `./mvnw test` 仍然通过，不需要启动 Nacos。
- `./mvnw -Pnacos test` 通过，证明可选 Nacos 依赖不会破坏默认 Spring profile。
- `docker compose -f platform/nacos/docker-compose.yml config` 通过。
- Nacos 启动后，控制台可以访问，客户端 API 端口可连通。
- `nacos` profile 下两个服务能注册到 Nacos。
- `order-service` 在不配置固定 URL 时能通过 Nacos 发现并调用 `catalog-service`。
- 应用启动时可通过 `spring.config.import` 拉取 Nacos 配置。当前只验收启动期读取；动态刷新作为后续扩展单独验证。

## 面试重点

- 注册中心和配置中心分别解决什么问题，和 DNS、环境变量、配置文件的边界在哪里。
- 服务注册流程：实例注册、心跳、健康状态、摘除、临时实例和持久实例。
- 服务发现流程：服务名、namespace、group、cluster、metadata、权重、负载均衡。
- 配置中心流程：dataId、group、namespace、配置优先级、动态刷新、灰度发布。
- Spring Cloud Alibaba 2025.x 的接入变化：`spring.config.import`、Nacos Server 3.x、HealthIndicator 默认关闭。
- 可用性设计：注册中心故障时客户端缓存、本地容灾、配置拉取失败的启动策略。
- 安全：控制台密码、token/identity key、内网部署，不把 Nacos 暴露到公网。
- 排障：端口 `8848/9848/9849`、Docker 网络、namespace ID 写错、服务名和 group 不一致。
