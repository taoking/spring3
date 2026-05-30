# 27 Kubernetes 生产化计划

## 目标

在当前 `deployment/k8s` 最小 YAML 基线上，补齐生产面试常问的 Ingress、HPA、PDB、ServiceMonitor、Secret 管理、镜像发布、GitOps、回滚和故障排查。

## 任务 Prompt

```text
基于当前 Spring Boot 3 学习项目，补充 Kubernetes 生产化专题。请先阅读：

- docs/kubernetes.md
- deployment/k8s
- docs/task-plans/17-kubernetes.md
- docs/task-plans/19-interview-expansion.md

目标：
1. 保持当前最小 YAML 清晰，不把示例扩展成复杂平台。
2. 文档化 Ingress、HPA、PDB、ServiceMonitor 的示例和限制。
3. 文档化 ConfigMap/Secret、外部 Secret、镜像 tag/digest、SBOM 和回滚策略。
4. 文档化 Helm/Kustomize/GitOps 的取舍。
5. 输出 Pod 启动失败、readiness 失败、滚动发布卡住、资源不足的排查命令。
6. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或 task plan 索引。

验收：
1. 文档能解释当前 deployment/k8s 每个 YAML 的作用。
2. 文档能回答 readiness/liveness/startupProbe、requests/limits、HPA、PDB、Ingress 和 ServiceMonitor 追问。
3. 不要求本地真实集群。
4. 不提交真实 Secret。
```

## 当前实施结果

- 新增 [Kubernetes 生产化专题](../kubernetes-production-playbook.md)。
- 基于当前 `deployment/k8s` 说明 Namespace、ConfigMap、Secret、Deployment、Service、probe、resources 和 graceful shutdown。
- 补充 Ingress、HPA、PDB、ServiceMonitor、Secret 管理、镜像 tag/digest、GitOps、回滚和故障排查。

## 验收命令

```bash
kubectl apply --dry-run=client -f deployment/k8s
kubeconform -strict -summary deployment/k8s
git diff --check
```

如果本地 `kubectl` kubeconfig 指向的集群不可用，可使用 `kubeconform` 做离线 schema 校验。

## 不做

- 不引入真实集群依赖。
- 不提交真实 Secret。
- 不把 ServiceMonitor 放进默认 apply 路径，避免缺 CRD 时失败。
- 不引入 Helm chart 或 GitOps 平台代码。
