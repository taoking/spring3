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

## 不做

- 不搭建真实生产集群。
- 不维护复杂 Helm chart，除非后续明确需要。
