# 服务端模块地图（导航）

`server/lautung-oa-parent/` 是多模块 Maven 工程（扁平 `lautung-oa-*` 模块，groupId `com.lautung`）。找服务端的东西先看这张表，再决定要不要 grep。
客户端模块与审批端口见 `client-modules.md`；领域术语见根目录 `GLOSSARY.md`。

模块依赖边（无环）：`common ← framework`；`common, model ← security`；`common, framework, security, model ← auth`；`common, framework, security, model, auth ← process`；全部 ← `server`。业务代码包声明为 `com.lautung.oa.*`；Flyway Java 迁移固定在 `db.migration`（不在 `com.lautung.oa` 树下，也不在 ArchUnit 分析集内）。

## 模块与关键类

| 模块 | 路径 | 关键类 / 内容 |
|---|---|---|
| `lautung-oa-common` | `lautung-oa-common/src/main/java/com/lautung/oa/common/` | `result/Result`（统一信封：`code`/`message`/`data`）、`result/ResultCodeEnum`（200/201/208/209/403）、`result/ResponseUtil`（过滤器写响应体，带 HTTP 状态重载）、`jwt/JwtHelper` |
| `lautung-oa-framework` | `lautung-oa-framework/src/main/java/com/lautung/oa/framework/` | `config/exception/GlobalExceptionHandler`（业务异常 200+code；容器级 404/405；参数校验 200+201，ADR 0033）、`config/mp/CommonFieldsMetaObjectHandler`（公共字段唯一写入者，ADR 0031）、`config/mp/MybatisPlusConfig`（分页拦截器，不再扫描域包）、`config/mp/SlowSqlLoggingConfiguration` + `observability/SlowSqlLoggingInterceptor`（慢 SQL WARN，默认关 dev 开，ADR 0041）、`observability/TraceIdFilter`、`observability/PhoneNumberMasker` + `PhoneNumberMaskingCustomizer`（结构化日志手机号打码，ADR 0039） |
| `lautung-oa-security` | `lautung-oa-security/.../security/` | `config/WebSecurityConfig`（过滤器链与入口点）、`config/PublicApiPaths`（匿名放行清单）、`session/SessionVerifier`（会话核验 module：过滤器与续期共用的唯一凭证判定，ADR 0044）、`response/AuthenticationRejections`（401 认证家族写出的唯一出口）、`filter/TokenAuthenticationFilter`（接入核验结果并组装权限，失败 401+208）、`filter/TokenLoginFilter`（POST /login，先校验入参再签发双 token）、`custom/RestAuthenticationEntryPoint`（未认证 401+208）、`custom/RestAccessDeniedHandler`（403+205）、`login/`（限流）、`revocation/`（会话吊销） |
| `lautung-oa-model` | `lautung-oa-model/src/main/java/com/lautung/oa/model/` | 实体：`system/SysUser|SysRole|SysMenu|SysUserRole|SysRoleMenu`（`SysRole.dataScope` 五档；`SysUser` 只有 `deptId`，岗位走 `sys_user_post`）、`process/OaProcess|OaProcessRecord…`；请求 DTO 的 jsr303 约束（`com.lautung.oa.vo.*`，ADR 0033） |
| `lautung-oa-auth` | `lautung-oa-auth/src/main/java/com/lautung/oa/auth/` | auth 域垂直切片：`controller/`（index/login、sysUser/Role/Menu）、`service/`（`grant/BackofficeGrant` 后台授予闭包：页面绝对路径 + 按钮点、`service/BackofficeGate` 方法级 gate、`service/PermissionCacheInvalidator` 权限缓存失效）、`mapper/` 与 `mapper/xml/`；`config/AuthMapperConfiguration` 只扫描本域 Mapper |
| `lautung-oa-process` | `lautung-oa-process/src/main/java/com/lautung/oa/process/` | process 域垂直切片：`controller/`、`service/`、`mapper/` + `mapper/xml/`、`approval/ApprovalWriter`、`attachment/`、`cc/`、`deployment/`、`membership/`、`publication/`、`read/`（五种审批列表读唯一入口 `ApprovalListReader`/`LedgerApprovalListReader`：行形状/窗口/回查/排序一处拥有，ADR 0045）、`mapper/xml/OaProcessMapper.xml` 的 `approvalRowLiveness`（手写 XML 不吃 `@TableLogic`，存活过滤每条读显式 include，`OaProcessMapperXmlLivenessTest` 兜住，ADR 0050）；`config/ProcessMapperConfiguration` 扫描 `process.mapper` + `process.attachment`；`process → auth` 直接边保留（3 个 `SysUserService` 调用点） |
| `lautung-oa-server` | `lautung-oa-server/src/main/java/com/lautung/oa/` | 装配模块：启动类 `LautungOaApplication`、`demo/` seeder、`src/main/resources/`（`application*.yml`、Flyway SQL、`db/seed/` 幂等种子、BPMN）、`db/migration/`（Java 迁移，如 V104/V110）、全部 `@SpringBootTest` 合同测试与测试资源 |
| `lautung-oa-coverage-report` | `lautung-oa-coverage-report/` | JaCoCo `report-aggregate`；排除项 `com/lautung/oa/demo/**`、`com/lautung/oa/model/**` |

## 认证与错误语义（改安全相关代码前必读）

请求经过的层，顺序即拒绝优先级：

1. `TokenAuthenticationFilter`：`PublicApiPaths.permitsAnonymous(uri)` 的 URI 跳过校验；其余请求无有效 token → **401 + code 208**；停用账号 → 401 + 209。四连核验（吊销/身份/版本/停用）由 `SessionVerifier` 承担（ADR 0044），过滤器与续期端点共用同一实现。
2. `TokenLoginFilter`：只接 `POST /admin/system/index/login`（POST-only 且不在白名单里，靠过滤器先于授权层接管）。空凭证/畸形体在限流计数之前拒绝（**401 + 208 + 字段消息**，不计入失败次数）；登录失败/锁定/停用 → **401 + 208/209**。
3. `AuthorizationFilter`：白名单 `permitAll`，其余 `authenticated()`。未认证被拒 → `RestAuthenticationEntryPoint`（**401 + 208**，覆盖匿名路径上的错误方法）；已认证被拒 → `RestAccessDeniedHandler`（**403 + 205**）。
4. `IndexController.refresh`：匿名白名单上的续期端点，失败由控制器自己写出认证家族语义（ADR 0037）——缺失/空白/无效/吊销的 refreshToken **401 + 208「认证失败」**，停用用户 **401 + 209**；畸形 JSON 仍按第 6 条的 200+201。
5. MVC 参数解析：`@Valid` 在控制器方法调用前执行，所以**校验先于 `@PreAuthorize`**。未授权 + 畸形体得到校验失败（201），不是 205；良构体才轮到后台 gate。认证早于这一层，所以受保护路由上匿名 + 畸形体仍是 401+208（ADR 0034）。
6. `@PreAuthorize`：拒绝 → `GlobalExceptionHandler` 的 `AccessDeniedException` 分支（**HTTP 200 + code 205**，历史约定）。未知路径 404、方法不匹配 405（均带 `code=201` 信封）；校验失败与缺体/畸形 JSON 是 HTTP 200 + `code=201` + 具体消息（ADR 0033）；其余业务失败 HTTP 200 + `code`。

回归锚点：`LoginAuthContractTest`（401/208/209 全表，含 refresh 失败形状与缺失/空白/无体）、`HttpErrorSemanticsContractTest`（404/405/类型不匹配）、`RequestValidationContractTest`（参数校验 201 文案与顺序契约）、`BackofficeRouteAuthorizationContractTest`（页面/按钮 gate、写端点必须声明按钮点、`/info` 合同）、`BackofficeGrantTest`（页面/按钮/隐藏页授予矩阵）、`FlywayMigrationContractTest`（迁移与 `/info` 种子，用良构体触发）、接口套件 `tests/api/f-http-semantics.spec.ts`、`tests/api/b-menu.spec.ts`（B22 顺序契约）。改本节描述的认证/校验优先级/错误语义家族（security 链、`@PreAuthorize`/`backofficeGate`、201/205/208/209）的轮次，附带重跑安全扫描（`node scripts/security-scan.mjs`，打在跑的演示栈）并当轮定性红项——SEC-GATE 系载荷对这类改动敏感，陈旧红靠周检才发现就太迟（#184）。

## 行政约定

- 业务 `@RequestMapping` 前缀：`/admin/system/*`（认证与配置）、`/admin/process/*`（流程）、`/admin/attachments/*`。
- 静态形状约束写请求 DTO 的 jsr303 注解 + `@Valid`；领域/状态判断留 service；登录在 `TokenLoginFilter` 里手工校验（ADR 0033）；refresh 的失败按认证家族由控制器写 401（ADR 0037），不走 201 校验路线。
- 配置类写请求体是 `model` 的 `vo/{system,process}/XxxRequest`：save 只创建（不含 `id`）、update 只要求 `id`、其余为空即不覆盖；改这两条规则先看 ADR 0034。长度上限对齐列宽、`sys_menu.type` 收 0/1/2（按钮点 perms 必填、path/component 为空、父级必须是页面；save 在 DTO，父级与落库形状在 service 判），见 ADR 0036/0056。
- 审批页面挂在 `/system` 下：目录 `processSet`/`processMgr`，页面绝对路径 `/system/processSet/...`、`/system/processMgr/...`；14 个 process gate 字面量已随之改为绝对路径。
- 后台接口由 `@backofficeGate.isAllowed('<页面绝对路径>'[, '<权限标识>'])` 统一判定（ADR 0052/0056）：读端点只传页面，写端点再传 V110 按钮点；页面先、按钮后，拒绝都是 HTTP 200 + code 205「没有操作权限」。`sys_menu` 全表即后台（type 0/1/2），没有根白名单与旧组件排除；隐藏页不单独构成权限，也不作 gate 目标。
- 后台页面路径一律绝对：`/system/processSet/...`、`/system/processMgr/...`（#221）与 `/system/sysUser`、`/system/sysRole`、`/system/sysMenu`、`/system/assignAuth`（V111，#222）。`/info` 返回 `{name, avatar, roles[], permissions[], menus[]}`，`menus` 只含 type 0/1（`path`/`parentPath`/`title`/`type`/`hidden`/`sort`/`icon`），按钮点只进 `permissions`（ADR 0058）。
- 表结构迁移只加不改：`lautung-oa-server/src/main/resources/db/migration/V*.sql`，Flyway 按版本执行。
- `POST /admin/process/startUp` 的 `idempotencyKey` 可空，唯一索引 `uk_oa_process_idempotency_key` 按用户唯一（ADR 0032）：重复送达返回同一条审批；冲突后的重读必须在 `ApprovalWriter.startUp` 的事务之外（可重复读下事务内看不到对手已提交的行）。
