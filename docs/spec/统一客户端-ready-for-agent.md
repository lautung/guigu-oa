## Problem Statement

云尚办公目前拆成两个前端：PC 管理端（配置）和公众号员工 H5（办事）。用户要在不同入口、不同认证方式（管理端用户名密码 vs 员工端微信 OAuth）之间切换；配置与办事被当作两个产品。侧栏与菜单体系不统一，微信相关能力（OAuth、绑手机、公众号菜单、模板消息）与目标产品方向不符。需要一份可交给实现代理执行的规格，把两端收成**统一客户端**：人人默认可办事（手机浏览器必须能完成），配置权限另授，侧栏按权限出菜单，待处理即通知，删除微信整段，沿用 Activiti。

## Solution

构建一个 Web **统一客户端**，替换现有 `guigu-oa-admin` 与 `guigu-oa-web`：

- **单一登录**：用户名密码；登录成功后一律落到**待处理**
- **混合侧栏**：办事五项前端内置、人人可见；配置项来自 `sys_menu` + 角色，仅有配置权限时出现；两组合并为一棵树
- **按路由分壳**：办事路由用移动可用 layout；配置路由用桌面 layout；手机 viewport 访问配置路由时硬拦截
- **办事**：发起审批、待处理、已处理、已发起、基本信息——复用现有 `/admin/process/*` 员工向 API
- **配置**：用户、角色、菜单、审批类型、审批模板（三步向导 + zip 上传 + 发布）、审批实例查询——复用现有管理端 API
- **删除**：微信 OAuth、绑手机、公众号菜单 UI 与后端、`/wechat/*`、模板消息推送

## User Stories

### 登录与会话

1. As a **用户**，I want to log in with username and password, so that I can access 云尚办公 without WeChat.
2. As a **用户**，I want to land on **待处理** after login on both desktop and mobile, so that I immediately see what needs my action.
3. As a **用户**，I want to see my username/avatar and log out from the top bar, so that I can manage my session without duplicate navigation.
4. As a **用户**，I want to be redirected to login when my session expires, so that unauthorized access is prevented.
5. As a **用户**，I want to return to my intended page after logging in (when redirected from a protected route), so that deep links work.

### 侧栏与菜单

6. As a **用户**，I want to see the **办事** group in the sidebar always, so that I can do my work without extra permission.
7. As a **用户 with 配置权限**，I want to see the **配置** group in the sidebar below 办事, so that I can manage the system when authorized.
8. As a **用户 without 配置权限**，I want the 配置 group hidden entirely, so that I am not confused by entries I cannot use.
9. As a **用户**，I want the sidebar to show one merged tree (办事 + 配置), not two workspace tabs, so that navigation feels like one product.
10. As a **用户 on mobile**，I want all 办事 entries fully visible in the sidebar, so that I can complete work on my phone.
11. As a **用户 on mobile with 配置权限**，I want the 配置 group collapsed at the bottom with a hint to use desktop, so that I understand config is not for phone.
12. As a **用户**，I want 发起审批、待处理、已处理、已发起、基本信息 as fixed 办事 entries not editable in 菜单管理, so that core workflows are always available.

### 手机与桌面访问控制

13. As a **用户 on mobile**，I want to be blocked with a full-screen message when navigating to any 配置 route, so that I cannot accidentally use desktop-only features on phone.
14. As a **用户 on desktop**，I want to use all 配置 pages normally, so that I can set up the office.
15. As a **用户 on mobile**，I want 办事 pages to render in a mobile-friendly layout, so that forms and lists are usable on small screens.
16. As a **用户 on desktop**，I want 办事 pages to work in the desktop shell, so that I can also process approvals on PC.

### 发起审批

17. As a **用户**，I want to open 发起审批 from the sidebar and see templates grouped by 审批类型, so that I can find the right form quickly.
18. As a **用户**，I want to see only **已发布** templates when initiating, so that drafts are not accidentally used.
19. As a **用户**，I want to tap a template and fill a dynamic form rendered from the template definition, so that each approval type has its own fields.
20. As a **用户**，I want to submit the form and start the approval flow, so that my request enters the workflow.
21. As a **用户**，I want confirmation after successful submission, so that I know the approval was created.

### 待处理

22. As a **用户**，I want **待处理** to be my default landing page and act as my notification center, so that I do not need a separate bell or message channel.
23. As a **用户**，I want a paginated list of approvals waiting for me, so that I can work through my queue.
24. As a **用户**，I want list items to show summary fields (formShowData semantics), with image fields optionally hidden in the list, so that I can scan items quickly.
25. As a **用户**，I want to open a pending item and see title, time, status, form fields, and a vertical 审批流转记录 (operator + description + time), so that I have full context before acting.
26. As a **用户**，I want status displayed as 审批中 / 审批通过 / 审批拒绝 (status 1 / 2 / -1), so that state is clear.
27. As a **用户**，I want to approve or reject from the detail page without entering a rejection reason, so that action is fast.
28. As a **用户**，I want the流转记录 to show 「已通过」 or 「驳回」 after my action, so that the audit trail is visible.
29. As a **用户**，I want to be taken to **已处理** after approving or rejecting, so that I can continue with completed work.

### 已处理

30. As a **用户**，I want a paginated list of approvals I have already processed, so that I can review my past decisions.
31. As a **用户**，I want to open an item in read-only detail, so that I can verify what I did.

### 已发起

32. As a **用户**，I want a paginated list of approvals I started, so that I can track my own requests.
33. As a **用户**，I want to open an item in read-only detail with flow history, so that I can see progress on my submissions.

### 基本信息

34. As a **用户**，I want to open 基本信息 from the 办事 group and see my name and phone only, so that I can confirm my profile without admin-only fields.
35. As a **用户**，I want 基本信息 not duplicated in the top bar, so that navigation stays clean.

### 配置权限

36. As an **管理员（via 配置权限）**，I want 配置权限 granted through role menu assignment (at least one 配置类 sys_menu), so that there is no separate "admin account type".
37. As an **管理员**，I want to assign menus to roles via the existing menu tree UI, so that authorization stays familiar.
38. As a **用户 without 配置权限**，I want to use all 办事 features without restriction, so that everyone can participate in approvals by default.

### 用户管理

39. As an **管理员**，I want to list, create, edit, delete, and enable/disable users, so that accounts are managed centrally.

### 角色管理

40. As an **管理员**，I want to list, create, edit, and delete roles, so that permission bundles are maintainable.
41. As an **管理员**，I want to open 菜单授权 from a role and check/uncheck the menu tree (including 配置 entries), so that I control who sees config.

### 菜单管理

42. As an **管理员**，I want to CRUD the menu tree for **配置类** entries only, so that 办事 built-ins are not accidentally removed from the product.
43. As an **管理员**，I want the menu tree to preserve existing hierarchy (组织与权限 / 审批设置 / 审批查询), so that structure matches the admin app.

### 审批类型

44. As an **管理员**，I want to list, create, edit, and delete 审批类型, so that templates can be categorized.

### 审批模板

45. As an **管理员**，I want to create a template through a three-step wizard (基本设置 → 表单设置 → 流程设置), so that setup is guided.
46. As an **管理员**，I want to set type, name, icon, and description in step one, so that the template is identifiable.
47. As an **管理员**，I want to design the form visually in step two, so that initiators see the right fields.
48. As an **管理员**，I want to upload an Activiti process zip in step three, so that workflow is defined without the online iframe designer.
49. As an **管理员**，I want to save a template as draft (未发布), so that I can iterate before go-live.
50. As an **管理员**，I want to edit and delete unpublished templates, so that mistakes can be fixed before release.
51. As an **管理员**，I want to publish an unpublished template from the list, so that it becomes visible in 办事 发起审批.
52. As an **管理员**，I want published templates to appear for initiators but remain governed by draft/publish rules on the admin side, so that production flows are controlled.

### 审批实例查询

53. As an **管理员**，I want to search approval instances by keyword, status, and time range with pagination, so that I can audit and support users.
54. As an **管理员**，I want to open instance detail read-only, so that I can inspect any running or completed approval.

### 移除与迁移

55. As a **产品负责人**，I want WeChat OAuth, bind-phone, 公众号菜单 config, and template-message push removed, so that the product is web-only.
56. As a **产品负责人**，I want no Dashboard placeholder homepage, so that the app opens on real work (待处理).
57. As a **产品负责人**，I want department, post, and log modules out of scope, so that we ship the merge without unfinished course modules.

## Implementation Decisions

### 应用结构

- Replace both existing frontends with **one** Vue application and **one** route table.
- Split **layout shells** by route class: 办事 routes use mobile-capable shell; 配置 routes use desktop shell.
- Post-login redirect: always **待处理** (not Dashboard, not 发起审批 home).

### 认证

- Sole login: `POST /admin/system/index/login` (username + password), same as current admin.
- Session bootstrap: `GET /admin/system/index/info` for user info, dynamic menus, buttons.
- Employee H5 WeChat OAuth flow is **removed**; all users authenticate the same way.

### 配置权限模型

- No new boolean "can configure" field on user.
- **配置权限** = user's roles grant at least one 配置类 entry in `sys_menu`.
- Authorization UI unchanged: 角色管理 → 菜单授权 (assignAuth).

### 侧栏数据模型

| 分组 | 来源 | 可见性 |
|------|------|--------|
| 办事 | Frontend built-in (5 items) | Always; not in 菜单管理 CRUD |
| 配置 | `sys_menu` filtered by role | Only when user has 配置权限 |

- Merge both sources into **one sidebar tree**; same merge logic on mobile and desktop (presentation differs).
- 办事 built-in order: 发起审批, 待处理, 已处理, 已发起, 基本信息.
- 配置 groups (from sys_menu hierarchy): 组织与权限 (用户/角色/菜单), 审批设置 (类型/模板), 审批查询 (实例列表).
- Exclude from config: 公众号菜单, Dashboard, 部门/岗位/日志.
- assignAuth remains a hidden route from 角色管理.

### Viewport policy

- Mobile: 办事 sidebar fully expanded; 配置 group at bottom, **collapsed by default**, with copy "请在电脑浏览器使用".
- Mobile navigation to any 配置 route: **hard intercept** full-screen "请在电脑浏览器使用"; do not render config page.
- Desktop: both groups usable; config pages render normally.

### 办事 API reuse

- Reuse existing employee-facing endpoints under `/admin/process/*`: findProcessType, getProcessTemplate, startUp, findPending/Processed/Started, show, approve, getCurrentUser.
- List/detail field semantics follow existing formShowData; list may hide image fields.
- Rejection does not require reason; record shows 已通过 or 驳回.

### 配置 API reuse

- System: `/admin/system/sysUser`, `sysRole`, `sysMenu`.
- Process config: `/admin/process/processType`, `processTemplate` (including uploadProcessDefinition, publish).
- Instance query: `GET /admin/process/{page}/{limit}`.

### 审批模板

- Three-step wizard: 基本设置 → 表单设置 (visual designer) → 流程设置 (Activiti **zip upload**).
- Publish from list for 未发布 templates; only 已发布 visible in 办事.
- **Online Activiti iframe designer (onlineProcessSet) is out of spec**; zip upload is the committed path.

### 删除项

- Frontend: admin `/wechat/*` views; employee WeChat auth in App.vue/request.js; `/about`, `/test` routes.
- Backend: `/admin/wechat/*` controllers and related services (OAuth, bindPhone, menu sync, template message push).
- Product: 公众号 as channel; openId-based identity; separate 管理端/员工端 product split.

### 待定（实现时决策，不进规格承诺）

- Whether API paths remain under `/admin` prefix.
- Data cleanup for `openId`, `wechat_menu` tables.
- Exact mobile layout component reuse vs rewrite from H5.

## Testing Decisions

### 原则

- Test **external behavior** only: what the user sees and can do, not Vue component internals or store implementation details.
- Prefer the **highest seam** with the fewest touch points across the codebase.

### 测试接缝（单一接缝）

**统一客户端导航与访问策略模块** — one module responsible for:

1. Composing the merged sidebar from 办事 built-ins + role-filtered 配置 menus
2. Deriving whether the current user has 配置权限
3. Classifying routes as 办事 vs 配置
4. Applying viewport policy (mobile config hard block; mobile 配置 group collapsed)
5. Resolving post-login default route (待处理)

Tests target this module's **public API** (pure functions or facades): given user menu payload, viewport width, and target route → expected sidebar shape, visibility flags, allow/deny navigation, default redirect.

**Why this seam:** Most novel unified-client logic lives here; neither legacy app has it. Backend Activiti tests (`ProcessTest`) cover engine deployment, not client IA. Existing frontend unit tests are scaffold-only (utils, Breadcrumb).

### 测试类型与范围

| 层 | 测什么 | 不测什么 |
|----|--------|----------|
| 导航与访问策略模块 | 无配置权限时配置组隐藏；有权限时合并树正确；手机点配置路由返回拦截；登录默认待处理；办事五项始终可见 | 具体 Vue 组件 DOM |
| （可选，实现后）关键路径 E2E | 登录→待处理；发起→提交；待处理→通过→已处理；管理员发布模板后办事可见 | 微信已删路径 |

Prior art: admin `tests/unit/utils/*.spec.js`, `tests/unit/components/*.spec.js` (Jest); backend `ProcessTest.java` (SpringBootTest + Activiti).

## Out of Scope

- 微信小程序、公众号（网页授权、模板消息、自定义菜单、配置里的公众号菜单）
- 部门、岗位、操作/登录日志（课程未完成，无完整 UI/API）
- 更换 Activiti 或重做流程设计器
- Activiti 在线 iframe 设计器（onlineProcessSet）
- 站内通知铃铛、邮件或其它外部通知通道
- Dashboard 占位首页
- 旧 H5「关于我们」「测试切账号」
- API 路径是否仍挂 `/admin`（实现时决策）
- `openId`、公众号菜单表数据清理（迁移时决策）
- 本票本身不修改后端业务规则 beyond 删除微信相关端点

## Further Notes

- **地图**：Part of #1（云尚办公统一客户端改造规格）
- **领域词汇**：仓库根目录 `GLOSSARY.md`
- **现状对照**：`docs/research/现有两端能力与路由对照.md`
- **人类可读规格汇总**：`docs/spec/云尚办公统一客户端改造规格.md`
- **决策索引**：GitHub issues #2–#7（均已关闭）
- Activiti engine and existing process APIs are retained; this is primarily a **frontend merge + auth simplification + WeChat removal** effort.
