# 05 OAuth2 Resource Server / JWT 计划

## 目标

在当前 Basic Auth 基础上补充 JWT 资源服务器示例，覆盖认证、授权、scope/role 映射和测试。

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

## 不做

- 不实现完整登录页。
- 不接入真实第三方 IdP。
- 不提交生产私钥或长期有效 token。
