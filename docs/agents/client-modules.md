# Module map

每个 module 的 interface 与职责。改代码或做审查前先看这里，再决定读哪个文件；决策记录在 `docs/adr/`，领域词在 `GLOSSARY.md`。

## 客户端

| module | interface / seam | 职责 |
|---|---|---|
| `src/recognition/index.ts` | `createRecognition(adapter)`；`capture()` / `assertCurrent()` / `recognize()` / `reject()` | 进入身份（generation + entryId）与迟到结果归属 |
| `src/request-scope.ts` | `openRequestScope(recognition, { notifyNormalErrors })` → `request(config, isRelevant?)`、`current()` | 打开时捕获一次身份、按次相关性、错误策略；`ScopedRequestConfig` 只在传输层 |
| `src/list-read.ts` | `createListRead({ openScope, toRequest })` → `view` / `read()` / `release()` | 列表读的口令、sequence 与迟到丢弃 |
| `src/list-page.ts` | `createListPage({ openScope, toRequest, present, notify, confirm })` → `view` / `rows` / `runWrite` / `confirmRemove` / `settle` | 列表页读后状态、分页、写后刷新与失败策略；浏览器接线在 `list-page-browser.ts` |
| `src/config-permission.ts` | `configPermission(menus)` → `entryOf` / `canOccupy` / `grantedConfigMenus` | 配置授予的客户端投影；组归属来自 `router/routes.ts` |
| `src/router/paths.ts` | `PATHS` / `ROUTES` / `workDetailPath` / `returnPathOfRoute` / `workListKindOf` | 路径与返回路径的唯一 owner；路由表从这里派生子路径 |
| `src/router/routes.ts` | `constantRoutes` / `ROUTE_GROUP_ENTRIES` | 路由表与组归属 |
| `src/surface/client-surface.ts` | `resolveClientSurface({ path, viewportWidth, hasToken, menus })` | 占用、密度与返回路径；不决定编辑生命周期 |
| `src/config/assign-roles.ts` | `createAssignRoles(adapter)` → `open(target)` → lease（`requestLeave` / `environmentChanged` / `released` / `dispose`） | 分配角色的 phase machine、待确认记录与强制离开；浏览器接线在 `assign-roles-browser.ts` |
| `src/config/form-rules.ts` | `requiredRule(message)` → `{ required, validator }` | 配置表单必填语义的唯一 owner：字符串 trim 判空（镜像 `@NotBlank`）、数值缺省判空（镜像 `@NotNull`）；文案由各 config module 的 `formRules()` 提供并镜像服务端 DTO（ADR 0038） |
| `src/config/template-editor.ts` | `createTemplateEditor(options)` → `open(id)`（`requestLeave` / `environmentChanged` / `dispose`）+ `formRules()` | 审批模板编辑的保存/待确认/占用；`occupancy()` 是端口；必填文案在 `formRules()` |
| `src/work/work-list.ts` | `presentWorkList` / `workListDefinition` / `workListRequest` / `workListPerspectiveFromRoute` / `summaryFields` | 办事四种列表的呈现与视角；文案在这里，路径在 `router/paths` |
| `src/work/form-values.ts` | `formFields` / `formFieldText` | 表单值转显示文本的唯一入口；用户输入只作字符（ADR 0028） |
| `src/work/approval-start.ts` | `createApprovalStart(adapter)` → `open(templateId)`（`draft` / `edit` / `submit` / `add` / `remove`） | 发起草稿与附件草稿的 owner、phase、每次尝试的提交标识（重新发起换新）；浏览器接线在 `approval-start-browser.ts`。表单渲染与必填校验是 form-create 内建、项目零转换：required provider 与消息在 `@form-create/element-ui/src/core/provider.js`，`$required`/`effect.required` 的读取在 `@form-create/core/src/handler/effect.js`，空值定义 `is.empty` 在 `@form-create/utils/lib/type.js`（服务端按 ADR 0035 镜像这套语义） |
| `src/work/approval-detail.ts` | `createApprovalDetail(adapter)` → `open(target)`（`decide` / `endRecovery` / `reloadAttachments`） | 审批详情与处理结果待确认；HTTP 接线在 `approval-detail-http.ts` |
| `src/submission-outcome.ts` | `classifySubmissionFailure(error, action)` | 明确拒绝 vs 待确认的分类 |
| `src/config/process-template.ts` | `publish` / `removeUnpublished` / `findStartable` / `presentDesignerState` | 模板列表动作与可发起清单 |

## 服务端鉴权

| module | interface | 职责 |
|---|---|---|
| `common.jwt.JwtHelper` | `createTokenPair` / `createAccessToken` / `readAccessToken` / `readRefreshToken` → `TokenClaims(userId, username, sessionId, tokenVersion, expiresAt)` | 一次登录的一对 access/refresh 共享 sessionId 与用户行的 token_version；无 sessionId 的令牌不可鉴权 |
| `security.revocation.TokenRevocation` | `isRevoked(sessionId)` / `revoke(sessionId)` | 退出的按会话吊销表；`RedisTokenRevocation` 是唯一实现，标记 TTL 与 refresh 同寿，过滤器与 refresh 共用 |
| `security.login.LoginAttemptLimiter` | `isBlocked(username, source)` / `recordFailure` / `recordSuccess` | 登录失败的两个计数维度（提交的账号、来源地址）；`RedisLoginAttemptLimiter` 是唯一实现，策略在 `LoginAttemptPolicy` |
| `security.login.LoginAudit` | `loginFailed` / `loginLocked` / `loginUnlocked` | 失败、锁定、解锁的审计轨迹；`Slf4jLoginAudit` 写结构化应用日志，字段脱敏且不含密码或令牌 |

## 服务端审批

| module | interface | 职责 |
|---|---|---|
| `process.approval.ApprovalEngine` | `start` / `complete` / `rejectToEnd` + `pendingTasks` / `processedTasks` / inherited `currentTasks` / `hasHistoricAssignee` | 唯一的审批运行时 Flowable 读写 port；`FlowableApprovalEngine` 是唯一实现，`ProcessEngineApproval` 是运行时唯一 Flowable 调用点（部署期触点见 `process.deployment`，#188） |
| `process.deployment.ProcessDeploymentEngine` | `definitionById` / `deployVerified` | 部署动作 port：按 definitionId 查定义、按路径与 key 校验部署（部署期摘要前后校验、内容寻址复查、定义计数、挂起检查与文案随迁在此，#188）；`FlowableProcessDeploymentEngine` 是唯一实现与部署期 Flowable 触点 |
| `process.read.ApprovalListReader` | `pending` / `processed` / `started` / `carbonCopied` / `instanceQuery`（参数强类型） | 五种审批列表读的唯一入口：行形状（`OaProcessMapper.xml` 共享列清单，`ProcessVo` 唯一行类型）、窗口（`PageWindow`）、回查批量化、排序一处拥有；pending/processed 仍由引擎分页与计数，tiebreaker 与引擎 businessKey 批量收在 port 实现内（ADR 0045） |
| `process.approval.ApprovalWriter` | `startUp` / `startedOutcome` / `approve` | 写入次序、事务边界、附件绑定与回滚补偿；同一提交标识重复送达返回同一条审批（ADR 0032），冲突重读只在事务外调用 `startedOutcome` |
| `process.membership.ApprovalMembership` / `ApprovalMembershipResolver` | `resolve(user, process)` → `canRead` / `canUpload` / `canAct` | 审批成员资格；证据接口 `MembershipEvidence` 由 engine 继承，扩展来自 `ProcessMembershipExtension` |
| `auth.grant.ConfigurationGrant` | `snapshot(userId)` → `hasAnyPage` / `covers` / `entries`；`isConfigurationMenu` | 配置授予的唯一闭包；gate、`/info` 与角色授权共用 |
| `auth.service.ConfigurationMenuGate` | `isGranted(...)` | 六个配置 controller 的方法级 gate |

## 约定

- 页面只做绑定与文案；迟到归属、phase、强制离开、写后刷新都在 module 里。
- 测试分两层：module 测试是主，规则打在 interface 上；view 挂载测试只覆盖模板状态分支与命令式接线，状态由 seam mock 驱动，不靠 view 测试重新断言 module 内部规则。
- 共享挂载工具在 `tests/unit/support/harness.js`（假 recognition、pinia 会话、axios adapter、Element Plus stub）；全局 setup 在 `tests/unit/setup.js`（auto-unmount、nprogress stub）。新测试直接复用，不要各写一份。
- 每个模块的决策理由看对应 ADR（0011–0022），不要从页面反推 interface。
