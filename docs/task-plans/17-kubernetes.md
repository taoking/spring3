# 17 Kubernetes 计划

## 目标

补充 Kubernetes 部署认知，覆盖 Deployment、Service、ConfigMap、Secret、readiness/liveness、滚动发布和资源限制。

## 任务 Prompt

```text
为当前项目补充 Kubernetes 部署示例。请先阅读 Docker 部署计划、docs/USAGE.md 和 docs/task-plans/17-kubernetes.md。

要求：
1. 不要求真实集群，优先提供可 dry-run 的 YAML 或 Helm/Kustomize 示例。
2. 为 catalog-service 和 order-service 提供 Deployment、Service、ConfigMap。
3. 配置 readiness/liveness probe，对应 Actuator health probes。
4. 配置资源 requests/limits、环境变量和优雅停机。
5. Prometheus 抓取方式写成注释或 ServiceMonitor 可选示例。
6. 更新文档，说明本地验证和面试重点。
```

## 示例内容

- `deployment/k8s/catalog-service.yaml`
- `deployment/k8s/order-service.yaml`
- `kubectl apply --dry-run=client -f deployment/k8s`
- ConfigMap 注入 `demo.clients.catalog.base-url`。

## 当前实施结果

- 已新增专题文档：[Kubernetes 部署示例](../kubernetes.md)。
- 已新增 `deployment/k8s/00-namespace.yaml`。
- 已新增 `deployment/k8s/01-catalog-configmap.yaml` 和 `deployment/k8s/02-order-configmap.yaml`。
- 已新增 `deployment/k8s/03-runtime-secret.yaml`，只包含空 `SENTRY_DSN` 示例，不包含真实密钥。
- 已新增 `deployment/k8s/10-catalog-service.yaml`，包含 `catalog-service` Service + Deployment。
- 已新增 `deployment/k8s/20-order-service.yaml`，包含 `order-service` Service + Deployment。
- `order-service` 通过 `DEMO_CLIENTS_CATALOG_BASE_URL=http://catalog-service:8081` 调用 Kubernetes Service。
- Deployment 已配置 Actuator readiness/liveness/startup probes、resources requests/limits、RollingUpdate、`preStop` 和 `terminationGracePeriodSeconds`。
- Prometheus 抓取以 Service/Pod annotations 方式演示；ServiceMonitor 仅写入文档示例，不提交 CRD 对象。

## 验证命令

无真实集群时：

```bash
kubeconform -strict -summary deployment/k8s/*.yaml
```

当前本地结果：

```text
Summary: 8 resources found in 6 files - Valid: 8, Invalid: 0, Errors: 0, Skipped: 0
```

有可连接集群时：

```bash
kubectl apply --dry-run=client -f deployment/k8s
kubectl apply --dry-run=server -f deployment/k8s
```

当前本机没有 Kubernetes API server，`kubectl apply --dry-run=client` 会失败在 API discovery，不代表 YAML schema 失败。

构建和加载本地镜像：

```bash
./mvnw package -DskipTests
docker build -t spring3/catalog-service:local ./catalog-service
docker build -t spring3/order-service:local ./order-service
```

真实部署后验证：

```bash
kubectl -n spring3 get deploy,svc,pod
kubectl -n spring3 rollout status deploy/catalog-service
kubectl -n spring3 rollout status deploy/order-service
```

## 实施要点

- K8s 任务依赖 Docker 镜像计划，先有镜像再谈部署。
- 探针要使用 Actuator readiness/liveness，不要用业务接口代替。
- Secret 示例不能包含真实密钥。
- 滚动发布策略要与 graceful shutdown 配套。

## 验收标准

- YAML 可通过 dry-run 或 kubeconform 校验。
- 文档说明每个资源对象的作用。
- readiness/liveness 配置合理。
- 不提交真实 Secret。
- README、使用说明、实施文档和面试路线包含 Kubernetes 入口。

## 不做

- 不搭建真实生产集群。
- 不维护复杂 Helm chart，除非后续明确需要。
