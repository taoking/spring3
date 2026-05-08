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
| Prometheus | `localhost:9090` |
| Grafana | `localhost:3000` |
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
| `order-service/src/main/resources/application.yml` | 订单服务端口、Feign、缓存、Resilience4j、Actuator、Sentry |
| `observability/docker-compose.yml` | Prometheus + Grafana |
| `observability/prometheus/prometheus.yml` | Prometheus 抓取目标 |
| `platform/nacos/docker-compose.yml` | 本地 Nacos 3.0.3 |

常用环境变量：

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| `SENTRY_DSN` | Sentry DSN，未设置时不真实上报 | 空 |
| `APP_ENV` | Sentry environment | `local` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | 默认 profile |

## 构建与测试

```bash
./mvnw test
./mvnw package -DskipTests
```

只测试单个模块：

```bash
./mvnw -pl catalog-service test
./mvnw -pl order-service test
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

## 后台启动服务

先打包：

```bash
./mvnw package -DskipTests
```

使用 `screen` 后台启动：

```bash
screen -dmS spring3-catalog zsh -lc 'java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar > catalog-service/target/run.log 2>&1'
screen -dmS spring3-order zsh -lc 'java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > order-service/target/run.log 2>&1'
```

查看后台会话：

```bash
screen -ls
```

查看日志：

```bash
tail -f catalog-service/target/run.log
tail -f order-service/target/run.log
```

停止后台服务：

```bash
screen -S spring3-catalog -X quit
screen -S spring3-order -X quit
```

## 健康检查

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/health
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
curl -u user:user123 http://localhost:8080/api/orders/admin/ping
curl -u admin:admin123 http://localhost:8080/api/orders/admin/ping
```

## Prometheus + Grafana

启动观测栈：

```bash
docker compose -f observability/docker-compose.yml up -d
```

查看状态：

```bash
docker compose -f observability/docker-compose.yml ps
curl -fsS http://localhost:9090/-/ready
curl -fsS http://localhost:3000/api/health
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
```

停止观测栈：

```bash
docker compose -f observability/docker-compose.yml down
```

访问地址：

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

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

后台启动：

```bash
screen -dmS spring3-nacos-catalog zsh -lc 'SPRING_PROFILES_ACTIVE=nacos java -jar catalog-service/target/catalog-service-0.0.1-SNAPSHOT.jar > catalog-service/target/nacos-run.log 2>&1'
screen -dmS spring3-nacos-order zsh -lc 'SPRING_PROFILES_ACTIVE=nacos java -jar order-service/target/order-service-0.0.1-SNAPSHOT.jar > order-service/target/nacos-run.log 2>&1'
```

查看状态和日志：

```bash
docker compose -f platform/nacos/docker-compose.yml ps
docker logs -f spring3-nacos
tail -f catalog-service/target/nacos-run.log
tail -f order-service/target/nacos-run.log
```

验证注册发现：

```bash
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=catalog-service'
curl -fsS 'http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=order-service'
```

验证配置读取和服务名调用：

```bash
curl -u admin:admin123 http://localhost:8080/api/orders/admin/stats
curl -u admin:admin123 http://localhost:8081/api/catalog/admin/stats

curl -u user:user123 \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-1001","quantity":2}' \
  http://localhost:8080/api/orders/preview
```

停止：

```bash
screen -S spring3-nacos-catalog -X quit
screen -S spring3-nacos-order -X quit
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
lsof -nP -iTCP:9090 -sTCP:LISTEN
lsof -nP -iTCP:3000 -sTCP:LISTEN
lsof -nP -iTCP:8848 -sTCP:LISTEN
```

## 常用收尾命令

停止 Spring 服务：

```bash
screen -S spring3-catalog -X quit
screen -S spring3-order -X quit
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
