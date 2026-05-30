# 26 工程质量与 CI 门禁计划

## 目标

在当前 GitHub Actions 和测试基线上，补齐资深面试需要的工程质量门禁设计，包括覆盖率、静态扫描、依赖安全、SBOM、镜像扫描、ArchUnit 架构规则和 CI 分层。

## 任务 Prompt

```text
基于当前 Spring Boot 3 学习项目，补充工程质量与 CI 门禁专题。请先阅读：

- .github/workflows/ci.yml
- pom.xml
- docs/USAGE.md
- docs/task-plans/19-interview-expansion.md

目标：
1. 评估当前 CI 覆盖：默认测试、Testcontainers、RabbitMQ/Kafka IT。
2. 设计默认快门禁、Docker 集成测试、夜间质量扫描和发布前安全门禁。
3. 文档化 JaCoCo、Checkstyle/SpotBugs/PMD、OWASP Dependency-Check、SBOM、镜像扫描取舍。
4. 给出至少 5 条 ArchUnit 架构规则候选。
5. 说明覆盖率不能替代有效测试。
6. 更新 README、docs/USAGE.md、docs/interview-roadmap.md 或 task plan 索引。

验收：
1. 文档能说明哪些检查默认执行，哪些手动执行，哪些发布前执行。
2. 至少给出 5 条 ArchUnit 规则候选。
3. 至少给出 5 条质量/安全相关命令或工具。
4. 不把慢扫描强行加入默认构建。
```

## 当前实施结果

- 新增 [工程质量与 CI 门禁专题](../engineering-quality-playbook.md)。
- 梳理当前 CI 基线：默认单元测试、Gateway Testcontainers、RabbitMQ IT、Kafka IT。
- 补充 L0-L4 分层门禁、JaCoCo、静态扫描、依赖安全、SBOM、镜像扫描和 ArchUnit 规则候选。
- 明确不把慢扫描强行加入默认构建，避免学习项目反馈变慢。

## 验收命令

```bash
./mvnw test
git diff --check
```

可选深度命令：

```bash
./mvnw org.jacoco:jacoco-maven-plugin:prepare-agent test org.jacoco:jacoco-maven-plugin:report
./mvnw org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom
./mvnw versions:display-dependency-updates
```

## 不做

- 不立即强制覆盖率门槛。
- 不把 OWASP/镜像扫描塞进默认 PR 快路径。
- 不引入大量风格规则制造噪音。
