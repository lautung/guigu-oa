# 服务端模块地图（导航）

`server/guigu-oa-parent/` 是多模块 Maven 工程。找服务端的东西先看这张表，再决定要不要 grep。
客户端模块与审批端口见 `client-modules.md`；领域术语见根目录 `GLOSSARY.md`。

## 模块与关键类

| 模块 | 路径 | 关键类 / 内容 |
|---|---|---|
| `common/common-util` | `common/common-util/src/main/java/com/atguigu/common/` | `result/Result`（统一信封：`code`/`message`/`data`）、`result/ResultCodeEnum`（200/201/208/209/403）、`result/ResponseUtil`（过滤器写响应体，带 HTTP 状态重载）、`jwt/JwtHelper` |
| `common/service-util` | `common/service-util/.../common/` | `config/exception/GlobalExceptionHandler`（业务异常 200+code；容器级 404/405）、`observability/TraceIdFilter` |
| `common/spring-security` | `common/spring-security/.../security/` | `config/WebSecurityConfig`（过滤器链与入口点）、`config/PublicApiPaths`（匿名放行清单）、`filter/TokenAuthenticationFilter`（token→认证，失败 401+208）、`filter/TokenLoginFilter`（POST /login，签发双 token）、`custom/RestAuthenticationEntryPoint`（未认证 401+208）、`custom/RestAccessDeniedHandler`（403+205）、`login/`（限流）、`revocation/`（会话吊销） |
| `model` | `model/src/main/java/com/atguigu/model/` | 实体：`system/SysUser|SysRole|SysMenu|SysUserRole|SysRoleMenu`、`process/OaProcess|OaProcessRecord…` |
| `service-oa` | `service-oa/src/main/java/com/atguigu/` | 业务实现与 HTTP 入口：`auth/`（index/login、sysUser/Role/Menu、`grant/ConfigurationGrant` 配置授予、`service/PermissionCacheInvalidator` 权限缓存失效）、`process/`（模板/审批/附件/`approval/ApprovalWriter`）、`db/migration/`（Flyway V1xx） |

## 认证与错误语义（改安全相关代码前必读）

请求经过的层，顺序即拒绝优先级：

1. `TokenAuthenticationFilter`：`PublicApiPaths.permitsAnonymous(uri)` 的 URI 跳过校验；其余请求无有效 token → **401 + code 208**；停用账号 → 401 + 209。
2. `TokenLoginFilter`：只接 `POST /admin/system/index/login`（POST-only 且不在白名单里，靠过滤器先于授权层接管）。登录失败/锁定/停用 → **401 + 208/209**。
3. `AuthorizationFilter`：白名单 `permitAll`，其余 `authenticated()`。未认证被拒 → `RestAuthenticationEntryPoint`（**401 + 208**，覆盖匿名路径上的错误方法）；已认证被拒 → `RestAccessDeniedHandler`（**403 + 205**）。
4. MVC：`@PreAuthorize` 拒绝 → `GlobalExceptionHandler` 的 `AccessDeniedException` 分支（**HTTP 200 + code 205**，历史约定）；未知路径 404、方法不匹配 405（均带 `code=201` 信封）；其余业务失败 HTTP 200 + `code`。

回归锚点：`LoginAuthContractTest`（401/208/209 全表）、`HttpErrorSemanticsContractTest`（404/405/类型不匹配）、接口套件 `tests/api/f-http-semantics.spec.ts`。

## 行政约定

- 业务 `@RequestMapping` 前缀：`/admin/system/*`（认证与配置）、`/admin/process/*`（流程）、`/admin/attachments/*`。
- 配置类接口由 `configurationMenuGate`（见 `ConfigurationGrant`）按菜单授予统一判定，`@PreAuthorize` 只是粗门。
- 表结构迁移只加不改：`service-oa/src/main/resources/db/migration/V*.sql`，Flyway 按版本执行。
