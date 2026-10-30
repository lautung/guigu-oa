# 服务端模块地图（导航）

`server/guigu-oa-parent/` 是多模块 Maven 工程。找服务端的东西先看这张表，再决定要不要 grep。
客户端模块与审批端口见 `client-modules.md`；领域术语见根目录 `GLOSSARY.md`。

## 模块与关键类

| 模块 | 路径 | 关键类 / 内容 |
|---|---|---|
| `common/common-util` | `common/common-util/src/main/java/com/atguigu/common/` | `result/Result`（统一信封：`code`/`message`/`data`）、`result/ResultCodeEnum`（200/201/208/209/403）、`result/ResponseUtil`（过滤器写响应体，带 HTTP 状态重载）、`jwt/JwtHelper` |
| `common/service-util` | `common/service-util/.../common/` | `config/exception/GlobalExceptionHandler`（业务异常 200+code；容器级 404/405；参数校验 200+201，ADR 0033）、`config/mp/CommonFieldsMetaObjectHandler`（公共字段唯一写入者，ADR 0031）、`observability/TraceIdFilter` |
| `common/spring-security` | `common/spring-security/.../security/` | `config/WebSecurityConfig`（过滤器链与入口点）、`config/PublicApiPaths`（匿名放行清单）、`filter/TokenAuthenticationFilter`（token→认证，失败 401+208）、`filter/TokenLoginFilter`（POST /login，先校验入参再签发双 token）、`custom/RestAuthenticationEntryPoint`（未认证 401+208）、`custom/RestAccessDeniedHandler`（403+205）、`login/`（限流）、`revocation/`（会话吊销） |
| `model` | `model/src/main/java/com/atguigu/model/` | 实体：`system/SysUser|SysRole|SysMenu|SysUserRole|SysRoleMenu`、`process/OaProcess|OaProcessRecord…`；请求 DTO 的 jsr303 约束（`com.atguigu.vo.*`，ADR 0033） |
| `service-oa` | `service-oa/src/main/java/com/atguigu/` | 业务实现与 HTTP 入口：`auth/`（index/login、sysUser/Role/Menu、`grant/ConfigurationGrant` 配置授予、`service/PermissionCacheInvalidator` 权限缓存失效）、`process/`（模板/审批/附件/`approval/ApprovalWriter`）、`db/migration/`（Flyway V1xx）。迁移以 SQL 为主；需要"先查元数据再决定动不动手"的（如 `V104` 依赖引擎历史回填）写成 `src/main/java/db/migration/` 下的 Java 迁移 |

## 认证与错误语义（改安全相关代码前必读）

请求经过的层，顺序即拒绝优先级：

1. `TokenAuthenticationFilter`：`PublicApiPaths.permitsAnonymous(uri)` 的 URI 跳过校验；其余请求无有效 token → **401 + code 208**；停用账号 → 401 + 209。
2. `TokenLoginFilter`：只接 `POST /admin/system/index/login`（POST-only 且不在白名单里，靠过滤器先于授权层接管）。空凭证/畸形体在限流计数之前拒绝（**401 + 208 + 字段消息**，不计入失败次数）；登录失败/锁定/停用 → **401 + 208/209**。
3. `AuthorizationFilter`：白名单 `permitAll`，其余 `authenticated()`。未认证被拒 → `RestAuthenticationEntryPoint`（**401 + 208**，覆盖匿名路径上的错误方法）；已认证被拒 → `RestAccessDeniedHandler`（**403 + 205**）。
4. MVC 参数解析：`@Valid` 在控制器方法调用前执行，所以**校验先于 `@PreAuthorize`**。未授权 + 畸形体得到校验失败（201），不是 205；良构体才轮到配置 gate。认证早于这一层，所以匿名 + 畸形体仍是 401+208（ADR 0034）。
5. `@PreAuthorize`：拒绝 → `GlobalExceptionHandler` 的 `AccessDeniedException` 分支（**HTTP 200 + code 205**，历史约定）。未知路径 404、方法不匹配 405（均带 `code=201` 信封）；校验失败与缺体/畸形 JSON 是 HTTP 200 + `code=201` + 具体消息（ADR 0033）；其余业务失败 HTTP 200 + `code`。

回归锚点：`LoginAuthContractTest`（401/208/209 全表）、`HttpErrorSemanticsContractTest`（404/405/类型不匹配）、`RequestValidationContractTest`（参数校验 201 文案与顺序契约）、`FlywayMigrationContractTest`（配置 gate 的授予矩阵，用良构体触发）、接口套件 `tests/api/f-http-semantics.spec.ts`、`tests/api/b-menu.spec.ts`（B22 顺序契约）。

## 行政约定

- 业务 `@RequestMapping` 前缀：`/admin/system/*`（认证与配置）、`/admin/process/*`（流程）、`/admin/attachments/*`。
- 静态形状约束写请求 DTO 的 jsr303 注解 + `@Valid`；领域/状态判断留 service；登录在 `TokenLoginFilter` 里手工校验（ADR 0033）。
- 配置类写请求体是 `model` 的 `vo/{system,process}/XxxRequest`：save 只创建（不含 `id`）、update 只要求 `id`、其余为空即不覆盖；改这两条规则先看 ADR 0034。长度上限对齐列宽、`sys_menu.type` 只收 0/1、菜单页必须自带组件路径（save 在 DTO、update 在 service 按落库形状判），见 ADR 0036。
- 配置类接口由 `configurationMenuGate`（见 `ConfigurationGrant`）按菜单授予统一判定，`@PreAuthorize` 只是粗门。
- 表结构迁移只加不改：`service-oa/src/main/resources/db/migration/V*.sql`，Flyway 按版本执行。
- `POST /admin/process/startUp` 的 `idempotencyKey` 可空，唯一索引 `uk_oa_process_idempotency_key` 按用户唯一（ADR 0032）：重复送达返回同一条审批；冲突后的重读必须在 `ApprovalWriter.startUp` 的事务之外（可重复读下事务内看不到对手已提交的行）。
