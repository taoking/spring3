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
| Nacos 控制台 | `localhost:8847` |
| Nacos 客户端 API | `localhost:8848` |

默认账号：

| 场景 | 用户名 | 密码 |
| --- | --- | --- |
| 业务普通用户 | `user` | `user123` |
| 业务管理员 | `admin` | `admin123` |
| Grafana | `admin` | `admin` |

主要配置文件：

| 文件 | 说明 |
| --- | --- |
| `pom.xml` | 父工程、版本、依赖管理 |
| `catalog-service/src/main/resources/application.yml` | 商品服务端口、商品样例、Actuator、Sentry |
| `order-service/src/main/resources/application.yml` | 订单服务端口、HTTP client 模式、Feign、RestClient、缓存、Resilience4j、Actuator、Sentry |
| `gateway-service/src/main/resources/application.yml` | 网关端口、静态路由、本地限流、fallback、Actuator |
| `gateway-service/src/main/resources/application-nacos.yml` | 网关 Nacos 服务发现路由 |
| `observability/docker-compose.yml` | Prometheus + Grafana + Zipkin |
| `observability/prometheus/prometheus.yml` | Prometheus 抓取目标 |
| `platform/nacos/docker-compose.yml` | 本地 Nacos 3.0.3 |

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

## 构建与测试

```bash
./mvnw test
./mvnw package -DskipTests
```

只测试单个模块：

```bash
./mvnw -pl catalog-service test
./mvnw -pl order-service test
./mvnw -pl gateway-service test
```

清理构建产物：

```bash
./mvnw clean
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

触发 Feign fallback：

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
      read-timeout: 800ms
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
docker compose -f observability/docker-compose.yml down
docker compose -f platform/nacos/docker-compose.yml down
```

查看工作区变更：

```bash
git status --short
```
