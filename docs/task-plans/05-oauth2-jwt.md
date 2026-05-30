# 05 OAuth2 Resource Server / JWT 计划

## 目标

在当前 Basic Auth 基础上补充 JWT 资源服务器示例，覆盖认证、授权、scope/role 映射和测试。

2026-05-15 已补充生产化专题，见 [OAuth2 / JWT 生产化专题](../security-oauth2-playbook.md)。当前 `jwt` profile 继续保留本地 HS256 学习模式，同时预留 `issuer-uri` 和 `jwk-set-uri` 配置入口，用于接入真实授权服务器。

## 任务 Prompt

```text
为当前项目补充 OAuth2 Resource Server / JWT 示例。请先阅读两个服务的 SecurityConfig、测试和 docs/task-plans/05-oauth2-jwt.md。

要求：
1. 保留默认 Basic Auth，新增 jwt profile 或 security.mode 配置。
2. 引入 Spring Security OAuth2 Resource Server 和 Jose 相关依赖。
3. 提供本地开发可用的 JWT 验证方式，不提交生产私钥。
4. 将 JWT claims 映射到 ROLE_USER / ROLE_ADMIN 或 scope。
5. 修改或新增测试，覆盖无 token、普通用户 token、管理员 token。
6. 更新 docs/USAGE.md，写明如何生成测试 token、如何调用接口。
```

## 示例内容

- `GET /api/orders/admin/ping` 需要 `ROLE_ADMIN`。
- 普通 token 访问 admin 接口返回 `403`。
- 管理员 token 访问成功。
- Actuator health/prometheus/swagger 仍按当前策略公开或受控。

## 实施要点

- 推荐使用 `jwt` profile 隔离，避免破坏现有 Basic Auth 学习路径。
- 测试 token 可以由测试工具生成，生产密钥不能提交。
- 明确 authentication 和 authorization 的区别。
- 服务间调用认证方式需要说明：继续 Basic、改为 service token，或通过网关透传。

## 验收标准

- `./mvnw test` 通过。
- 默认 profile 下 Basic Auth 行为不变。
- `jwt` profile 下 Bearer token 生效。
- 无 token、错误 token、权限不足 token 都返回符合预期的状态码。
- 文档包含 token 生成和 curl 示例。

## 实施记录

- 已在 `catalog-service` 和 `order-service` 引入 `spring-boot-starter-oauth2-resource-server`，包含 Resource Server 和 JOSE/JWT 支撑。
- 已新增 `application-jwt.yml`，通过 `SPRING_PROFILES_ACTIVE=jwt` 切换 `demo.security.mode=jwt`。
- 已在两个服务的 `SecurityConfig` 中保留默认 Basic Auth，并新增 JWT Resource Server filter chain。
- 已提供本地 HS256 开发密钥：`DEMO_SECURITY_JWT_SECRET`，默认值仅用于学习演示，不是生产密钥。
- 已将 JWT `roles` claim 映射为 `ROLE_USER` / `ROLE_ADMIN`，并保留默认 `scope` 到 `SCOPE_*` 的映射。
- 已明确服务间认证取舍：JWT profile 下仍保留 Basic 作为内部服务调用凭证；生产可替换为 `client_credentials` service token。
- 已预留生产化 Resource Server 配置：`demo.security.jwt.issuer-uri` 和 `demo.security.jwt.jwk-set-uri`，优先级高于本地 HS256 secret。
- 已新增 `CatalogJwtSecurityTest`，覆盖 public endpoint、无 token、错误 token、普通用户 token、管理员 token、JWT 模式下 Basic 内部凭证。
- 已新增 `OrderJwtSecurityTest`，覆盖无 token、错误 token、普通用户 token、管理员 token，并验证 order 调 catalog 仍使用 Basic。
- 已在 `docs/USAGE.md` 写入 token 生成脚本和 curl 验证命令。

已验证：

```bash
./mvnw -pl catalog-service,order-service -am test
./mvnw test
./mvnw -Pnacos test
./mvnw package -DskipTests
```

## 生产化补充

配置优先级：

1. `demo.security.jwt.jwk-set-uri`
2. `demo.security.jwt.issuer-uri`
3. `demo.security.jwt.secret`

生产环境建议使用授权服务器的 `issuer-uri` 或 `jwk-set-uri`，并结合：

- JWK rotation。
- audience 校验。
- `client_credentials` 服务间 token。
- 网关鉴权与服务侧最小授权。
- 短 access token + refresh token 或 opaque token 吊销策略。

本项目仍保留 Basic 内部调用，是为了不破坏已有 Feign/RestClient 学习链路。生产系统应改成 service token、mTLS 或服务网格身份。

## 不做

- 不实现完整登录页。
- 不接入真实第三方 IdP。
- 不提交生产私钥或长期有效 token。
