# 工程质量与 CI 门禁专题

## 定位

当前项目已经有 Maven 多模块、单元测试、profile 测试、Testcontainers 集成测试和 GitHub Actions。资深面试会继续追问：如何让团队持续交付、如何防止架构腐化、如何控制依赖和镜像风险、如何在 CI 成本和质量之间取舍。

本专题不直接把所有扫描工具加入默认构建，避免学习项目 CI 变慢。建议采用“默认快门禁 + 手动深度门禁 + 发布前安全门禁”的分层策略。

## 当前 CI 基线

| Job | 当前命令 | 覆盖 |
| --- | --- | --- |
| unit-tests | `./mvnw -B test` | 默认 profile 单元测试和 MVC 测试 |
| integration-tests | `./mvnw -B -Pintegration-test verify` | Gateway Testcontainers |
| RabbitMQ IT | `./mvnw -B -Prabbitmq,integration-test ... verify` | RabbitMQ Testcontainers |
| Kafka IT | `./mvnw -B -Pkafka,integration-test ... verify` | Kafka Testcontainers |

当前优点：

- 默认构建不依赖外部服务。
- MQ 和 Gateway 集成测试隔离在 Docker job。
- profile 隔离清楚，不污染默认路径。

当前缺口：

- 无覆盖率报告和覆盖率门禁。
- 无静态代码扫描。
- 无依赖漏洞扫描和 SBOM。
- 无架构规则自动化。
- 无镜像扫描和发布前检查。

## 推荐门禁分层

| 层级 | 触发 | 内容 | 原则 |
| --- | --- | --- | --- |
| L0 本地快速 | 每次提交前 | `./mvnw test`、`git diff --check` | 快，几分钟内完成 |
| L1 PR 默认 | push / PR | 单元测试、profile 编译、关键 slice 测试 | 阻断主干破坏 |
| L2 Docker IT | PR / main | Testcontainers、MQ、Gateway 集成测试 | 依赖 Docker，成本较高 |
| L3 质量扫描 | 手动 / nightly | JaCoCo、ArchUnit、SpotBugs/Checkstyle、依赖漏洞 | 防技术债扩散 |
| L4 发布前 | release | SBOM、镜像扫描、签名、制品追踪 | 面向生产风险 |

## JaCoCo 覆盖率

覆盖率适合做趋势和底线，不适合替代有效测试。

建议策略：

- 初期只生成报告，不立刻设高门槛。
- 先按模块看核心业务类覆盖，不追求 DTO、配置类、启动类。
- 后续设置渐进式门槛，例如 line 60%、branch 40%，再逐步提高。
- 对新增高风险逻辑要求有测试，不用全局覆盖率掩盖质量问题。

候选命令：

```bash
./mvnw test
./mvnw org.jacoco:jacoco-maven-plugin:prepare-agent test org.jacoco:jacoco-maven-plugin:report
```

面试回答底线：覆盖率高不等于测试有效。要结合边界值、失败路径、契约测试和集成测试。

## 静态扫描取舍

| 工具 | 适合 | 风险 |
| --- | --- | --- |
| Checkstyle | 团队代码风格一致 | 规则过细会制造噪音 |
| SpotBugs | 空指针、资源泄漏、并发风险 | 需要配置排除规则 |
| PMD | 复杂度、重复、坏味道 | 误报较多 |
| Error Prone | 编译期 bug pattern | Maven/Spring 项目接入成本略高 |

建议：

- 学习项目先文档化规则，后续再引入最小 Checkstyle 或 SpotBugs。
- 质量门禁初期以 warning 报告为主，稳定后再 fail build。
- 所有 suppress 都必须写明原因。

## 依赖安全和 SBOM

建议能力：

| 能力 | 候选工具 | 命令示例 |
| --- | --- | --- |
| 依赖漏洞 | OWASP Dependency-Check、Snyk、GitHub Dependabot | `./mvnw org.owasp:dependency-check-maven:check` |
| SBOM | CycloneDX Maven Plugin | `./mvnw org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom` |
| 依赖升级 | Versions Maven Plugin | `./mvnw versions:display-dependency-updates` |
| 镜像扫描 | Trivy、Docker Scout、Grype | `trivy image spring3/order-service:local` |

生产建议：

- Dependabot 自动提 PR，但不要自动合并。
- 安全扫描要区分 runtime、test、optional 依赖。
- 漏洞处理需要 SLA，例如 critical 24h、high 7d。
- SBOM 随 release 制品保存，便于事故追溯。

## ArchUnit 规则候选

建议后续新增 `architecture-test` 或普通 test 中的 ArchUnit 规则。

| 规则 | 目的 |
| --- | --- |
| Controller 不直接访问 Feign/RestClient client | 保持 Controller -> Service -> Client 分层 |
| Web 层只依赖 DTO 和 service，不依赖 messaging 实现 | 防止入口层耦合 MQ |
| `src/kafka/java` 只能在 `kafka` Maven profile 编译 | 防止默认路径污染 |
| `src/rabbitmq/java` 只能在 `rabbitmq` Maven profile 编译 | 防止默认路径污染 |
| `common` 模块不能依赖业务服务模块 | 保持公共模块单向依赖 |
| `GlobalExceptionHandler` 统一输出 ProblemDetail | 防止错误响应碎片化 |
| 配置类使用 `@ConfigurationProperties` 优先于散落 `@Value` | 保持配置可测试和可文档化 |
| 禁止业务代码读取真实密钥或硬编码 token | 防止敏感信息进入仓库 |
| Gateway 不实现订单业务逻辑 | 保持入口治理和业务服务边界 |
| 可选组件必须有 profile 或独立目录隔离 | 保持默认 profile 轻量 |

面试回答底线：ArchUnit 不是替代 code review，而是把关键架构约束变成自动反馈。

## 测试分层

| 类型 | 当前项目例子 | 目标 |
| --- | --- | --- |
| Unit test | service/helper 逻辑 | 快速验证纯逻辑 |
| MVC slice | Controller + MockBean | 验证 HTTP、Validation、ProblemDetail |
| SpringBootTest | profile 行为 | 验证配置和上下文 |
| MockWebServer | Feign/RestClient 下游 | 验证 HTTP client 行为 |
| Spring Cloud Contract | provider/consumer 契约 | 防接口兼容破坏 |
| Testcontainers | Gateway/MQ | 验证真实外部依赖 |
| 手动演练 | Prometheus/Grafana/Zipkin | 验证观测和排障闭环 |

建议：

- 默认 PR 先跑快测试。
- Docker IT 可并行跑，避免拖慢反馈。
- 高风险变更必须补失败路径测试。
- 契约测试用于服务间接口兼容，不代替业务集成测试。

## 镜像和发布门禁

建议后续发布前检查：

- Dockerfile 不使用 `latest`。
- 非 root 用户运行。
- 镜像层尽量小，避免把 target 临时文件和密钥打入镜像。
- Actuator health/readiness/liveness 配置正确。
- 镜像扫描无 critical/high 未处理漏洞。
- release tag、镜像 digest、SBOM、commit SHA 可追溯。

## 面试追问清单

| 追问 | 回答要点 |
| --- | --- |
| 覆盖率多少才算够？ | 看风险和趋势，不用单一数字替代有效测试 |
| 为什么要分层测试？ | 速度、反馈、覆盖面和稳定性不同 |
| Contract 和 MockWebServer 区别？ | Contract 管接口兼容，MockWebServer 管客户端行为 |
| Testcontainers 为什么适合 CI？ | 可重复、隔离真实依赖、避免共享测试环境污染 |
| 质量门禁会不会拖慢团队？ | 分层触发，默认快，深度扫描 nightly/release |
| 如何防架构腐化？ | 模块边界、ArchUnit、code review、文档和重构 |
| Dependabot PR 怎么处理？ | 分级、测试、变更日志、兼容验证 |
| SBOM 有什么用？ | 漏洞响应、供应链追溯、制品合规 |
| 镜像扫描发现漏洞怎么办？ | 升级基础镜像/依赖、评估可达性、记录豁免 |
| 静态扫描误报怎么办？ | 调整规则、局部 suppress 并写原因 |

## 后续落地建议

1. 先增加 ArchUnit 最小规则，保护当前模块边界。
2. 再增加 JaCoCo 报告，不立刻设置高门槛。
3. 增加 Dependabot 和 SBOM 生成，发布前保存制品。
4. 镜像扫描放到手动或 release job，避免默认 PR 过慢。
5. 每次新增 profile 都增加一个“默认路径不受影响”的测试或编译检查。
