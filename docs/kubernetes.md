# Kubernetes 部署示例

## 目标

本专题用于学习 Spring Boot 3 服务在 Kubernetes 中的基础部署形态，覆盖 Deployment、Service、ConfigMap、Secret、readiness/liveness、资源限制、滚动发布、优雅停机和 Prometheus 抓取方式。

当前只提供可 dry-run 的最小 YAML 示例，不要求真实集群，也不维护 Helm chart。

## 文件结构

| 文件 | 作用 |
| --- | --- |
| `deployment/k8s/00-namespace.yaml` | 创建 `spring3` namespace |
| `deployment/k8s/01-catalog-configmap.yaml` | `catalog-service` 非敏感环境变量 |
| `deployment/k8s/02-order-configmap.yaml` | `order-service` 非敏感环境变量，包含 `DEMO_CLIENTS_CATALOG_BASE_URL=http://catalog-service:8081` |
| `deployment/k8s/03-runtime-secret.yaml` | 空 `SENTRY_DSN` Secret 示例，不包含真实密钥 |
| `deployment/k8s/10-catalog-service.yaml` | `catalog-service` Service + Deployment |
| `deployment/k8s/20-order-service.yaml` | `order-service` Service + Deployment |

## 镜像前提

先构建 jar 和本地镜像：

```bash
./mvnw package -DskipTests
docker build -t spring3/catalog-service:local ./catalog-service
docker build -t spring3/order-service:local ./order-service
```

如果使用 kind：

```bash
kind load docker-image spring3/catalog-service:local
kind load docker-image spring3/order-service:local
```

如果使用 minikube：

```bash
minikube image load spring3/catalog-service:local
minikube image load spring3/order-service:local
```

真实集群通常需要把镜像推到 registry，再把 YAML 中的 `image` 改为 registry 地址和不可变 tag。

## 校验与 Dry-run

无真实集群时，优先用 kubeconform 做离线 schema 校验：

```bash
brew install kubeconform
kubeconform -strict -summary deployment/k8s/*.yaml
```

当前本地校验结果：

```text
Summary: 8 resources found in 6 files - Valid: 8, Invalid: 0, Errors: 0, Skipped: 0
```

有可连接集群时再做 Kubernetes dry-run：

```bash
kubectl apply --dry-run=client -f deployment/k8s
kubectl apply --dry-run=server -f deployment/k8s
```

注意：较新的 `kubectl` 即使使用 `--dry-run=client`，也可能需要连接 API server 做资源 discovery。没有集群时如果看到连接 `localhost:8080` 失败，说明失败点是本地没有 Kubernetes API server，不代表 YAML schema 已失败。

## 部署与查看

应用 YAML：

```bash
kubectl apply -f deployment/k8s
```

查看资源：

```bash
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

## 配置与 Secret

非敏感配置放在 ConfigMap：

- `APP_ENV=k8s`
- `SERVER_SHUTDOWN=graceful`
- `SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=20s`
- `JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError`
- `DEMO_CLIENTS_CATALOG_BASE_URL=http://catalog-service:8081`

敏感配置只放 Secret。仓库里的 `03-runtime-secret.yaml` 只保留空 `SENTRY_DSN` 示例，不提交真实 DSN。

在真实集群中创建或更新 Secret：

```bash
kubectl -n spring3 create secret generic spring3-runtime-secret \
  --from-literal=SENTRY_DSN='你的 Sentry DSN' \
  --dry-run=client -o yaml | kubectl apply -f -
```

生产环境建议使用 External Secrets、Sealed Secrets 或云厂商 Secret Manager，不把明文 secret 写进 Git。

## 探针

当前 YAML 使用 Spring Boot Actuator probes：

| Probe | 路径 | 用途 |
| --- | --- | --- |
| `startupProbe` | `/actuator/health/liveness` | 给 JVM 和 Spring 容器启动时间，不让 liveness 过早重启 |
| `readinessProbe` | `/actuator/health/readiness` | 决定 Pod 是否进入 Service endpoints |
| `livenessProbe` | `/actuator/health/liveness` | 判断进程是否需要重启 |

不要用业务接口替代探针。业务接口可能依赖下游服务、认证、限流或外部状态，容易导致错误扩缩容或错误重启。

## 滚动发布与优雅停机

YAML 中的滚动发布策略：

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1
    maxUnavailable: 0
```

优雅停机配置：

- Spring Boot：`server.shutdown=graceful`
- Spring lifecycle：`spring.lifecycle.timeout-per-shutdown-phase=20s`
- Kubernetes：`terminationGracePeriodSeconds=35`
- `preStop`：`sleep 10`，给 endpoints 摘除和连接排空留时间

滚动更新镜像：

```bash
kubectl -n spring3 set image deployment/catalog-service \
  catalog-service=spring3/catalog-service:2026-05-09

kubectl -n spring3 rollout status deployment/catalog-service
```

回滚：

```bash
kubectl -n spring3 rollout undo deployment/catalog-service
```

## Prometheus 抓取

当前 Service 和 Pod template 已加 Prometheus 注解：

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8080"
```

如果集群使用 Prometheus Operator，可以改成 ServiceMonitor。示例：

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: spring3-services
  namespace: spring3
spec:
  selector:
    matchExpressions:
      - key: app.kubernetes.io/part-of
        operator: In
        values: ["spring3-learning"]
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
```

ServiceMonitor 依赖 Prometheus Operator CRD，本仓库不直接提交该对象，避免没有 CRD 的集群 dry-run 失败。

## 面试复盘点

- Deployment 管副本和滚动发布，Service 提供稳定访问入口和负载均衡。
- ConfigMap 放非敏感配置，Secret 放敏感配置；Secret 默认不是加密保险箱，生产要结合 KMS 或外部 Secret 系统。
- readiness 决定是否接流量，liveness 决定是否重启，startup 防止慢启动被 liveness 误杀。
- 滚动发布要和 `maxUnavailable`、readiness、`preStop`、`terminationGracePeriodSeconds`、Spring graceful shutdown 一起设计。
- requests 影响调度，limits 影响资源上限；Java 容器要关注内存比例、OOM 行为和 GC。
- Prometheus 可以通过注解抓取，也可以通过 ServiceMonitor 管理，前者轻量，后者更适合平台化集群。
