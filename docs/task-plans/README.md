# 后续任务计划 Prompt 索引

这个目录用于沉淀后续扩展任务的计划、执行 prompt、示例内容和验收标准。每个文件都可以作为后续独立任务的输入，适合逐个模块推进。

## 使用方式

1. 选择一个专题文件。
2. 复制其中的“任务 Prompt”给后续执行任务。
3. 执行前先阅读该专题的“实施要点”“验收标准”和“不做”。
4. 执行后把验证命令、结果和未完成项写回对应文档或 README。

## 执行顺序建议

| 顺序 | 专题 | 文档 | 优先级 |
| --- | --- | --- | --- |
| 1 | Nacos 实装（基线已完成） | [01-nacos.md](01-nacos.md) | P0 |
| 2 | Spring Cloud Gateway（基线已完成） | [02-gateway.md](02-gateway.md) | P0 |
| 3 | 链路追踪（基线已完成） | [03-tracing.md](03-tracing.md) | P0 |
| 4 | RestClient / `@HttpExchange`（RestClient 基线已完成） | [04-http-clients.md](04-http-clients.md) | P0 |
| 5 | OAuth2 Resource Server / JWT（基线已完成） | [05-oauth2-jwt.md](05-oauth2-jwt.md) | P0 |
| 6 | 自定义 starter / autoconfigure（基线已完成） | [06-autoconfigure-starter.md](06-autoconfigure-starter.md) | P0 |
| 7 | Resilience4j 深化（基线已完成） | [07-resilience4j.md](07-resilience4j.md) | P0 |
| 8 | Testcontainers / CI（基线已完成） | [08-testcontainers-ci.md](08-testcontainers-ci.md) | P0 |
| 9 | Docker 镜像与部署（基线已完成） | [09-container-deployment.md](09-container-deployment.md) | P1 |
| 10 | Java 21 虚拟线程（基线已完成） | [10-virtual-threads.md](10-virtual-threads.md) | P1 |
| 11 | Sentinel（基线已完成） | [11-sentinel.md](11-sentinel.md) | P1 |
| 12 | 结构化日志（基线已完成） | [12-structured-logging.md](12-structured-logging.md) | P1 |
| 13 | API 治理（基线已完成） | [13-api-governance.md](13-api-governance.md) | P1 |
| 14 | Spring Cloud Contract（基线已完成） | [14-contract-testing.md](14-contract-testing.md) | P1 |
| 15 | 消息队列 | [15-messaging.md](15-messaging.md) | P2 |
| 16 | Native Image / AOT | [16-native-aot.md](16-native-aot.md) | P2 |
| 17 | Kubernetes | [17-kubernetes.md](17-kubernetes.md) | P2 |

## 通用执行约束

- 默认 profile 必须保持轻量，不强制依赖 Nacos、Sentinel、消息队列、Zipkin、Tempo 或 Kubernetes。
- 新增外部组件时优先使用独立 profile、独立 Docker Compose 或独立 Maven profile。
- 不接入数据库和 Redis，除非任务明确变更项目边界。
- 不提交真实密钥、真实 Sentry DSN、私钥或访问 token。
- 每个专题都要更新使用说明或专题文档。
- 每个专题至少保留一条可自动执行的验证命令。
- 涉及运行时行为变更时必须跑 `./mvnw test`。
