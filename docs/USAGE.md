# 使用说明

## 环境要求

- JDK 21
- Docker Desktop
- Maven Wrapper：使用仓库内的 `./mvnw`
- 可选工具：`jq`，用于格式化 Prometheus API 返回

## 项目配置

| 项目 | 配置 |
| --- | --- |
| Java | `21` |
| Spring Boot | `3.5.14` |
| Spring Cloud | `2025.0.2` |
| Order Service | `localhost:8080` |
| Catalog Service | `localhost:8081` |
| Gateway Service | `localhost:8088` |
| Prometheus | `localhost:9090` |
| Grafana | `localhost:3000` |
| Zipkin | `localhost:9411` |
| RabbitMQ AMQP | `localhost:5672` |
| RabbitMQ Management | `localhost:15672` |
| Kafka broker | `localhost:9092` |
| Kafka UI | `localhost:8089` |
| Nacos 控制台 | `localhost:8847` |
| Nacos 客户端 API | `localhost:8848` |

默认账号：

| 场景 | 用户名 | 密码 |
| --- | --- | --- |
| 业务普通用户 | `user` | `user123` |
| 业务管理员 | `admin` | `admin123` |
| Grafana | `admin` | `admin` |
| RabbitMQ Management | `guest` | `guest` |

主要配置文件：

| 文件 | 说明 |
| --- | --- |
| `pom.xml` | 父工程、版本、依赖管理 |
| `demo-observability-autoconfigure/pom.xml` | `@DemoLog` 自动配置、条件装配、配置绑定、默认 Bean |
| `demo-observability-spring-boot-starter/pom.xml` | starter 依赖聚合，业务服务只需要引入它 |
| `catalog-service/src/main/resources/application.yml` | 商品服务端口、商品样例、Actuator、Sentry |
| `order-service/src/main/resources/application.yml` | 订单服务端口、HTTP client 模式、Feign、RestClient、缓存、Resilience4j、Actuator、Sentry |
| `order-service/src/main/resources/application-rabbitmq.yml` | RabbitMQ 连接、exchange/queue/DLQ、listener retry |
| `order-service/src/main/resources/application-kafka.yml` | Kafka producer/consumer、topic/DLT、manual ack、重试和演示参数 |
| `gateway-service/src/main/resources/application.yml` | 网关端口、静态路由、本地限流、fallback、Actuator |
| `gateway-service/src/main/resources/application-nacos.yml` | 网关 Nacos 服务发现路由 |
| `observability/docker-compose.yml` | Prometheus + Grafana + Zipkin |
| `observability/prometheus/prometheus.yml` | Prometheus 抓取目标 |
| `deployment/docker-compose.yml` | 应用容器 + Prometheus + Grafana + Zipkin 一体化本地部署 |
| `deployment/prometheus/prometheus.yml` | 容器网络内按服务名抓取业务服务指标 |
| `deployment/k8s/*.yaml` | Kubernetes 最小部署示例 |
| `docs/kubernetes.md` | Kubernetes 部署、探针、滚动发布和排障说明 |
| `platform/nacos/docker-compose.yml` | 本地 Nacos 3.0.3 |
| `platform/rabbitmq/docker-compose.yml` | 本地 RabbitMQ + Management UI |
| `platform/kafka/docker-compose.yml` | 本地 Kafka + Kafka UI |
| `docs/kafka-playbook.md` | Kafka 使用、事件设计、测试和面试复盘 |
| `docs/native-aot.md` | Spring AOT / Native Image 构建、验证和排障说明 |

常用环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SENTRY_DSN` | Sentry DSN，未设置时不真实上报 | 空 |
| `APP_ENV` | Sentry environment | `local` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | 默认 profile |
| `TRACING_SAMPLING_PROBABILITY` | trace 采样率，学习环境默认全采样 | `1.0` |
| `ZIPKIN_TRACING_ENABLED` | 是否向 Zipkin 上报 trace | `true` |
| `ZIPKIN_ENDPOINT` | Zipkin span 上报地址 | `http://localhost:9411/api/v2/spans` |
| `DEMO_CLIENTS_CATALOG_MODE` | order-service 调用 catalog-service 的 HTTP client 模式：`feign` 或 `restclient` | `feign` |
| `DEMO_SECURITY_JWT_SECRET` | `jwt` profile 下本地 HS256 测试 token 密钥，至少 32 字节 | `spring3-local-dev-secret-key-32-bytes-minimum` |
| `RABBITMQ_HOST` | `rabbitmq` profile 下 RabbitMQ 主机 | `localhost` |
| `RABBITMQ_PORT` | `rabbitmq` profile 下 RabbitMQ AMQP 端口 | `5672` |
| `RABBITMQ_USERNAME` | `rabbitmq` profile 下 RabbitMQ 用户名 | `guest` |
| `RABBITMQ_PASSWORD` | `rabbitmq` profile 下 RabbitMQ 密码 | `guest` |
| `ORDER_PREVIEW_POISON_SKU` | RabbitMQ 消费失败演示 SKU | `SKU-RABBITMQ-FAIL` |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka` profile 下 Kafka bootstrap servers | `localhost:9092` |
| `ORDER_KAFKA_TOPIC` | Kafka 主 topic | `spring3.order-preview.events.v1` |
| `ORDER_KAFKA_DLT_TOPIC` | Kafka 死信 topic | `spring3.order-preview.dlt.v1` |
| `ORDER_KAFKA_CONSUMER_GROUP` | Kafka 消费组 | `spring3-order-preview` |
| `ORDER_KAFKA_POISON_SKU` | Kafka 消费失败演示 SKU | `SKU-KAFKA-FAIL` |
| `JAVA_OPTS` | 容器内 JVM 参数，例如内存比例和 OOM 退出策略 | `-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` |
| `SERVER_SHUTDOWN` | Spring Boot 优雅停机开关 | `graceful` |
| `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE` | 优雅停机每阶段等待时间 | `20s` |
| `GRAALVM_HOME` | Native Image 本地编译时可指向 GraalVM JDK 21 | 未设置 |

## 构建与测试

```bash
./mvnw test
./mvnw package -DskipTests
```

Docker Desktop 可用时运行 Testcontainers 集成测试：

```bash
./mvnw -Pintegration-test verify
```

只运行 Gateway 的容器集成测试：

```bash
./mvnw -pl gateway-service -am -Pintegration-test verify
```

只运行 RabbitMQ 容器集成测试：

```bash
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

只运行 Kafka 容器集成测试：

```bash
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

`integration-test` Maven profile 使用 Failsafe 执行 `**/*IT.java`，普通 `./mvnw test` 不会启动容器。当前 `GatewayNginxContainerIT` 使用固定镜像 `nginx:1.27.3-alpine` 模拟真实下游服务，`OrderRabbitMqProfileIT` 使用固定镜像 `rabbitmq:3.13-management` 验证消息生产、消费、幂等和 DLQ，`OrderKafkaProfileIT` 使用固定镜像 `confluentinc/cp-kafka:7.6.1` 验证 Kafka 生产消费、幂等、顺序和 DLT。RabbitMQ/Kafka IT 还需要额外启用对应 Maven profile，所以默认 `./mvnw -Pintegration-test verify` 不会引入 MQ 依赖。Docker 不可用时，Testcontainers 测试会通过 `disabledWithoutDocker` 跳过；本地需要先启动 Docker Desktop。

GitHub Actions：

| Job | 命令 | 说明 |
| --- | --- | --- |
| `unit-tests` | `./mvnw -B test` | 默认轻量测试，不依赖 Docker |
| `integration-tests` | `docker info`、`./mvnw -B -Pintegration-test verify`、RabbitMQ IT 命令、Kafka IT 命令 | Docker 可用时运行 Gateway、RabbitMQ 和 Kafka Testcontainers 集成测试 |

只测试单个模块：

```bash
./mvnw -pl catalog-service test
./mvnw -pl order-service test
./mvnw -pl gateway-service test
```

Native / AOT 手动验证命令：

```bash
./mvnw -pl catalog-service -am package -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
```

`native:compile` 需要本机安装 GraalVM `native-image`。当前仓库不把 native 构建加入默认 CI。

清理构建产物：

```bash
./mvnw clean
```

## Docker 镜像与应用 Compose

先构建 Spring Boot jar：

```bash
./mvnw package -DskipTests
```

单独构建业务服务镜像：

```bash
docker build -t spring3/catalog-service:local ./catalog-service
docker build -t spring3/order-service:local ./order-service
```

也可以由 Compose 统一构建：

```bash
docker compose -f deployment/docker-compose.yml build
```

启动两个业务服务和观测组件：

```bash
docker compose -f deployment/docker-compose.yml up -d
```

查看容器状态和日志：

```bash
docker compose -f deployment/docker-compose.yml ps
docker compose -f deployment/docker-compose.yml logs -f catalog-service
docker compose -f deployment/docker-compose.yml logs -f order-service
docker compose -f deployment/docker-compose.yml logs -f prometheus
```

健康检查和探针：

```bash
curl -fsS http://localhost:8081/actuator/health/readiness
curl -fsS http://localhost:8081/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
curl -fsS http://localhost:8080/actuator/health/liveness
```

验证容器网络内的 `order-service -> catalog-service` 调用：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

`deployment/docker-compose.yml` 通过环境变量把 `DEMO_CLIENTS_CATALOG_BASE_URL` 设置为 `http://catalog-service:8081`，容器内服务间调用不依赖 `host.docker.internal`。

查看 Prometheus targets，预期 `catalog-service` 和 `order-service` 都是 `up`：

```bash
curl -fsS 'http://localhost:9090/api/v1/targets?state=active' \
  | jq -r '.data.activeTargets[] | [.labels.job, .health, .scrapeUrl, (.lastError // "")] | @tsv'
```

查询 `up`：

```bash
curl -fsS 'http://localhost:9090/api/v1/query?query=up' \
  | jq -r '.data.result[] | [.metric.job, .metric.instance, .value[1]] | @tsv'
```

停止并清理容器和网络：

```bash
docker compose -f deployment/docker-compose.yml down
```

排障命令：

```bash
docker compose -f deployment/docker-compose.yml config
docker compose -f deployment/docker-compose.yml ps
docker inspect spring3-app-order --format '{{json .State.Health}}'
docker compose -f deployment/docker-compose.yml logs --tail=200 order-service
```

## Kubernetes 部署示例

当前 Kubernetes 示例不要求真实集群，重点是学习 Deployment、Service、ConfigMap、Secret、readiness/liveness、资源限制、滚动发布和优雅停机。完整说明见 [Kubernetes 部署示例](kubernetes.md)。

先构建镜像：

```bash
./mvnw package -DskipTests
docker build -t spring3/catalog-service:local ./catalog-service
docker build -t spring3/order-service:local ./order-service
```

无真实集群时做离线 schema 校验：

```bash
brew install kubeconform
kubeconform -strict -summary deployment/k8s/*.yaml
```

有真实集群时做 dry-run：

```bash
kubectl apply --dry-run=client -f deployment/k8s
kubectl apply --dry-run=server -f deployment/k8s
```

较新的 `kubectl` 在 `--dry-run=client` 下仍可能连接 API server 做资源 discovery；没有集群时可用 kubeconform 先做离线校验。

应用和查看：

```bash
kubectl apply -f deployment/k8s
kubectl -n spring3 get deploy,svc,pod
kubectl -n spring3 rollout status deploy/catalog-service
kubectl -n spring3 rollout status deploy/order-service
```

端口转发验证：

```bash
kubectl -n spring3 port-forward svc/catalog-service 8081:8081
curl -fsS http://localhost:8081/actuator/health
```

```bash
kubectl -n spring3 port-forward svc/order-service 8080:8080
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

清理：

```bash
kubectl delete -f deployment/k8s
```

Manifest 覆盖内容：

| 对象 | 说明 |
| --- | --- |
| `Namespace` | 固定使用 `spring3` |
| `ConfigMap` | 非敏感环境变量、服务间调用地址、JVM 参数 |
| `Secret` | 空 `SENTRY_DSN` 示例，不提交真实 DSN |
| `Deployment` | 2 副本、滚动发布、资源 requests/limits、`preStop`、graceful shutdown |
| `Service` | ClusterIP，`order-service` 通过 `http://catalog-service:8081` 调用 provider |

探针使用 Actuator：

- readiness：`/actuator/health/readiness`
- liveness：`/actuator/health/liveness`
- startup：`/actuator/health/liveness`

## 自定义 Starter / Autoconfigure

当前项目提供两个演示模块：

| 模块 | 职责 |
| --- | --- |
| `demo-observability-autoconfigure` | 放自动配置代码，包含 `@AutoConfiguration`、`@ConditionalOnClass`、`@ConditionalOnMissingBean`、`@ConfigurationProperties` 和 `AutoConfiguration.imports` |
| `demo-observability-spring-boot-starter` | 只做依赖聚合，依赖 autoconfigure 模块和 `spring-boot-starter-aop`，不写业务代码 |

`catalog-service` 和 `order-service` 引入 starter 后，`@DemoLog` 注解会自动生效，不再需要各服务复制 `LoggingAspect`。

配置项：

```yaml
demo:
  observability:
    demolog:
      enabled: true
      slow-threshold: 500ms
```

关闭自动配置：

```bash
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar \
  --demo.observability.demolog.enabled=false
```

排查自动配置是否生效：

```bash
./mvnw -pl demo-observability-autoconfigure test
```

也可以在业务服务启动时加上 `--debug` 查看 Spring Boot condition evaluation report，搜索 `DemoLogAutoConfiguration`。

## Java 21 虚拟线程

默认 profile 保留传统 `ThreadPoolTaskExecutor`：

| Bean | 默认 profile | `virtual-thread` profile |
| --- | --- | --- |
| `demoTaskExecutor` | `ThreadPoolTaskExecutor`，线程名前缀 `demo-async-` | `SimpleAsyncTaskExecutor` + virtual threads，线程名前缀 `demo-vt-` |
| Web request thread | 平台线程 | `spring.threads.virtual.enabled=true` 后由 Spring Boot 使用虚拟线程 |
| Resilience4j TimeLimiter executor | 固定平台线程池 `catalog-governance-*` | 保持不变，用于演示治理隔离边界 |

启动虚拟线程 profile：

```bash
SPRING_PROFILES_ACTIVE=virtual-thread ./mvnw -pl order-service spring-boot:run
```

先启动 `catalog-service`，再请求订单预览：

```bash
./mvnw -pl catalog-service spring-boot:run

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

观察请求线程：

```bash
curl -u user:user123 'http://localhost:8080/api/orders/thread-probe?delayMs=100'
```

观察 `@Async` 线程：

```bash
curl -u user:user123 'http://localhost:8080/api/orders/thread-probe?async=true&delayMs=100'
```

响应包含 `threadName` 和 `virtual`：

```json
{"mode":"async","threadName":"demo-vt-1","virtual":true,"delayMs":100}
```

日志观察点：

```bash
tail -f order-service/target/run.log | grep -E 'Thread probe|Async notification|Handled order preview'
```

适用场景：

- 适合阻塞 I/O 密集路径，例如等待 HTTP、文件、网络或短暂 sleep 的演示接口。
- 不会让 CPU 密集计算自动变快，CPU 仍然是瓶颈。
- 需要关注 `ThreadLocal`、MDC、第三方 SDK 和同步锁导致的 pinned thread。
- 本项目保留 Resilience4j TimeLimiter 固定线程池，便于对比虚拟线程和治理隔离线程池的边界。

验证命令：

```bash
./mvnw -pl order-service -am -Dtest=OrderVirtualThreadProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## 前台启动服务

分别打开两个终端：

```bash
./mvnw -pl catalog-service spring-boot:run
```

```bash
./mvnw -pl order-service spring-boot:run
```

```bash
./mvnw -pl gateway-service spring-boot:run
```

## 后台启动服务

先打包：

```bash
./mvnw package -DskipTests
```

使用 `screen` 后台启动：

```bash
screen -dmS spring3-catalog zsh -lc 'java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar > catalog-service/target/run.log 2>&1'
screen -dmS spring3-order zsh -lc 'java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > order-service/target/run.log 2>&1'
screen -dmS spring3-gateway zsh -lc 'java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar > gateway-service/target/run.log 2>&1'
```

查看后台会话：

```bash
screen -ls
```

查看日志：

```bash
tail -f catalog-service/target/run.log
tail -f order-service/target/run.log
tail -f gateway-service/target/run.log
```

停止后台服务：

```bash
screen -S spring3-catalog -X quit
screen -S spring3-order -X quit
screen -S spring3-gateway -X quit
```

## 健康检查

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8088/actuator/health
```

Swagger：

- `http://localhost:8081/swagger-ui.html`
- `http://localhost:8080/swagger-ui.html`

## 示例请求

正常调用：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

触发 catalog fallback：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  'http://localhost:8080/api/orders/preview?failCatalog=true'
```

触发参数校验：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"","quantity":0}' \
  http://localhost:8080/api/orders/preview
```

触发管理员权限校验：

```bash
curl -u user:user123 http://localhost:8080/api/orders/admin/stats
curl -u admin:admin123 http://localhost:8080/api/orders/admin/stats
```

## HTTP Client 模式

`order-service` 默认使用 OpenFeign 调用 `catalog-service`。配置位于 `order-service/src/main/resources/application.yml`：

```yaml
demo:
  clients:
    catalog:
      mode: feign
      base-url: http://localhost:8081
      username: user
      password: user123
      connect-timeout: 500ms
      read-timeout: 3s
```

RestClient 模式要求配置固定 `demo.clients.catalog.base-url`；Nacos 服务发现路径仍建议使用默认 Feign 模式。

切换为 RestClient：

```bash
DEMO_CLIENTS_CATALOG_MODE=restclient ./mvnw -pl order-service spring-boot:run
```

或使用 jar 参数：

```bash
java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar \
  --demo.clients.catalog.mode=restclient
```

验证 RestClient 正常调用：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

验证 RestClient fallback：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  'http://localhost:8080/api/orders/preview?failCatalog=true'
```

检查当前模式：

```bash
curl -u admin:admin123 http://localhost:8080/api/orders/admin/stats
```

HTTP client 选型对比：

| Client | 风格 | 适合场景 | 注意点 |
| --- | --- | --- | --- |
| OpenFeign | 声明式接口 | Spring Cloud 微服务、服务发现、负载均衡、fallback 与治理能力集成 | 多一层代理和 Spring Cloud 依赖，复杂问题要理解 Feign、LoadBalancer、CircuitBreaker 各自边界 |
| RestClient | Spring Framework 6 同步 fluent API | MVC 阻塞式服务间调用、简单外部 API、需要精确控制超时/认证/错误处理 | fallback、服务发现、重试等治理能力需要自行组合或接入其他组件 |
| WebClient | 响应式 fluent API | WebFlux、流式响应、高并发非阻塞 IO、Gateway 相关场景 | 在纯 MVC 场景中为同步调用频繁 `.block()` 通常收益不大 |
| `@HttpExchange` | Spring 原生声明式 HTTP Interface | 希望保留接口声明式写法，同时基于 RestClient 或 WebClient 适配 | Spring Cloud 生态能力不如 OpenFeign 完整，治理能力要额外设计 |

## Resilience4j 服务治理

`order-service` 在 catalog 调用边界增加了 `CatalogGovernanceService`。默认正常路径仍调用 `POST /api/orders/preview`，下面参数用于稳定触发治理策略：

| 参数 | 触发策略 | 说明 |
| --- | --- | --- |
| `failCatalog=true` | Retry + CircuitBreaker + fallback | 下游返回 fallback 后，治理层把它视为失败并按配置重试，最终返回明确的降级商品 |
| `slowCatalog=true` | TimeLimiter + fallback | catalog 慢响应会被 `resilience4j.timelimiter.instances.catalog-service.timeout-duration` 截断 |
| `rateLimit=true` | RateLimiter + fallback | 本地演示配置为每个刷新周期只放行 1 次，连续调用第二次会被限流 |
| `bulkhead=true&holdBulkhead=true` | Bulkhead + fallback | 第一个请求持有舱壁，第二个并发请求会触发 bulkhead full |

配置集中在 `order-service/src/main/resources/application.yml`：

```yaml
demo:
  resilience:
    catalog:
      async-pool-size: 4
      bulkhead-hold-duration: 1s

resilience4j:
  retry:
    instances:
      catalog-service:
        max-attempts: 3
        wait-duration: 100ms
  circuitbreaker:
    instances:
      catalog-service:
        sliding-window-size: 5
        minimum-number-of-calls: 2
  timelimiter:
    instances:
      catalog-service:
        timeout-duration: 1s
  ratelimiter:
    instances:
      catalog-rate-limit:
        limit-for-period: 1
        limit-refresh-period: 10s
  bulkhead:
    instances:
      catalog-bulkhead:
        max-concurrent-calls: 1
        max-wait-duration: 0
```

触发失败重试和熔断统计：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  'http://localhost:8080/api/orders/preview?failCatalog=true'
```

触发慢调用超时：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  'http://localhost:8080/api/orders/preview?slowCatalog=true'
```

触发限流：

```bash
for sku in SKU-RATE-1 SKU-RATE-2; do
  curl -s -u user:user123 \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$sku\",\"quantity\":1}" \
    'http://localhost:8080/api/orders/preview?rateLimit=true'
  echo
done
```

触发 Bulkhead full：

```bash
for sku in SKU-BH-1 SKU-BH-2; do
  curl -s -u user:user123 \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$sku\",\"quantity\":1}" \
    'http://localhost:8080/api/orders/preview?bulkhead=true&holdBulkhead=true' &
done
wait
```

直接查看本机指标：

```bash
curl -fsS http://localhost:8080/actuator/prometheus \
  | rg 'resilience4j_(retry|circuitbreaker|timelimiter|ratelimiter|bulkhead)'
```

策略边界：

| 策略 | 适合 | 不适合 |
| --- | --- | --- |
| Retry | 短暂网络抖动、幂等读请求 | 非幂等写请求、确定性业务失败 |
| CircuitBreaker | 下游持续失败时快速失败，保护调用方线程 | 替代限流或容量隔离 |
| TimeLimiter | 给异步调用设置上限，避免请求无限等待 | 替代 HTTP client connect/read timeout |
| RateLimiter | 控制入口或外部 API 调用速率 | 解决慢调用堆积，或跨实例全局限流 |
| Bulkhead | 限制并发，避免某类调用拖垮整个服务 | 替代熔断、重试或线程池容量规划 |

当前配置让 TimeLimiter 的 `1s` 小于 Feign read timeout 的 `3s`，因此 `slowCatalog=true` 会优先演示 Resilience4j 超时。RestClient 模式下如果 `demo.clients.catalog.read-timeout` 设置得更短，则 HTTP client 超时会先触发。

## Sentinel 可选专题

Sentinel 作为阿里系服务治理专题单独隔离：默认 profile 不引入 Sentinel 依赖；只有使用 Maven `-Psentinel` 时才会编译 `order-service/src/sentinel/java` 和 `order-service/src/sentinel-test/java`。运行时还需要设置 Spring profile `sentinel`。

版本基线：

| 组件 | 当前版本 |
| --- | --- |
| Spring Boot | `3.5.14` |
| Spring Cloud | `2025.0.2` |
| Spring Cloud Alibaba | `2025.0.0.0` |
| Sentinel | `1.8.9` |

Spring Cloud Alibaba 2025.0.x 官方版本说明适配 Spring Boot 3.5.x 和 Spring Cloud 2025.0.x，组件表中 `2025.0.0.0` 对应 Sentinel `1.8.9`。Sentinel starter 为 `com.alibaba.cloud:spring-cloud-starter-alibaba-sentinel`。

本地规则通过 `SentinelRuleConfig` 在 `sentinel` profile 启动时加载，不要求启动 Dashboard：

| 资源 | 触发入口 | 规则 |
| --- | --- | --- |
| `order-preview-flow` | `POST /api/orders/preview?sentinelFlow=true` | QPS 限流，默认 `1` |
| `order-preview-hot-sku` | `POST /api/orders/preview?sentinelHotSku=true` | 按第一个参数 `sku` 做热点参数限流，默认 `1` |
| `order-catalog-degrade-probe` | `GET /api/orders/sentinel/degrade-probe?slow=true` | 慢调用比例熔断，默认慢调用阈值 `10ms`，探针延迟 `50ms` |

核心配置在 `order-service/src/main/resources/application-sentinel.yml`：

```yaml
spring:
  cloud:
    sentinel:
      enabled: true
      eager: true
      transport:
        dashboard: ${SENTINEL_DASHBOARD:localhost:8858}
        port: ${SENTINEL_TRANSPORT_PORT:8719}

demo:
  sentinel:
    flow:
      qps: 1
    hot-sku:
      qps: 1
      duration: 1s
    degrade:
      slow-threshold: 10ms
      slow-ratio-threshold: 0.5
      minimum-request-amount: 2
      stat-interval: 1s
      time-window: 5s
      probe-delay: 50ms
```

启动：

```bash
./mvnw -Psentinel -pl order-service -am package -DskipTests
```

```bash
./mvnw -pl catalog-service spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=sentinel ./mvnw -Psentinel -pl order-service spring-boot:run
```

正常业务路径不加 Sentinel 演示开关，仍然走现有 OpenFeign + Resilience4j：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":1}' \
  http://localhost:8080/api/orders/preview
```

触发 Sentinel QPS 限流，第二个请求会返回 `429` ProblemDetail，`strategy=FLOW`：

```bash
for sku in SKU-SENT-FLOW-1 SKU-SENT-FLOW-2; do
  curl -s -u user:user123 \
    -H 'Content-Type: application/json' \
    -d "{\"sku\":\"$sku\",\"quantity\":1}" \
    'http://localhost:8080/api/orders/preview?sentinelFlow=true'
  echo
done
```

触发热点参数限流，同一个 `sku` 连续请求时第二个请求会返回 `strategy=HOT_PARAM`：

```bash
for i in 1 2; do
  curl -s -u user:user123 \
    -H 'Content-Type: application/json' \
    -d '{"sku":"SKU-SENT-HOT","quantity":1}' \
    'http://localhost:8080/api/orders/preview?sentinelHotSku=true'
  echo
done
```

触发 Sentinel 慢调用熔断，连续慢调用后第三次请求会返回 `strategy=DEGRADE`：

```bash
for i in 1 2 3; do
  curl -s -u user:user123 \
    'http://localhost:8080/api/orders/sentinel/degrade-probe?slow=true'
  echo
done
```

自动化验证：

```bash
./mvnw -Psentinel -pl order-service -am -Dtest=OrderSentinelProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Sentinel 与 Resilience4j 对比：

| 维度 | Sentinel | Resilience4j |
| --- | --- | --- |
| 定位 | 流量治理、热点参数、控制台规则管理、阿里系微服务常见选型 | 应用内轻量治理库，和 Spring Cloud CircuitBreaker 集成自然 |
| 限流 | QPS、线程数、热点参数、集群限流能力更完整 | RateLimiter 更适合进程内固定速率控制 |
| 熔断 | 慢调用比例、异常比例、异常数，规则可由控制台或数据源管理 | CircuitBreaker 配置清晰，配合 Retry/TimeLimiter/Bulkhead 组合 |
| 依赖边界 | 本项目放在 `-Psentinel` + `sentinel` profile，避免默认运行复杂化 | 当前默认治理方案，业务路径长期保留 |
| 面试重点 | Dashboard/规则下发、热点参数、流控效果、降级返回、集群限流 | 状态机、滑动窗口、fallback、重试顺序、线程隔离和指标 |

排查点：

- 同时使用 Maven `-Psentinel` 和 Spring `SPRING_PROFILES_ACTIVE=sentinel`，缺一不可。
- Sentinel 本地日志目录可通过 `SENTINEL_LOG_DIR` 设置，默认写入 `${user.home}/logs/csp`。
- 本项目的 Sentinel 规则是本地内存规则，重启后重新加载；生产环境通常接 Dashboard、Nacos 或 Apollo 等数据源。
- 当前熔断探针用慢调用比例复现；异常比例和异常数规则可用同一资源模型扩展。

## Kafka 消息队列

Kafka 是可选消息队列专题，默认 profile 不引入 Kafka 运行依赖。只有同时使用 Maven `-Pkafka` 和 Spring `SPRING_PROFILES_ACTIVE=kafka` 时，才会编译并启用 `order-service/src/kafka/java` 下的发布者、消费者和 topic 配置。

当前示例围绕 `OrderPreviewCreatedEvent`：

| 能力 | 当前实现 |
| --- | --- |
| 生产 | `KafkaOrderPreviewEventPublisher` 监听订单预览事件，通过 `KafkaTemplate` 发布 JSON event |
| 消费 | `KafkaOrderPreviewConsumer` 使用 `@KafkaListener` 消费订单预览 topic |
| 分区顺序 | 使用 `orderId` / `partitionKey` 作为 message key，同一 key 进入同一 partition |
| Offset | 关闭 auto commit，listener 使用 manual ack |
| 幂等 | 使用 `eventId` 和内存 `ProcessedKafkaEventStore`，重复消息会被跳过并 ack |
| 重试/DLT | `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`，重试耗尽后进入 DLT |
| 指标 | `orders.preview.kafka.published.total`、`processed.total`、`duplicates.total`、`failed.total`、`send.failed.total` |

本地 Kafka：

```bash
docker compose -f platform/kafka/docker-compose.yml config
docker compose -f platform/kafka/docker-compose.yml up -d
docker compose -f platform/kafka/docker-compose.yml ps
```

Kafka UI：

```text
http://localhost:8089
```

启动业务服务：

```bash
./mvnw -pl catalog-service spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=kafka ./mvnw -Pkafka -pl order-service spring-boot:run
```

正常生产和消费：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: kafka-demo-request' \
  -H 'traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01' \
  -d '{"sku":"SKU-KAFKA-OK","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

消费失败、重试和死信：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-KAFKA-FAIL","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

查看指标：

```bash
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.published.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.processed.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.kafka.failed.total
```

自动化验证：

```bash
./mvnw -Pkafka -pl order-service -am test -DskipTests
./mvnw -Pkafka,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderKafkaProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

停止 Kafka：

```bash
docker compose -f platform/kafka/docker-compose.yml down
```

完整说明见 [Kafka 使用与面试专题](kafka-playbook.md)。

## RabbitMQ 消息队列

RabbitMQ 是可选消息队列专题，默认 profile 不引入 AMQP 运行依赖。只有同时使用 Maven `-Prabbitmq` 和 Spring `SPRING_PROFILES_ACTIVE=rabbitmq` 时，才会编译并启用 `order-service/src/rabbitmq/java` 下的发布者、消费者和队列配置。

当前示例围绕 `OrderPreviewCreatedEvent`：

| 能力 | 当前实现 |
| --- | --- |
| 生产 | `RabbitOrderPreviewEventPublisher` 监听订单预览事件，通过 `RabbitTemplate` 发布 JSON 消息 |
| 消费 | `RabbitOrderPreviewConsumer` 使用 `@RabbitListener` 消费订单预览队列 |
| 幂等 | 使用 `eventId`，当前示例以 `orderId` 作为事件 ID，重复消息会被跳过 |
| 重试 | `spring.rabbitmq.listener.simple.retry.*` 配置消费失败重试 |
| 死信 | 主队列绑定 `x-dead-letter-exchange` 和 `x-dead-letter-routing-key`，重试耗尽后进入 DLQ |
| 指标 | `orders.preview.rabbitmq.published.total`、`processed.total`、`duplicates.total`、`failed.total` |

本地 RabbitMQ：

```bash
docker compose -f platform/rabbitmq/docker-compose.yml config
docker compose -f platform/rabbitmq/docker-compose.yml up -d
docker compose -f platform/rabbitmq/docker-compose.yml ps
```

管理界面：

```text
http://localhost:15672
guest / guest
```

启动业务服务：

```bash
./mvnw -pl catalog-service spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=rabbitmq ./mvnw -Prabbitmq -pl order-service spring-boot:run
```

正常生产和消费：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-RABBITMQ-OK","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

消费失败、重试和死信：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-RABBITMQ-FAIL","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

查看队列和日志：

```bash
docker compose -f platform/rabbitmq/docker-compose.yml logs -f rabbitmq
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.rabbitmq.published.total
curl -u user:user123 http://localhost:8080/actuator/metrics/orders.preview.rabbitmq.failed.total
```

自动化验证：

```bash
./mvnw -Prabbitmq -pl order-service -am test -DskipTests
./mvnw -Prabbitmq,integration-test -pl order-service -am -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=OrderRabbitMqProfileIT -Dfailsafe.failIfNoSpecifiedTests=false verify
```

停止 RabbitMQ：

```bash
docker compose -f platform/rabbitmq/docker-compose.yml down
```

Kafka、RabbitMQ、RocketMQ 面试对比：

| 维度 | RabbitMQ | Kafka | RocketMQ |
| --- | --- | --- | --- |
| 核心模型 | exchange、queue、binding、routing key | topic、partition、offset、consumer group | topic、tag、consumer group、queue |
| 常见场景 | 业务异步解耦、复杂路由、重试/DLQ | 高吞吐事件流、日志流、数据管道 | 事务消息、顺序消息、延迟消息、国内业务中台 |
| 顺序语义 | 单队列内有序，扩展并发后需业务设计 | partition 内有序，key 决定分区 | 支持顺序消息，需要选择队列和消费模型 |
| 重试/DLQ | 队列参数和 listener retry 组合清晰 | 通常用 retry topic、DLT 或框架封装 | Broker 原生重试和死信语义更强 |
| 面试重点 | ack/nack、DLX、publisher confirm、幂等消费 | offset 提交、rebalance、consumer lag、幂等 producer | tag 过滤、延迟级别、事务半消息、顺序消费 |

## Native Image / AOT

Native Image / AOT 是 Spring Boot 3 专题内容，当前只做手动学习和验证，不进入默认构建、默认 CI 或默认发布路径。完整说明见 [Native Image / AOT 专题](native-aot.md)。

当前已验证：

| 项目 | 结果 |
| --- | --- |
| `catalog-service` 普通 jar 构建 | 通过 |
| `catalog-service` `spring-boot:process-aot` | 通过 |
| `catalog-service` `native:compile` | 已尝试，本机缺少 GraalVM `native-image`，未生成 binary |

检查环境：

```bash
java -version
command -v native-image
native-image --version
```

确认 Spring Boot parent 提供的 `native` profile：

```bash
./mvnw help:active-profiles -Pnative -pl catalog-service -am
```

执行 AOT 处理：

```bash
./mvnw -pl catalog-service -am package -DskipTests
./mvnw -Pnative -pl catalog-service spring-boot:process-aot -DskipTests
```

不要直接把 `-am` 和 `spring-boot:process-aot` 组合使用，因为 `common` 和 starter 模块不是可启动应用，没有 main class。

本机 GraalVM native binary 构建：

```bash
./mvnw -Pnative -pl catalog-service native:compile -DskipTests
ZIPKIN_TRACING_ENABLED=false catalog-service/target/catalog-service --server.port=8081
curl -fsS http://localhost:8081/actuator/health
```

Docker buildpacks native 镜像构建：

```bash
./mvnw -Pnative -pl catalog-service spring-boot:build-image \
  -DskipTests \
  -Dspring-boot.build-image.imageName=spring3/catalog-service-native:local

docker run --rm -p 8081:8081 \
  -e ZIPKIN_TRACING_ENABLED=false \
  spring3/catalog-service-native:local
```

排查重点：

- `native-image` 不存在时，先安装 GraalVM JDK 21 并设置 `JAVA_HOME` 或 `GRAALVM_HOME`。
- SpringDoc、Sentry、Feign、Gateway、AOP、Jackson 和动态代理都需要 native 后分别验证，不要默认认为 JVM 运行成功就等于 native 可用。
- 第一次 native 构建耗时明显长于普通 jar 构建，学习项目先从 `catalog-service` 做最小闭环。

## OAuth2 Resource Server / JWT

默认 profile 继续使用 Basic Auth。`jwt` profile 会启用 Spring Security OAuth2 Resource Server，支持 `Authorization: Bearer <token>`，并把 JWT 中的 `roles` claim 映射为 `ROLE_USER`、`ROLE_ADMIN`。`scope` claim 会保留为 Spring Security 默认的 `SCOPE_*` 权限。

本学习项目在 `jwt` profile 下仍保留 Basic Auth，作为内部服务调用凭证；也就是说 `order-service -> catalog-service` 继续使用现有 Basic 认证。生产环境可把这部分替换为 OAuth2 `client_credentials` service token、mTLS 或网关内网鉴权。

启动 JWT 模式：

```bash
SPRING_PROFILES_ACTIVE=jwt ./mvnw -pl catalog-service spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=jwt ./mvnw -pl order-service spring-boot:run
```

生成本地测试 token：

```bash
TOKEN=$(ROLE=ADMIN python3 - <<'PY'
import base64
import hashlib
import hmac
import json
import os
import time

secret = os.environ.get(
    "DEMO_SECURITY_JWT_SECRET",
    "spring3-local-dev-secret-key-32-bytes-minimum",
).encode()
role = os.environ.get("ROLE", "USER").upper()
roles = ["USER"]
if role == "ADMIN":
    roles.append("ADMIN")

def b64url(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()

now = int(time.time())
header = {"alg": "HS256", "typ": "JWT"}
payload = {
    "sub": role.lower(),
    "iss": "spring3-local",
    "iat": now,
    "exp": now + 3600,
    "roles": roles,
    "scope": "orders:read catalog:read",
}
signing_input = ".".join([
    b64url(json.dumps(header, separators=(",", ":")).encode()),
    b64url(json.dumps(payload, separators=(",", ":")).encode()),
])
signature = hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()
print(signing_input + "." + b64url(signature))
PY
)
```

普通用户 token：

```bash
TOKEN=$(ROLE=USER python3 - <<'PY'
import base64, hashlib, hmac, json, os, time
secret = os.environ.get("DEMO_SECURITY_JWT_SECRET", "spring3-local-dev-secret-key-32-bytes-minimum").encode()
now = int(time.time())
def b64url(data): return base64.urlsafe_b64encode(data).rstrip(b"=").decode()
header = {"alg": "HS256", "typ": "JWT"}
payload = {"sub": "user", "iss": "spring3-local", "iat": now, "exp": now + 3600, "roles": ["USER"], "scope": "orders:read catalog:read"}
signing_input = ".".join([b64url(json.dumps(header, separators=(",", ":")).encode()), b64url(json.dumps(payload, separators=(",", ":")).encode())])
print(signing_input + "." + b64url(hmac.new(secret, signing_input.encode(), hashlib.sha256).digest()))
PY
)
```

调用 order 业务接口：

```bash
curl -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

验证 admin 权限：

```bash
curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/orders/admin/stats
```

普通用户 token 预期返回 `403`；管理员 token 预期返回 `200`。

验证无 token 和错误 token：

```bash
curl -i http://localhost:8080/api/orders/admin/stats
curl -i -H 'Authorization: Bearer invalid.token.value' \
  http://localhost:8080/api/orders/admin/stats
```

以上本地 HS256 密钥只用于学习演示。真实环境应使用授权服务器的 `issuer-uri` 或 `jwk-set-uri`，不要把生产私钥、长期有效 token 或真实密钥提交到仓库。

## Spring Cloud Gateway

`gateway-service` 是统一入口，默认端口 `8088`。默认 profile 使用静态路由：

| 网关路径 | 下游 |
| --- | --- |
| `/catalog/**` | `http://localhost:8081/**` |
| `/orders/**` | `http://localhost:8080/**` |

网关不承载业务鉴权逻辑，默认透传 `Authorization` header；服务侧仍负责 Basic Auth 和 `@PreAuthorize`。网关会补充：

- `X-Request-Id`：请求链路标识，同时写入响应头和下游请求头。
- `X-Gateway-Auth-Type`：识别 `Basic`、`Bearer` 或其他认证头，便于后续扩展 JWT。
- `X-RateLimit-Limit`、`X-RateLimit-Remaining`：本地学习版限流响应头。

启动顺序：

```bash
./mvnw -pl catalog-service spring-boot:run
./mvnw -pl order-service spring-boot:run
./mvnw -pl gateway-service spring-boot:run
```

验证 catalog 路由：

```bash
curl -u user:user123 \
  -H 'X-Request-Id: demo-gateway-catalog-1' \
  http://localhost:8088/catalog/api/catalog/products/SKU-1001
```

验证 order 路由：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-gateway-order-1' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview
```

验证未认证请求：

```bash
curl -i http://localhost:8088/catalog/api/catalog/products/SKU-1001
```

预期由下游服务返回 `401`。如果下游未启动，网关会返回 fallback `503`。

验证 fallback：

```bash
screen -S spring3-order -X quit

curl -i -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview
```

验证网关 Actuator：

```bash
curl -fsS http://localhost:8088/actuator/health
curl -fsS http://localhost:8088/actuator/prometheus | grep jvm
```

限流配置位于 `gateway-service/src/main/resources/application.yml`：

```yaml
demo:
  gateway:
    rate-limit:
      enabled: true
      requests-per-window: 60
      window: 1m
```

## Prometheus + Grafana + Zipkin

启动观测栈：

```bash
docker compose -f observability/docker-compose.yml up -d
```

查看状态：

```bash
docker compose -f observability/docker-compose.yml ps
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3000/api/health
curl -fsS http://localhost:9411/health
```

查看 Prometheus 抓取目标：

```bash
curl -fsS 'http://localhost:9090/api/v1/targets?state=active' \
  | jq -r '.data.activeTargets[] | [.labels.job, .health, .scrapeUrl, (.lastError // "")] | @tsv'
```

查询服务 `up` 状态：

```bash
curl -fsS 'http://localhost:9090/api/v1/query?query=up' \
  | jq -r '.data.result[] | [.metric.job, .metric.instance, .value[1]] | @tsv'
```

查询业务指标：

```bash
curl -fsS 'http://localhost:9090/api/v1/query?query=orders_preview_total' \
  | jq -r '.data.result[] | [.metric.application, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=orders_preview_fallback_total' \
  | jq -r '.data.result[] | [.metric.application, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=catalog_product_simulated_failure_total' \
  | jq -r '.data.result[] | [.metric.application, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=resilience4j_retry_calls_total' \
  | jq -r '.data.result[] | [.metric.name, .metric.kind, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=resilience4j_circuitbreaker_calls_seconds_count' \
  | jq -r '.data.result[] | [.metric.name, .metric.kind, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=resilience4j_timelimiter_calls_total' \
  | jq -r '.data.result[] | [.metric.name, .metric.kind, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=resilience4j_ratelimiter_available_permissions' \
  | jq -r '.data.result[] | [.metric.name, .value[1]] | @tsv'

curl -fsS 'http://localhost:9090/api/v1/query?query=resilience4j_bulkhead_available_concurrent_calls' \
  | jq -r '.data.result[] | [.metric.name, .value[1]] | @tsv'
```

查看 Docker 日志：

```bash
docker compose -f observability/docker-compose.yml logs -f prometheus
docker compose -f observability/docker-compose.yml logs -f grafana
docker compose -f observability/docker-compose.yml logs -f zipkin
```

停止观测栈：

```bash
docker compose -f observability/docker-compose.yml down
```

访问地址：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Zipkin UI: `http://localhost:9411/zipkin`

## Micrometer Tracing + Zipkin

三个服务默认启用 Micrometer Tracing，采样率为 `1.0`，日志相关 ID 格式为 `[application,traceId,spanId]`。Zipkin 没启动时服务仍可启动；不需要 trace 上报时可以设置：

```bash
export ZIPKIN_TRACING_ENABLED=false
```

启动完整链路：

```bash
docker compose -f observability/docker-compose.yml up -d
./mvnw package -DskipTests

screen -dmS spring3-catalog zsh -lc 'java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar > catalog-service/target/run.log 2>&1'
screen -dmS spring3-order zsh -lc 'java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > order-service/target/run.log 2>&1'
screen -dmS spring3-gateway zsh -lc 'java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar > gateway-service/target/run.log 2>&1'
```

发起带固定 W3C traceId 的订单预览请求：

```bash
TRACE_ID=4bf92f3577b34da6a3ce929d0e0e4736

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -H "traceparent: 00-${TRACE_ID}-00f067aa0ba902b7-01" \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview

sleep 5
```

查询 Zipkin trace：

```bash
curl -fsS "http://localhost:9411/api/v2/trace/${TRACE_ID}" \
  | jq -r '.[] | [.traceId, .name, (.localEndpoint.serviceName // "-"), (.remoteEndpoint.serviceName // "-")] | @tsv'
```

检查日志中的同一个 traceId：

```bash
rg "$TRACE_ID" gateway-service/target/run.log order-service/target/run.log catalog-service/target/run.log
```

核心代码位置：

- `catalog-service/pom.xml`、`order-service/pom.xml`、`gateway-service/pom.xml`：Micrometer Tracing + Zipkin exporter 依赖。
- `order-service/src/main/java/com/taoking/spring3/order/config/FeignConfig.java`：Feign 出站请求注入当前 trace context。
- `order-service/src/test/java/com/taoking/spring3/order/web/OrderControllerTest.java`：验证 `traceparent` 传播到 catalog client。

## 结构化日志

`json-logging` profile 使用 Spring Boot 3.5 内建 structured logging，不额外引入 logback encoder。默认 profile 仍保留普通文本日志；只有启用 `SPRING_PROFILES_ACTIVE=json-logging` 时才输出 JSON 到 console。

配置文件：

| 服务 | 配置 |
| --- | --- |
| `catalog-service` | `catalog-service/src/main/resources/application-json-logging.yml` |
| `order-service` | `order-service/src/main/resources/application-json-logging.yml` |
| `gateway-service` | `gateway-service/src/main/resources/application-json-logging.yml` |

核心配置：

```yaml
logging:
  structured:
    format:
      console: logstash
    json:
      context:
        include: true
      add:
        application: ${spring.application.name}

demo:
  observability:
    http-logging:
      enabled: true
      request-id-header: X-Request-Id
```

启动示例：

```bash
SPRING_PROFILES_ACTIVE=json-logging ./mvnw -pl catalog-service spring-boot:run
```

```bash
SPRING_PROFILES_ACTIVE=json-logging ./mvnw -pl order-service spring-boot:run
```

发起请求：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":1}' \
  http://localhost:8080/api/orders/preview
```

查看 JSON 请求日志：

```bash
SPRING_PROFILES_ACTIVE=json-logging ./mvnw -pl order-service spring-boot:run 2>&1 \
  | jq -R 'fromjson? | select(.message=="http request completed")'
```

请求日志字段：

| 字段 | 说明 |
| --- | --- |
| `application` | `spring.application.name` |
| `requestId` | 来自 `X-Request-Id`，没有则自动生成并写回响应头 |
| `traceId` / `spanId` | Micrometer Tracing 写入 MDC 后由 structured logging 输出 |
| `event` | `http.request` 或 `gateway.request` |
| `method` / `path` | HTTP 方法和路径，不记录 query string 和请求体 |
| `status` / `elapsedMs` | 响应状态码和耗时 |
| `authScheme` | 只记录 `Basic`、`Bearer` 等认证类型，不记录凭证原文 |

脱敏规则：

- 不记录完整请求体。
- 不记录 `Authorization` 原文，只记录认证 scheme。
- 不记录 password、token、secret 等 query 参数；当前请求日志只记录 path，不记录 query string。
- JSON 日志会包含 MDC 中的 `traceId`、`spanId`、`requestId`，便于和 Zipkin、Prometheus 告警、错误响应关联。

自动化验证：

```bash
./mvnw -pl order-service -am -Dtest=OrderJsonLoggingProfileTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## API 治理

当前项目用少量代码演示资深面试常见的接口治理点：稳定错误码、请求关联 ID、API versioning、接口废弃策略和 OpenAPI 分组。

### 统一错误响应

两个 Servlet 服务的 `ProblemDetail` 都会扩展以下字段：

| 字段 | 说明 |
| --- | --- |
| `errorCode` | 稳定错误码，不随错误文案变化 |
| `requestId` | 优先使用入站 `X-Request-Id`，没有则生成 UUID，并写回响应头 |
| `timestamp` | UTC 时间戳，用于排障对齐日志 |
| `path` | 当前请求路径 |

当前错误码示例：

| 错误码 | 场景 |
| --- | --- |
| `ORDER_VALIDATION_FAILED` | 订单请求参数校验失败 |
| `ORDER_SENTINEL_BLOCKED` | Sentinel 流控或熔断拦截 |
| `CATALOG_PRODUCT_NOT_FOUND` | 商品不存在 |
| `CATALOG_VALIDATION_FAILED` | Catalog 请求参数校验失败 |
| `CATALOG_SIMULATED_FAILURE` | Catalog 演示异常 |
| `SECURITY_ACCESS_DENIED` | 已认证但权限不足 |
| `SYSTEM_INTERNAL_ERROR` | 未预期服务端异常 |

验证错误码和 requestId：

```bash
curl -i -u user:user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Request-Id: demo-error-1' \
  -d '{"sku":"","quantity":0}' \
  http://localhost:8080/api/orders/preview
```

预期响应头包含 `X-Request-Id: demo-error-1`，响应体包含 `errorCode=ORDER_VALIDATION_FAILED`、`requestId=demo-error-1` 和 `timestamp`。

### 版本路由

旧接口仍可用：

```bash
curl -i -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":1}' \
  http://localhost:8080/api/orders/preview
```

旧接口会返回以下废弃提示：

| Header | 值 |
| --- | --- |
| `Deprecation` | `true` |
| `Sunset` | `Thu, 31 Dec 2026 23:59:59 GMT` |
| `Link` | `</api/v1/orders/preview>; rel="successor-version"` |
| `X-API-Deprecated-Reason` | 迁移到 `/api/v1/orders/preview` 或 `/api/v2/orders/preview` |

推荐新调用方使用 v1：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":1}' \
  http://localhost:8080/api/v1/orders/preview
```

v2 演示轻量响应结构变化，不复制业务逻辑：

```bash
curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":1}' \
  http://localhost:8080/api/v2/orders/preview
```

v2 响应把原订单预览放在 `data` 字段下，并增加 `apiVersion=v2` 和 `links.previous`。

兼容策略：

- 新增字段保持向后兼容，优先加在 v1 响应里。
- 破坏性响应结构变化放到 v2。
- 旧路径进入废弃期后保留明确的 `Deprecation`、`Sunset` 和 successor link。
- 错误码是客户端契约，不能因为错误文案调整而变更。

### OpenAPI 分组

订单服务：

| 分组 | 地址 |
| --- | --- |
| 默认 | `http://localhost:8080/v3/api-docs` |
| `orders-v1` | `http://localhost:8080/v3/api-docs/orders-v1` |
| `orders-v2` | `http://localhost:8080/v3/api-docs/orders-v2` |
| `orders-ops` | `http://localhost:8080/v3/api-docs/orders-ops` |

Catalog 服务：

| 分组 | 地址 |
| --- | --- |
| 默认 | `http://localhost:8081/v3/api-docs` |
| `catalog-public` | `http://localhost:8081/v3/api-docs/catalog-public` |
| `catalog-admin` | `http://localhost:8081/v3/api-docs/catalog-admin` |

Swagger UI：

```bash
open http://localhost:8080/swagger-ui.html
open http://localhost:8081/swagger-ui.html
```

自动化验证：

```bash
./mvnw -pl catalog-service,order-service -am -Dtest=CatalogControllerTest,OrderControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

## Spring Cloud Contract

当前项目把 Spring Cloud Contract 作为独立 `contract-test` Maven profile，不进入默认 `./mvnw test`，避免日常测试额外生成代码和本地 stub 制品。

覆盖范围：

| 角色 | 模块 | 内容 |
| --- | --- | --- |
| Provider | `catalog-service` | `GET /api/catalog/products/{sku}` 契约，覆盖成功、商品不存在、模拟失败 |
| Consumer | `order-service` | 使用 Stub Runner 从本地 Maven 仓库加载 `catalog-service` 的 `stubs` jar，验证订单预览能处理正常响应和下游错误 fallback |

Provider 契约文件：

```text
catalog-service/src/contract-test/resources/contracts/catalog/
```

Provider 基类：

```text
catalog-service/src/contract-test/java/com/taoking/spring3/catalog/contract/CatalogContractBase.java
```

单独运行 provider 契约测试：

```bash
./mvnw -Pcontract-test -pl catalog-service -am test
```

生成并安装本地 stubs jar：

```bash
./mvnw -Pcontract-test -pl catalog-service -am install
```

生成物会安装到本机 Maven 仓库：

```text
~/.m2/repository/com/taoking/spring3/catalog-service/0.0.1-SNAPSHOT/catalog-service-0.0.1-SNAPSHOT-stubs.jar
```

运行 consumer 契约测试：

```bash
./mvnw -Pcontract-test -pl order-service -am -Dtest=OrderCatalogContractStubTest -Dsurefire.failIfNoSpecifiedTests=false test
```

完整本地契约验证流程：

```bash
./mvnw -Pcontract-test -pl catalog-service -am clean install
./mvnw -Pcontract-test -pl order-service -am -Dtest=OrderCatalogContractStubTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Spring Cloud Contract 与 MockWebServer 的区别：

| 对比项 | Spring Cloud Contract | MockWebServer |
| --- | --- | --- |
| 关注点 | provider 和 consumer 共享 HTTP 契约，provider 破坏响应字段时测试失败 | consumer 测试内手写 mock 响应，主要验证客户端逻辑 |
| 维护方式 | 契约由 provider 维护并生成 stubs，consumer 使用同一份 stubs | 每个 consumer 测试各自维护 mock 响应 |
| 适用场景 | 多服务协作、接口兼容性、防止 provider breaking change | 单服务内快速验证超时、fallback、请求头、序列化 |
| 当前项目定位 | 验证 `catalog-service` 的 API 契约和 `order-service` 对契约响应的处理 | 保留在 Feign/RestClient 测试中验证 client 细节 |

当前没有搭建远程契约仓库。后续如果接入企业制品库，可以让 provider 在 CI 中发布 `stubs` classifier，consumer CI 再以固定版本或版本范围拉取 stubs 做契约回归。

## Nacos

Nacos 是可选专题，默认 profile 不依赖 Nacos。只有同时使用 Maven `-Pnacos` 和 Spring `SPRING_PROFILES_ACTIVE=nacos` 时才启用 Nacos。

校验配置：

```bash
docker compose -f platform/nacos/docker-compose.yml config
```

启动：

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

curl -fsS -X POST 'http://127.0.0.1:8848/nacos/v1/cs/configs' \
  --data-urlencode 'dataId=gateway-service.yml' \
  --data-urlencode 'group=DEFAULT_GROUP' \
  --data-urlencode 'content=demo:
  gateway:
    rate-limit:
      requests-per-window: 120'
```

打包：

```bash
./mvnw -Pnacos package -DskipTests
```

前台启动：

```bash
SPRING_PROFILES_ACTIVE=nacos java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar
```

```bash
SPRING_PROFILES_ACTIVE=nacos java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar
```

```bash
SPRING_PROFILES_ACTIVE=nacos java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar
```

后台启动：

```bash
screen -dmS spring3-nacos-catalog zsh -lc 'SPRING_PROFILES_ACTIVE=nacos java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar > catalog-service/target/nacos-run.log 2>&1'
screen -dmS spring3-nacos-order zsh -lc 'SPRING_PROFILES_ACTIVE=nacos java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > order-service/target/nacos-run.log 2>&1'
screen -dmS spring3-nacos-gateway zsh -lc 'SPRING_PROFILES_ACTIVE=nacos java -jar gateway-service/target/gateway-service-0.0.1-SNAPSHOT.jar > gateway-service/target/nacos-run.log 2>&1'
```

查看状态和日志：

```bash
docker compose -f platform/nacos/docker-compose.yml ps
docker logs -f spring3-nacos
tail -f catalog-service/target/nacos-run.log
tail -f order-service/target/nacos-run.log
tail -f gateway-service/target/nacos-run.log
```

验证注册发现：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=catalog-service'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=order-service'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=gateway-service'
```

验证配置读取和服务名调用：

```bash
curl -u admin:admin123 http://localhost:8080/api/orders/admin/stats
curl -u admin:admin123 http://localhost:8081/api/catalog/admin/stats

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8088/orders/api/orders/preview
```

停止：

```bash
screen -S spring3-nacos-catalog -X quit
screen -S spring3-nacos-order -X quit
screen -S spring3-nacos-gateway -X quit
docker compose -f platform/nacos/docker-compose.yml down
```

访问地址：

- 控制台：`http://localhost:8847`
- 客户端 API：`http://localhost:8848`

## Sentry

启动时设置真实 DSN：

```bash
export SENTRY_DSN='你的 Sentry DSN'
export APP_ENV='local'
./mvnw -pl order-service spring-boot:run
```

触发验证异常：

```bash
curl -u admin:admin123 -X POST http://localhost:8080/api/orders/admin/sentry-error
```

未设置 `SENTRY_DSN` 时，应用正常启动，但不会真实上报事件。

## 端口占用排查

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:8088 -sTCP:LISTEN
lsof -nP -iTCP:9090 -sTCP:LISTEN
lsof -nP -iTCP:3000 -sTCP:LISTEN
lsof -nP -iTCP:9411 -sTCP:LISTEN
lsof -nP -iTCP:5672 -sTCP:LISTEN
lsof -nP -iTCP:15672 -sTCP:LISTEN
lsof -nP -iTCP:8848 -sTCP:LISTEN
```

## 常用收尾命令

停止 Spring 服务：

```bash
screen -S spring3-catalog -X quit
screen -S spring3-order -X quit
screen -S spring3-gateway -X quit
```

停止 Docker 服务：

```bash
docker compose -f deployment/docker-compose.yml down
docker compose -f observability/docker-compose.yml down
docker compose -f platform/nacos/docker-compose.yml down
docker compose -f platform/rabbitmq/docker-compose.yml down
```

查看工作区变更：

```bash
git status --short
```
