# Module map

每个 module 的 interface 与职责。改代码或做审查前先看这里，再决定读哪个文件；决策记录在 `docs/adr/`，领域词在 `GLOSSARY.md`。

## 客户端

| module | interface / seam | 职责 |
|---|---|---|
| `src/recognition/index.ts` | `createRecognition(adapter)`；`capture()` / `assertCurrent()` / `recognize()` / `reject()` | 进入身份（generation + entryId）、迟到结果归属与 `/info` 形状校验（`roles`/`permissions`/`menus`） |
| `src/request/request.ts` | `configureRequest(recognition)` → 默认导出 `request`；`createRequestClient` | 纯传输（ADR 0048）:axios 实例、附头、拦截器骨架、200 直通与转发；续期与失败分类外置 |
| `src/request/renewal.ts` | `createRenewal(recognition, resend)` → `onExpired(config, context)`；`setRequestToken` / `isAuthentication` | 续期状态机唯一 owner:单飞去重、换头重放、认证端点豁免；208 的决策与执行（ADR 0048） |
| `src/request/request-failure.ts` | `RequestFailure` / `BusinessRequestFailure` / `PermissionRequestFailure` / `businessFailure` / `transportFailure` / `businessEnvelope` / `rejectRequest` / `refreshRefusal` / `TransportScopeFields` | 请求失败分类与业务信封识别的唯一 owner（#145/#155）；205 是命名失败且不受 `notifyNormalErrors` 抑制（#224）；传输 scope 字段一处拥有，传输与续期共用的失败基座 |
| `src/request/request-scope.ts` | `openRequestScope(recognition, { notifyNormalErrors })` → `request(config, isRelevant?)`、`current()` | 打开时捕获一次身份、按次相关性、错误策略；`ScopedRequestConfig` 只在传输层 |
| `src/list/list-read.ts` | `createListRead({ openScope, toRequest })` → `view` / `read()` / `release()` | 列表读的口令、sequence 与迟到丢弃 |
| `src/list/list-page.ts` | `createListPage({ openScope, toRequest, present, notify, confirm })` → `view` / `rows` / `runWrite` / `confirmRemove` / `settle` | 列表页读后状态、分页、写后刷新与失败策略；浏览器接线在 `src/list/list-page-browser.ts` |
| `src/permission/index.ts` | `hasPermission(perm)`；`hasPermi` 指令 / `createHasPermi(read)`；`isPermitted` / `permissionSet` | 按钮点判定（ADR 0052/0058）：读 user store 的 `permissions`，无权限移除 DOM（`mounted`/`updated`）；服务端 gate 是唯一强制点 |
| `src/navigation/index.ts` | `resolveAuthGuard({ path, fullPath, hasToken, menus })` / `buildLoginRedirectPath` | 导航决策：认证 + 后台页面授予；办事、工作台与公告读面内置始终放行，配置页只放行 `/info` `menus` 覆盖的页面、详情子路径与含已授子页的目录，否则回落到工作台（`getDefaultPostLoginRoute`，ADR 0051） |
| `src/shell/shell-nav.ts` | `SHELL_MODULE_DEFINITIONS` / `buildShellNav(menus)` / `activeModuleKey(path)` / `isShellLeafActive` / `lastTabPathForModule` | 五个一级模块（工作台/办事/系统管理/系统监控/系统工具）的唯一 owner，五项固定不随权限增减：工作台与办事内置，系统管理由 `/info.menus` 派生（无授予时置灰），监控/工具落在 `/monitor`、`/tool` 空态占位；配置页按 `/system/log/`、`/system/processSet/`、`/system/processMgr/` 路径前缀分「日志管理/审批设置/审批管理」，其余平铺；侧栏激活项判定也在这一处 |
| `src/shell/shell-tabs.ts` | `initialShellTabs` / `openShellTab` / `closeShellTab` / `closeCurrentTab` / `closeOtherTabs` / `closeAllTabs` / `isFixedTab` | 标签只存 path + title：工作台固定、同路径去重、关闭当前激活右邻再左邻、全部关闭回工作台；路径带 fullPath 以保留模板编辑的 id 查询 |
| `src/shell/breadcrumb.ts` | `buildBreadcrumb({ path, title, menus })` | 「首页 / 一级 / 二级 / 页面」trail；一级用模块标题，配置页走菜单 parentPath 链，详情与隐藏页回退到列表/可见父级 |
| `src/shell/shell-state.ts` | `useShellStore` → `syncRoute` / `closeTab` / `closeCurrentTab` / `closeOtherTabs` / `closeAllTabs` / `setTheme` / `setDensity` / `toggleCollapsed` / drawer actions | 外壳状态：tabs、activeModule、collapsed、drawer、theme、density；标签不持久化，主题与密度写 localStorage |
| `src/shell/shell-preferences.ts` | `readShellPreferences` / `writeShellTheme` / `writeShellDensity` / `applyShellTheme` | 亮/暗与三档密度的取值校验与持久化；`html.dark` 只在这里切换 |
| `src/shell/AppShell.vue` | 根布局 | 认证路由的唯一 `router-view`：顶栏 + 220/62px 侧栏 + 38px 标签条 + 面包屑 + 16px 内容区；<900px 隐藏标签条、侧栏改 240px 抽屉、配置页顶栏提示「建议桌面」 |
| `src/shell/AppTopBar.vue` / `AppSidebar.vue` / `ShellTabs.vue` / `ShellBreadcrumb.vue` / `ModulePlaceholder.vue` | 外壳 chrome 组件 | 一级药丸导航（无落点的模块置灰）、深色树、卡片标签与关闭下拉、面包屑行、监控/工具空态页；顶栏保留公告铃铛与退出登录，全屏/用户菜单/菜单搜索/主题密度控件留给 #230 |
| `src/shell/NoticeBell.vue` | 公告铃铛 | 从旧 TopBar 抽出的唯一铃铛实例：未读角标（99+）与最近 5 条，点击进详情、查看全部进 `/notice` |
| `src/shell/shell-icons.ts` | `shellIcon(name)` | 菜单 icon 字符串 → 内联 SVG 的白名单（不引图标包），未知回退 Document |
| `src/config/permission.ts` | `configPermission(menus)` → `entryOf` / `coversPage` / `canOccupy` / `grantedMenus` | `/info` 的 `menus`（`BackofficeMenuEntry`）投影；组归属来自 `router/routes.ts`；隐藏页只随可见父页面生效，且自身必须在 `menus` 里 |
| `src/config/sys-role.ts` | `DATA_SCOPE_OPTIONS` / `dataScopeLabel` / `deptOptionsRequest` / `formRules(dataScope)` / `writeRequest` | 角色表单与数据范围：五档标签、`dataScope=2` 时自定部门必填（镜像服务端）、写载荷固定带 `dataScope`/`deptIds`、详情回读映射 |
| `src/config/sys-menu.ts` | `presentAssignableConfigMenus` / `assignMenuLabel` / `collectCheckedIds` / `writeRequest` | 菜单树展示与角色授权：`type=2` 按钮点保留在树里并以 `名称（perms）` 标注、初始勾选逐项还原、按钮行写形状为空 path/component |
| `src/config/oper-log.ts` | `BUSINESS_TYPE_OPTIONS` / `presentQuery` / `presentListRecord` / `presentDetail` / `formatOperParam` / `listRequest` / `detailRequest` / `removeRequest` / `cleanRequest` | 操作日志查询投影（空字段省略、时间范围映射）与请求构造；业务类型 0..7、状态 0/1 标签、列表与详情展示；`oper_param` JSON 缩进、截断/非 JSON 回退原文 |
| `src/config/login-log.ts` | `LOGIN_STATUS_OPTIONS` / `presentQuery` / `presentListRecord` / `listRequest` / `removeRequest` / `cleanRequest` / `unlockRequest` | 登录日志四值（成功/失败/锁定/解锁）标签与查询投影；列表请求、批量删除、清空与按 username 编码的解锁请求 |
| `src/notice/config.ts` | `NOTICE_TYPES` / `NOTICE_STATUSES` / `NOTICE_PERMISSIONS` / `presentFeedItem` / `presentDetail` / `presentAdminListRecord` / `feedQueryForTab` / `feedRequest` / `unreadRequest` / `unreadCountRequest` / `detailRequest` / `markReadRequest` / `imageRequest` / `admin*` 与写侧请求构造 | 公告（ADR 0055）的读侧/管理侧请求形状、类型与状态标签、列表与详情投影；管理侧写请求（create/update/remove/publish/withdraw/upload）留给 #220 复用 |
| `src/notice/api.ts` | `fetchFeed` / `fetchUnread` / `fetchUnreadCount` / `fetchNoticeDetail` / `markNoticeRead` / `fetchNoticeImage` / `findNoticePage` 等 | 薄包装：读侧走 JSON 客户端，`fetchNoticeImage` 走二进制客户端（同源图片带认证头的唯一路径）；管理侧函数由 #220 发布页使用 |
| `src/notice/unread.ts` | `createNoticeUnreadSource(send)` / `createUnreadPolling({ source, clock, intervalMs })` → `state` / `start` / `stop` / `refresh` | 铃铛数据源与轮询时机（立即、60 秒、focus 补拉）：latest-wins、失败静默、识别终态停表；时机用注入时钟单测 |
| `src/notice/unread-browser.ts` | `useNoticeUnread()` / `refreshNoticeUnread()` | 浏览器接线：每次刷新重开请求作用域（关普通错误提示），外壳卸载即停；详情写回执后让角标立即归零 |
| `src/notice/notice-images.ts` | `noticeImageId(src)` / `createNoticeImageHydrator({ loadImage, createObjectUrl, revokeObjectUrl })` → `hydrate` / `revokeAll` / `dispose` | 正文同源图片的认证取回：`/admin/notices/images/{id}` 换成 `blob:`，内容变化撤销、卸载作废，非公告图片不碰；#220 的预览可复用 |
| `src/notice/NoticeContent.vue` | props `html` / `loadImage?` / `createObjectUrl?` / `revokeObjectUrl?` | 全仓唯一 `v-html` 渲染面：服务端写入时已消毒，客户端不再二次消毒；ESLint 只对本文件关 `vue/no-v-html`，`npm run check:v-html` 守卫第二处 |
| `src/notice/quill.ts` | `noticeQuillToolbar({ image? })` / `noticeQuillFormats({ image? })` / `NOTICE_QUILL_TOOLBAR` | Quill 2 工具栏能力的唯一 owner（ADR 0055）：只开 h1–h4、粗斜下划线删除线、有序/无序、引用、代码块、链接、图片、清除格式；`image:false` 供只有 edit 权限的用户隐藏图片按钮 |
| `src/notice/QuillEditor.vue` | props `modelValue` / `readonly` / `showImage` / `placeholder`；`insertImage(url)` | Quill 2 本地包装：Quill 拥有 DOM，不使用 `v-html`；图片按钮只发 `image` 事件，由页面走服务端上传后在光标处插入同源 URL |
| `src/notice/admin.ts` | `createNoticeEditor(adapter)` → `state` / `open` / `updateForm` / `saveDraft` / `publish` / `withdraw` / `remove` / `requestLeave` / `dispose`；`noticeWritePayload` / `emptyNoticeForm` | 发布管理状态机（ADR 0055）：新建/草稿/已发布/已撤回的动作矩阵、创建后 replace 到编辑路径、dirty 快照与离开确认、写载荷只含 title/type/content |
| `src/notice/admin-browser.ts` | `createNoticeEditorPage(router)` | 公告编辑页的浏览器接线：HTTP、确认框、提示、路由跳转/替换与编辑路径 |
| `src/views/config/notice/index.vue` | 页面 | 系统管理 > 通知公告：标题/类型/状态筛选与分页；编辑/发布(再次发布)/撤回/删除动作按状态与 `v-hasPermi` 收口；删除与发布/撤回二次确认 |
| `src/views/config/notice/edit.vue` | 页面 | 公告编辑：标题/类型/Quill 2 正文、编辑/预览两个 tab（预览经 `NoticeContent` 水合图片）、按状态显示保存草稿/发布/撤回/删除；路由离开与返回走 dirty 确认 |
| `src/views/notice/index.vue` | 页面 | 全部公告：全部/未读两个 tab、`publish_time DESC, id DESC` 分页、类型/标题/发布人/发布时间/读态；读状态与分页复用 `createListPage` |
| `src/views/notice/detail.vue` | 页面 | 公告详情：类型/标题/发布人/发布时间/正文；加载成功后每次访问只写一次已读回执；撤回/删除/不存在按不存在视图处理 |
| `src/router/paths.ts` | `PATHS` / `ROUTES` / `workDetailPath` / `noticeDetailPath` / `noticeEditPath` / `returnPathOfRoute` / `workListKindOf` | 路径与返回路径的唯一 owner；路由表从这里派生子路径；工作台是 `/workbench`（登录落点），监控/工具空态是 `/monitor`、`/tool`，审批页面在 `/system/processSet`、`/system/processMgr` 下，日志页面在 `/system/log/operLog`、`/system/log/loginLog`，公告读面是内置 `/notice` 与 `/notice/:id`，发布管理是 `/system/notice` 与隐藏编辑 `/system/notice/edit/:id?` |
| `src/router/routes.ts` | `constantRoutes` / `ROUTE_GROUP_ENTRIES` | 路由表与组归属：`/login` 在外，所有认证路由挂在唯一的 `/` AppShell 下（子路由保留绝对路径语义与既有 name/meta），`/monitor`、`/tool` 是无授予的模块空态占位；`/` 重定向与守卫回落都是工作台，`/work` 自身仍回待处理；公告读路由没有 `menuGroup`，发布管理页与编辑子路由属于 `config` 组（编辑子路由靠已授 `/system/notice` 前缀通过 #224 守卫） |
| `src/router/guard.ts` | `setupNavigation(router, pinia, recognition)` | 路由生命周期接线：NProgress、标题、`recognition.recognize`、`beforeEach`；认证决策在 `src/navigation` |
| `src/surface/client-surface.ts` | `resolveClientSurface({ path, hasToken, menus })` | 只剩后台页授予判定与返回路径；不再按视口占用/回落（ADR 0051），外壳不路由经过它，#231 删除 |
| `src/config/assign-roles.ts` | `createAssignRoles(adapter)` → `open(target)` → lease（`requestLeave` / `environmentChanged` / `released` / `dispose`） | 分配角色的 phase machine、待确认记录与强制离开；浏览器接线在 `assign-roles-browser.ts` |
| `src/config/form-rules.ts` | `requiredRule(message)` → `{ required, validator }` | 配置表单必填语义的唯一 owner：字符串 trim 判空（镜像 `@NotBlank`）、数值缺省判空（镜像 `@NotNull`）；文案由各 config module 的 `formRules()` 提供并镜像服务端 DTO（ADR 0038） |
| `src/config/template-editor.ts` | `createTemplateEditor(options)` → `open(id)`（`requestLeave` / `environmentChanged` / `dispose`）+ `formRules()` | 审批模板编辑的保存/待确认/占用；`occupancy()` 是端口；必填文案在 `formRules()` |
| `src/work/work-list.ts` | `presentWorkList` / `workListDefinition` / `workListRequest` / `workListPerspectiveFromRoute` / `summaryFields` | 办事四种列表的呈现与视角；文案在这里，路径在 `router/paths` |
| `src/work/workbench.ts` | `formatWorkbenchDate` / `pendingHeadline` / `workbenchTotals` / `workbenchKpis` / `workbenchRecent` / `workbenchNotices` / `workbenchRequests` / `presentWorkbench` | 工作台首屏投影：四张 KPI 取现有办事列表 `total`（不新增统计接口），近期待处理前 5、未读公告前 5 与 2×2 快捷入口；「今日已办/当天到期」没有接口口径，卡面写「已处理」 |
| `src/views/workbench/index.vue` | 页面 `/workbench` | 登录落点：页头日期与待处理文案、KPI 卡、近期审批表、公告栏、快捷入口；一次页面打开一个请求作用域（关普通错误提示），公告复用 #219 `src/notice/unread.ts` 的读数源做首屏一次读取（60 秒轮询仍只在顶栏铃铛），8 行骨架、空态 CTA 与可重试错误态；KPI、近期行、公告与快捷入口都可导航，不缓存数据 |
| `src/work/form-values.ts` | `formFields` / `formFieldText` | 表单值转显示文本的唯一入口；用户输入只作字符（ADR 0028） |
| `src/work/approval-start.ts` | `createApprovalStart(adapter)` → `open(templateId)`（`draft` / `edit` / `submit` / `add` / `remove`） | 发起草稿与附件草稿的 owner、phase、每次尝试的提交标识（重新发起换新）；浏览器接线在 `approval-start-browser.ts`。表单渲染与必填校验是 form-create 内建、项目零转换：required provider 与消息在 `@form-create/element-ui/src/core/provider.js`，`$required`/`effect.required` 的读取在 `@form-create/core/src/handler/effect.js`，空值定义 `is.empty` 在 `@form-create/utils/lib/type.js`（服务端按 ADR 0035 镜像这套语义） |
| `src/work/approval-detail.ts` | `createApprovalDetail(adapter)` → `open(target)`（`decide` / `endRecovery` / `reloadAttachments`） | 审批详情与处理结果待确认；HTTP 接线在 `approval-detail-http.ts` |
| `src/request/submission-outcome.ts` | `classifySubmissionFailure(error, action)` | 明确拒绝 vs 待确认的分类 |
| `src/config/process-template.ts` | `publish` / `removeUnpublished` / `findStartable` / `presentDesignerState` | 模板列表动作与可发起清单 |

日志管理两个页面 `src/views/config/operLog/index.vue`、`src/views/config/loginLog/index.vue` 只做筛选、表格、详情/确认框接线：读状态、分页与写后刷新走 `createListPage`，按钮点走 `v-hasPermi`；详情只在操作日志页，按 `presentDetail` 的文本展示，不使用 `v-html`。

公告展示面（#219）：`src/shell/NoticeBell.vue` 的铃铛只消费 `src/notice/unread-browser.ts` 的未读计数与最近 5 条；`/notice` 列表与 `/notice/:id` 详情是内置读面（登录即有，不设菜单授予）。正文只经 `NoticeContent.vue` 渲染，图片经 `notice-images` 认证取回后显示为 `blob:`。发布管理面（#220）：`/system/notice` 列表与隐藏子路由 `/system/notice/edit/:id?` 属于配置组，页面状态机在 `src/notice/admin.ts`，Quill 工具栏能力在 `src/notice/quill.ts`；图片上传端点由服务端 `system:notice:add` 把关，只有 edit 权限时编辑页隐藏图片按钮。

## 服务端鉴权

| module | interface | 职责 |
|---|---|---|
| `common.jwt.JwtHelper` | `createTokenPair` / `createAccessToken` / `readAccessToken` / `readRefreshToken` → `TokenClaims(userId, username, sessionId, tokenVersion, expiresAt)` | 一次登录的一对 access/refresh 共享 sessionId 与用户行的 token_version；无 sessionId 的令牌不可鉴权 |
| `security.revocation.TokenRevocation` | `isRevoked(sessionId)` / `revoke(sessionId)` | 退出的按会话吊销表；`RedisTokenRevocation` 是唯一实现，标记 TTL 与 refresh 同寿，过滤器与 refresh 共用 |
| `security.login.LoginAttemptLimiter` | `isBlocked(username, source)` / `recordFailure(username, source, userAgent)` / `recordSuccess` / `unlockAccount(username)` | 登录失败的两个计数维度（提交的账号、来源地址）；`unlockAccount` 只清账号维度的三个键并审计，来源维度不提供人工解锁；`RedisLoginAttemptLimiter` 是唯一实现，策略在 `LoginAttemptPolicy` |
| `security.login.LoginAudit` | `record(LoginEvent)` / `recordLogOnly(LoginEvent)`；`LoginEvent(type, username, sourceAddress, userAgent, msg)`，`LoginEventType` 四值 0 成功/1 失败/2 锁定/3 解锁 | 登录审计端口：`record` 持久化（库是查询真相），`recordLogOnly` 只写应用日志（被锁定挡下的尝试）；DB 实现在 audit（`CompositeLoginAudit` = `DatabaseLoginAudit` + `Slf4jLoginAudit` 兜底），security 单独使用时退回日志实现 |

## 服务端审批

| module | interface | 职责 |
|---|---|---|
| `process.approval.ApprovalEngine` | `start` / `complete` / `rejectToEnd` + `pendingTasks` / `processedTasks` / inherited `currentTasks` / `hasHistoricAssignee` | 唯一的审批运行时 Flowable 读写 port；`FlowableApprovalEngine` 是唯一实现，`ProcessEngineApproval` 是运行时唯一 Flowable 调用点（部署期触点见 `process.deployment`，#188） |
| `process.deployment.ProcessDeploymentEngine` | `definitionById` / `deployVerified` | 部署动作 port：按 definitionId 查定义、按路径与 key 校验部署（部署期摘要前后校验、内容寻址复查、定义计数、挂起检查与文案随迁在此，#188）；`FlowableProcessDeploymentEngine` 是唯一实现与部署期 Flowable 触点 |
| `process.publication.PublicationVerifier` | `verify(template, publication)` → `Verdict`（`usable` / `definition`；`Binding` 三态 `DefinitionMissing` / `Inconsistent` / `Usable`） | 发布判定唯一入口：发布、恢复、可发起共用同一判定，证据问题理由与绑定不一致的内部理由由读侧消费方归并成同一条对外文案；不抛业务异常、不直接触 Flowable，引擎交互只走 `process.deployment` port（#189） |
| `process.read.ApprovalListReader` | `pending` / `processed` / `started` / `carbonCopied` / `instanceQuery`（参数强类型） | 五种审批列表读的唯一入口：行形状（`OaProcessMapper.xml` 共享列清单，`ProcessVo` 唯一行类型）、窗口（`PageWindow`）、回查批量化、排序一处拥有；pending/processed 仍由引擎分页与计数，tiebreaker 与引擎 businessKey 批量收在 port 实现内（ADR 0045） |
| `process.approval.ApprovalWriter` | `startUp` / `startedOutcome` / `approve` | 写入次序、事务边界、附件绑定与回滚补偿；同一提交标识重复送达返回同一条审批（ADR 0032），冲突重读只在事务外调用 `startedOutcome` |
| `process.membership.ApprovalMembership` / `ApprovalMembershipResolver` | `resolve(user, process)` → `canRead` / `canUpload` / `canAct` | 审批成员资格；证据接口 `MembershipEvidence` 由 engine 继承，扩展来自 `ProcessMembershipExtension` |
| `auth.grant.BackofficeGrant` | `snapshot(userId)` → `hasAnyPage` / `covers` / `allows` / `menus` / `permissions`；`isBackofficeMenu` / `isGrantableBackofficeMenu` / `visiblePageParentId` | 后台授予的唯一闭包：页面绝对路径 + 仅随已授页面生效的按钮点；`visiblePageParentId` 供授予保存判定隐藏页能否落库；gate、`/info` 与角色授权共用 |
| `auth.service.BackofficeGate` | `isAllowed(pagePath[, permission])` | 各后台 controller 的方法级 gate：读只判页面，写加判按钮点 |

## 服务端公告（#218 的稳定合同，供 #219/#220）

服务端合同由 #218 落地；客户端展示面（铃铛、`/notice`、`/notice/:id`、唯一 `v-html` 渲染面与图片认证取回）由 #219 落地，发布管理页 `/system/notice` 由 #220 落地（ADR 0055/0028）。客户端按下面形状接线，不要在页面里重新猜字段：

- 读侧（登录即有，不设页面授予、不套数据范围）：
  - `GET /admin/notices/feed?page=1&limit=20&unreadOnly=false` → `{code,message,data:{current,size,total,records:[{id,title,type,publisherName,publishTime,read}]}}`；排序 `publishTime DESC, id DESC`；`limit` 上限 500；列表没有 `content`。
  - `GET /admin/notices/unread` → `data:[…同上行…]`，最多 5 条未读。
  - `GET /admin/notices/unread-count` → `data:<number>`（撤回/删除立即排除）。
  - `GET /admin/notices/{id}` → `data:{id,title,type,content,publisherId,publisherName,publishTime,read}`；撤回/删除/不存在时没有 `data`（信封仍是 200）。GET 不写回执。
  - `POST /admin/notices/{id}/read` → 打开详情成功后显式调用；幂等；撤回/删除返回 201「公告不存在」。
  - `GET /admin/notices/images/{id}` → 二进制，`Content-Type` 是存储类型且带 `X-Content-Type-Options: nosniff`；无权 205、不存在 201。正文里的图片地址只允许 `/admin/notices/images/{id}`。
- 管理侧（页面 `/system/notice`；按钮 `system:notice:add|edit|remove|publish`，`publish` 同时管撤回，没有 `:query`）：`GET /admin/system/notices`（`page/limit/title/type/status`，行不含 `content`）、`GET /admin/system/notices/{id}`（含草稿正文，编辑页必需）、`POST /admin/system/notices`、`PUT /admin/system/notices/{id}`、`DELETE /admin/system/notices/{id}`、`POST .../{id}/publish`、`POST .../{id}/withdraw`、`POST /admin/system/notices/images`（multipart 字段 `file`，响应 `{id,url}`）。
- 类型固定 `1 通知 / 2 公告`；状态 `0 草稿 / 1 已发布 / 2 已撤回`。已发布可编辑；已撤回不可编辑，只能再发布（刷新 `publishTime` 与发布人快照）；删除等价于隐式撤回。

## 约定

- 页面只做绑定与文案；迟到归属、phase、强制离开、写后刷新都在 module 里。
- 测试分两层：module 测试是主，规则打在 interface 上；view 挂载测试只覆盖模板状态分支与命令式接线，状态由 seam mock 驱动，不靠 view 测试重新断言 module 内部规则。
- 共享挂载工具在 `tests/unit/support/harness.js`（假 recognition、pinia 会话、axios adapter、Element Plus stub）；全局 setup 在 `tests/unit/setup.js`（auto-unmount、nprogress stub）。新测试直接复用，不要各写一份。
- 每个模块的决策理由看对应 ADR（0011–0022），不要从页面反推 interface。
