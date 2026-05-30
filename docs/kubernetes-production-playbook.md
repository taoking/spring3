# Kubernetes 生产化专题

## 定位

当前项目已有 `deployment/k8s` 最小部署示例，覆盖 Namespace、ConfigMap、Secret 示例、Deployment、Service、readiness/liveness/startup probe、resources、滚动发布和 graceful shutdown。生产面试会继续追问 Ingress、HPA、PDB、ServiceMonitor、Secret 管理、镜像发布、GitOps、回滚和故障排查。

本专题不要求本地有真实 Kubernetes 集群，也不把示例 YAML 扩展成完整平台。目标是能解释生产部署边界和面试追问。

## 当前资产

| 文件 | 作用 |
| --- | --- |
| `deployment/k8s/00-namespace.yaml` | 命名空间 |
| `deployment/k8s/01-catalog-configmap.yaml` | catalog 配置 |
| `deployment/k8s/02-order-configmap.yaml` | order 配置 |
| `deployment/k8s/03-runtime-secret.yaml` | Sentry DSN 示例 Secret |
| `deployment/k8s/10-catalog-service.yaml` | catalog Service + Deployment |
| `deployment/k8s/20-order-service.yaml` | order Service + Deployment |

已覆盖的生产基本功：

- `readinessProbe`：是否接流量。
- `livenessProbe`：是否需要重启。
- `startupProbe`：慢启动保护。
- `resources.requests/limits`：调度和资源上限。
- `terminationGracePeriodSeconds` + `preStop`：优雅下线。
- `RollingUpdate maxUnavailable=0`：滚动发布不中断。
- Prometheus scrape annotations：基础指标抓取。

## Ingress

生产入口通常不是直接暴露 Service，而是 Ingress / Gateway API / Service Mesh。

示例：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: spring3-ingress
  namespace: spring3
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "30"
spec:
  ingressClassName: nginx
  rules:
    - host: spring3.local
      http:
        paths:
          - path: /orders
            pathType: Prefix
            backend:
              service:
                name: order-service
                port:
                  number: 8080
          - path: /catalog
            pathType: Prefix
            backend:
              service:
                name: catalog-service
                port:
                  number: 8081
```

面试追问：

- Ingress 和 Service 区别是什么？
- TLS 证书放在哪里？
- 超时、body size、限流在哪里配置？
- 为什么生产更推荐 Gateway API 或专用网关？

## HPA

HPA 需要 metrics-server 或自定义指标适配器。

示例：

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
  namespace: spring3
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

生产要点：

- HPA 不是限流，扩容有滞后。
- CPU 指标不适合所有场景，I/O 等待型服务可能更需要 QPS、latency、queue lag。
- `requests.cpu` 必须合理，否则 CPU 利用率百分比没有意义。
- 扩容要配合下游容量，避免把压力转移给 catalog、DB 或 MQ。

## PDB

PDB 用于限制自愿驱逐，降低节点维护时的可用性风险。

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service
  namespace: spring3
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: order-service
```

追问要点：

- PDB 不阻止节点故障，只约束自愿驱逐。
- `minAvailable` 和副本数要匹配。
- 单副本服务设置 PDB 意义有限，还可能阻碍节点维护。

## ServiceMonitor

如果使用 Prometheus Operator，可用 ServiceMonitor 替代 scrape annotations。

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: spring3-services
  namespace: spring3
spec:
  selector:
    matchLabels:
      app.kubernetes.io/part-of: spring3-learning
  endpoints:
    - port: http
      path: /actuator/prometheus
      interval: 15s
```

注意：ServiceMonitor 依赖 CRD，本地没有 Prometheus Operator 时不能直接 apply。

## ConfigMap 和 Secret

当前 `03-runtime-secret.yaml` 只保留空 DSN 示例，不提交真实密钥。

生产建议：

- Secret 不直接提交明文，使用 External Secrets、Sealed Secrets、Vault 或云厂商 Secret Manager。
- ConfigMap 存普通配置，Secret 存敏感配置。
- 配置变更要有审计和回滚。
- 环境隔离使用 namespace、Helm values、Kustomize overlay 或 GitOps repo 分层。

## 镜像发布

建议镜像 tag 策略：

| Tag | 用途 |
| --- | --- |
| `local` | 本地学习 |
| git short SHA | CI 构建可追溯 |
| semantic version | release 版本 |
| digest | 生产精确锁定 |

生产底线：

- 不使用 `latest` 作为生产部署 tag。
- 记录 image digest、commit SHA、构建时间和 SBOM。
- 发布前扫描镜像漏洞。
- 回滚时回滚到已知 digest，而不是模糊 tag。

## 发布和回滚

常用命令：

```bash
kubectl apply -f deployment/k8s
kubectl -n spring3 rollout status deployment/order-service
kubectl -n spring3 rollout history deployment/order-service
kubectl -n spring3 rollout undo deployment/order-service
```

灰度或滚动发布检查：

- readiness 通过后再接流量。
- p95、错误率、重启次数、fallback、熔断器打开数。
- 新旧版本 API 兼容。
- `maxUnavailable=0` 避免滚动期间容量掉到 0。

## GitOps / Helm / Kustomize

生产常见选择：

| 方式 | 适用 | 取舍 |
| --- | --- | --- |
| 原生 YAML | 小项目、学习 | 简单但重复 |
| Kustomize | 多环境 overlay | Kubernetes 原生，模板能力有限 |
| Helm | chart 复用和参数化 | 模板复杂，需控制 values |
| Argo CD / Flux | GitOps 持续同步 | 需要运维平台和权限治理 |

本项目当前适合保持原生 YAML。后续如果要扩展，可新增：

```text
deployment/k8s/base
deployment/k8s/overlays/dev
deployment/k8s/overlays/prod
```

## 故障排查

Pod 无法启动：

```bash
kubectl -n spring3 get pods
kubectl -n spring3 describe pod <pod>
kubectl -n spring3 logs <pod>
```

readiness 不通过：

```bash
kubectl -n spring3 get endpoints order-service
kubectl -n spring3 describe pod <pod> | grep -A5 Readiness
```

滚动发布卡住：

```bash
kubectl -n spring3 rollout status deployment/order-service
kubectl -n spring3 describe deployment order-service
```

资源不足：

```bash
kubectl -n spring3 top pods
kubectl describe node <node>
```

面试回答底线：先看事件和状态，再看日志和指标，不要只盯应用日志。

## 面试追问清单

| 追问 | 回答要点 |
| --- | --- |
| readiness 和 liveness 区别？ | readiness 控制接流量，liveness 控制重启 |
| startupProbe 解决什么？ | 慢启动时避免 liveness 过早杀进程 |
| requests 和 limits 区别？ | requests 用于调度，limits 是上限 |
| HPA 为什么没扩容？ | metrics-server、requests、指标滞后、冷却窗口 |
| PDB 能防节点宕机吗？ | 不能，只约束自愿驱逐 |
| ConfigMap 改了 Pod 会自动更新吗？ | 挂载文件可能更新，环境变量不会，通常滚动重启 |
| Secret 能不能提交 Git？ | 不提交明文，用外部 Secret 或加密方案 |
| 滚动发布如何回滚？ | rollout history/undo，镜像 digest 可追溯 |
| ServiceMonitor 为什么 apply 失败？ | CRD 未安装 |
| Ingress 502 怎么查？ | controller 日志、Service endpoints、Pod readiness、后端端口 |

## 验收清单

- 能解释当前 `deployment/k8s` 每个 YAML 的作用。
- 能说明 probes、resources、rolling update 和 graceful shutdown 如何配合。
- 能给出 Ingress、HPA、PDB、ServiceMonitor 示例和限制。
- 能说明 Secret 不提交明文和镜像 tag/digest 追溯策略。
- 能给出 Pod 启动失败、readiness 失败、发布卡住和资源不足的排查命令。
