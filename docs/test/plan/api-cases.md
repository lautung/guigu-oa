# 接口测试：工具、用例与判定口径

来源：[确定接口测试的工具与用例来源 #120](https://github.com/lautung/guigu-oa/issues/120) 的 resolution。
执行票：[执行接口测试并留证 #123](https://github.com/lautung/guigu-oa/issues/123)。
记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。
执行记录：`docs/test/execution/2026-10-29-api.md`（基线轮）、`docs/test/execution/2026-10-30-api-after-fix.md`（after-fix 轮）。

本文件定「测什么、怎么判」，不写执行结果。期望值按**修复后的最终契约**书写；基线轮曾断言的缺陷行为
（#134/#137/#138/#143/#144/#145/#146）已在 after-fix 轮翻红转绿。

## 1. 范围与定位

- **黑盒覆盖 56 个业务端点**：以 [盘点接口、鉴权与配置权限矩阵 #110](https://github.com/lautung/guigu-oa/issues/110)
  的端点底稿为准，走 Compose 栈 nginx 的 `/dev-api`，顺带验证代理剥离。
- **白盒保持现状**：MockMvc 合同测试覆盖 32 个端点，不为 23 个缺口反向补 MockMvc——真实 MySQL 方言、
  真实 Flowable 流转、真实预签名 URL 只有黑盒能看，白盒的价值在快速定金行为契约。
- **客户端不调用的端点照样覆盖**：`logout`、`sysRole/batchRemove`、`process/getProcessTemplate`、
  `sysRole/findAll`。服务端暴露了就是对外契约面。
- **不在本套件**：`/actuator/health`（已有 `ObservabilityContractTest`）、`/v3/api-docs`、`/swagger-ui/**`
  的匿名暴露归安全测试（#113/#116）。

## 2. 工具链

- `@playwright/test` 的 `request` fixture（`APIRequestContext`，1.64 已在 devDependencies），**不新增依赖**。
- 独立配置 `client/guigu-oa-client/playwright.api.config.ts`：`testDir: './tests/api'`，
  **不配 `webServer`**（打已运行的 Compose 栈），单 worker 串行、重试 0。
- `use.baseURL = ${DEMO_ORIGIN:-http://localhost:9530}/dev-api`；确需直连后端时用 `API_BASE_URL`
  覆盖完整前缀（此时不带 `/dev-api`）。
- 报告：默认 `list`；机器可读同时输出 `test-results/api/report.json` 与 `test-results/api/junit.xml`。
- 与安全定向用例共用同一 HTTP harness 与登录 helper，不各写一套。

## 3. 权限分支矩阵

每条规则对全量 56 端点机械适用，不逐个临场决定。

| 端点类别 | 端点数 | 必测分支 |
|---|---|---|
| 匿名 | 2（`login`、`refresh`） | 正例 + 凭据/令牌非法分支；不发 token |
| 仅 token | 19（`info`、`logout`、11 个办事、6 个附件） | 正例 + 无 token → 208 + 篡改/过期 token → 208 + 停用账号 → 209 |
| 配置 gate | 35 | 正例 + 有 token 无授予 → 205 + 单页授予只开该页（其余页仍 205） |
| 成员/归属 | 10（`show`/`approve` 及其余 8 个办事与附件端点） | 正例 + 非成员 → 403（办事）或 205（附件） + 历史处理人/抄送人可读但 `isApprove=false` |

错误分支：业务校验失败 → 201；受保护路径/已发布模板的不可变操作 → 201；容器级错误按最终契约
（未知路径 404、方法不匹配 405，均带业务信封；认证失败 401 + 208/209）。

## 4. 断言口径

1. **契约层（全部用例）**：`body.code` 为权威。成功 200，业务失败按第 3 节与第 6 节的期望值。
   `message` 只在语义稳定的位置断言（如 `没有操作权限`），不做全文匹配。
2. **数据结构层**：字段存在 + 类型 + 关键值（`id`/`total`/`records`/`canUpload`/`isApprove` 之类）。
   **不做全量快照**——快照会让任何一次正常字段新增变红，把回归基线变成维护负担。
3. **HTTP 语义层（独立用例组 F）**：#145 修复后，认证失败返回 401、容器级错误返回真实 404/405，
   业务失败仍是 HTTP 200 + `body.code`。状态码调整只改 F 组，其余用例不动。

## 5. 测试数据与可重复性

- **可回滚的用 API 造数自清理**：临时用户、临时角色、临时菜单、审批类型、未发布模板。
  用例建、用例删，失败也走 `finally` 清理。
- **不可回滚的单独分组并标注「需快照重置」**：已发布模板、审批实例、Flowable 流转、`publish`。
  沿用 #112 的全库/卷快照，不另立机制。
- **账号基线沿用种子**：`admin`（配置全授）、`user`（无配置授予）、`alice`/`bob`/`carol`（发起/处理/抄送）。
  gate 分支用 API 现场授予的**单页账号**构造，不用 admin 冒充。
- **干净栈前置**：`tests/api/global-setup.ts` 以 alice 的已发起总数 >2000 为压测造数卷信号，
  直接拒绝运行并给出重置命令；依赖演示数据的用例（D1、D5–D8、D10、E5、E6、`startUp` 正例）
  在干净卷上才有稳定基线。
- 登录限流按账号累计失败：错密码用例只用临时账号并在同一用例内成功登录一次清零，种子账号不承担失败计数。

## 6. 用例清单（56 端点 / 66 用例）

`重置` 列：`无`（只读）、`临时对象`（API 造数自清理）、`快照`（需快照重置）。

### A. 认证与认出（4 端点 / 4 用例）

| # | 端点 | 用例（分支） | 期望 | 账号 | 重置 |
|---|---|---|---|---|---|
| A1 | `POST /admin/system/index/login` | 正例 / 错密码 / 不存在用户 / 停用用户 | 200 / 208 / 208 / 209 | 匿名 | 无 |
| A2 | `POST /admin/system/index/refresh` | 正例 / access 当 refresh / 篡改 refresh / 停用用户 | 200 / 208 / 208 / 209 | 匿名 | 无 |
| A3 | `GET /admin/system/index/info` | admin 有 `configMenus` / user 为空 / 无 token | 200 / 200 / 208 | admin/user/匿名 | 无 |
| A4 | `POST /admin/system/index/logout` | 有 token 作废会话（#134）/ 无 token 幂等 | 200，退出后原 token 调 `info` → 208 / 200 | admin | 无 |

### B. 系统配置（21 端点 + 2 组授予分支）

| # | 端点 | 用例（分支） | 期望 | 账号 | 重置 |
|---|---|---|---|---|---|
| B1 | `PUT sysUser/updateStatus/{id}/{status}` | 停用后下一次请求 / 无授予 | 200→209 / 205 | admin/user | 临时对象 |
| B2 | `GET sysUser/{page}/{limit}` | 正例 / 无授予 / `limit` 超上限 / 不返回密码哈希 | 200 / 205 / 201 / `records` 不含 `password` | admin/user | 无 |
| B3 | `GET sysUser/get/{id}` | 正例 / 无授予 / 不存在的 id | 200 / 205 / 200 + `data=null` | admin/user | 临时对象 |
| B4 | `POST sysUser/save` | 正例（BCrypt 落库）/ 无授予 | 200 / 205 | admin/user | 临时对象 |
| B5 | `PUT sysUser/update` | 空密码保留原 hash / 改密后旧会话立即失效（#143） | 200 / 旧 token → 208 | admin | 临时对象 |
| B6 | `DELETE sysUser/remove/{id}` | 逻辑删除后列表不含 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| B7 | `GET sysRole/toAssign/{userId}` | 正例（全量角色 + 已授）/ 无授予 | 200 / 205 | admin/user | 临时对象 |
| B8 | `POST sysRole/doAssign` | 全量覆写生效 / 不存在或已删除的 roleId（#144） | 200 / 201 且不写关系行 | admin | 临时对象 |
| B9 | `GET sysRole/findAll` | 有授予 / 无授予 | 200 / 205 | admin/user | 无 |
| B10 | `GET sysRole/{page}/{limit}` | 正例 / 无授予 | 200 / 205 | admin/user | 无 |
| B11 | `POST sysRole/save` | 正例 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| B12 | `GET sysRole/get/{id}` | 正例 / 无授予 / 不存在的 id | 200 / 205 / 200 + `data=null` | admin/user | 临时对象 |
| B13 | `PUT sysRole/update` | 正例 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| B14 | `DELETE sysRole/remove/{id}` | 正例（`get` 返回 null）/ 无授予 | 200 / 205 | admin | 临时对象 |
| B15 | `DELETE sysRole/batchRemove` | 正例（体为 `List<Long>`）/ 无授予 | 200 / 205 | admin/user | 临时对象 |
| B16 | `GET sysMenu/toAssign/{roleId}` | 可授树 + 已勾选 / 无授予 | 200 / 205 | admin | 临时对象 |
| B17 | `POST sysMenu/doAssign` | 合法授予 / 新增非法 id / 旧非配置 id 静默跳过 | 200 / 201 / 200 | admin | 临时对象 |
| B18 | `GET sysMenu/findNodes` | 配置树（滤非配置）/ 无授予 | 200 / 205 | admin | 无 |
| B19 | `POST sysMenu/save` | 正例 / 受保护 path 重定义 / 非配置菜单 | 200 / 201 / 201 | admin | 临时对象 |
| B20 | `PUT sysMenu/update` | 正例 / 受保护 path/type/parentId/status 变更 | 200 / 201 | admin | 临时对象 |
| B21 | `DELETE sysMenu/remove/{id}` | 正例 / 有子节点 / 受保护入口 | 200 / 201 / 201 | admin | 临时对象 |
| G1 | 授予分支 | 单页授予只开 `sysUser`，其余配置页仍 205 | 205 | 单页账号 | 临时对象 |
| G2 | 授予分支 | 删除角色后成员授予即时回收（#146） | 原授予页 205，`/info` 的 `configMenus` 为空 | admin | 临时对象 |

B17 的「旧非配置 id 静默跳过」需要历史遗留数据才能构造，干净卷上没有该分支，列为已知未覆盖。

### C. 审批配置（14 端点）

| # | 端点 | 用例（分支） | 期望 | 账号 | 重置 |
|---|---|---|---|---|---|
| C1 | `GET /admin/process/{page}/{limit}` | 正例 / 无授予 | 200 / 205 | admin/user | 无 |
| C2 | `GET processTemplate/{page}/{limit}` | 正例（含未发布）/ 无授予 | 200 / 205 | admin/user | 无 |
| C3 | `GET processTemplate/get/{id}` | 正例 / 无授予 / 不存在的 id | 200 / 205 / 200 + `data=null` | admin/user | 无 |
| C4 | `POST processTemplate/save` | 新建即未发布 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| C5 | `PUT processTemplate/update` | 未发布正例 / 已发布 | 200 / 201 | admin | 临时对象 + 快照 |
| C6 | `DELETE processTemplate/remove/{id}` | 未发布正例 / 已发布 | 200 / 201 | admin | 临时对象 + 快照 |
| C7 | `POST uploadProcessDefinition` | zip 正例 / 非 zip / 空文件 | 200 / 201 / 201 | admin | 临时对象 |
| C8 | `POST processTemplate/publish/{id}` | 未发布正例 / 再发布 | 200 / 201 | admin | 快照 |
| C9 | `GET processType/findAll` | 正例 / 无授予 | 200 / 205 | admin/user | 无 |
| C10 | `GET processType/{page}/{limit}` | 正例 / 无授予 | 200 / 205 | admin/user | 无 |
| C11 | `GET processType/get/{id}` | 正例 / 无授予 / 不存在的 id | 200 / 205 / 200 + `data=null` | admin/user | 临时对象 |
| C12 | `POST processType/save` | 正例 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| C13 | `PUT processType/update` | 正例 / 无授予 | 200 / 205 | admin/user | 临时对象 |
| C14 | `DELETE processType/remove/{id}` | 逻辑删除后 `get` 返回 null | 200 | admin | 临时对象 |

### D. 办事（11 端点）

| # | 端点 | 用例（分支） | 期望 | 账号 | 重置 |
|---|---|---|---|---|---|
| D1 | `GET findPending/{page}/{limit}` | bob 有记录 / alice 空 | 200 / 200 | bob/alice | 快照 |
| D2 | `POST startUp` | alice 正例 / 未发布模板 / 抄送人无效 | 200 / 201 / 201 | alice | 快照 |
| D3 | `GET getProcessTemplate/{id}` | 已发布可读 / 未发布返回 null | 200 / `data=null` | 任意登录用户 | 无 |
| D4 | `GET findStartable` | 只含已发布且可用 | 200 | alice | 快照 |
| D5 | `GET show/{id}` | 申请人/处理人/抄送人可读 / 非成员 | 200（抄送人 `isApprove=false`）/ 403 | alice/bob/carol/eve | 快照 |
| D6 | `POST approve` | 当前处理人通过 / 非当前处理人 | 200 / 403 | bob/admin | 快照 |
| D7 | `GET findProcessed/{page}/{limit}` | bob 正例 | 200 | bob | 快照 |
| D8 | `GET findCarbonCopied/{page}/{limit}` | carol 正例 | 200 | carol | 快照 |
| D9 | `GET carbonCopyRecipients` | 普通用户可读，只含 `id` 与 `name` | 200 | user | 无 |
| D10 | `GET findStarted/{page}/{limit}` | alice 只看到本人，`userId` 参数被服务端忽略 | 200 | alice | 快照 |
| D11 | `GET getCurrentUser` | 只含 `name` 与 `phone` | 200 | alice | 无 |

### E. 附件（6 端点 + 1 条生命周期正例）

| # | 端点 | 用例（分支） | 期望 | 账号 | 重置 |
|---|---|---|---|---|---|
| E1 | `POST attachments/uploads` | 草稿正例 / 超过 50 MiB / 非成员 | 200 / 201 / 205 | alice/bob | 快照 |
| E2 | `POST {attachmentId}/complete` | 正例 / 对象缺失 / 大小不符 / 非 owner | 200 / 201 / 201 / 205 | alice/bob | 快照 |
| E3 | `GET drafts/{draftId}` | owner 正例 / 他人空列表 / 非法 draftId | 200 / 空 / 201 | alice/bob | 临时对象 |
| E4 | `DELETE pending/{attachmentId}` | PENDING 正例 / 已绑定 / 非 owner | 200 / 205 / 205 | alice/bob | 临时对象 |
| E5 | `GET processes/{processId}` | 成员可读（`canUpload`）/ 非成员 | 200 / 205 | bob/admin | 快照 |
| E6 | `POST processes/{pid}/{aid}/download-url` | 成员正例 / 非成员 / 附件不属于该 process | 200 / 205 / 205 | bob/carol/admin | 快照 |
| E7 | 生命周期 | 登记 → 预签名 PUT → complete → 删除 | 全链 200 | alice | 临时对象 |

### F. HTTP 语义（7 用例，#145 修复后的契约）

| # | 场景 | 期望 |
|---|---|---|
| F1 | 登录失败 | HTTP 401 + `code=208` |
| F2 | 未知路径 | HTTP 404 + `code=201`「请求的资源不存在」 |
| F3 | 方法不匹配（`GET doAssign`） | HTTP 405 + `code=201` |
| F4 | 方法不匹配（`GET updateStatus`） | HTTP 405 + `code=201` |
| F5 | 无 token 受保护请求 | HTTP 401 + `code=208` |
| F6 | 匿名路径上的错误方法 | HTTP 401 + `code=208`（不再 403 空体） |
| F7 | 路由参数类型不匹配 | HTTP 200 + `code=201`（业务信封，非容器级错误） |

## 7. 缺口执行顺序（#120 §7，已按此执行）

1. **第一轮（4 个零 HTTP 覆盖的风险端点）**：`uploadProcessDefinition`、`publish/{id}`、
   `process/getProcessTemplate`、`carbonCopyRecipients`。
2. **第二轮（配置写 12 个）**：`updateStatus`、`sysUser/remove`、`sysRole/save|update|remove|batchRemove`、
   `processType/save|update|remove`、`processTemplate/save|update|remove`。
3. **第三轮（其余 7 个读与 `logout`）**：`sysUser/get`、`sysRole/get`、`processType/findAll|get`、
   `findStartable`、`getCurrentUser`、`logout`。

## 8. 判定口径

- **通过**：实际行为满足期望。
- **失败**：实际偏离期望；一缺陷一 issue（`bug` + `severity:Sx`），由修复票闭环。
- **跳过**：只允许环境性缺口并注明原因；`known-blocked` 不得伪造通过。
- **出口准则**：66/66 通过、0 跳过、0 重试，连续两轮结果一致；56/56 端点均有用例引用；
  权限分支矩阵全部落地。**接口回归基线通过 ≠ 版本可发布**：S3 缺陷清零后才算接口回归通过。

## 9. 命令

```bash
cd client/guigu-oa-client
DEMO_ORIGIN=http://localhost:9530 npm run test:api
# 直连后端（不经 nginx）
API_BASE_URL=http://localhost:8800 npm run test:api
```

前置：Compose 栈已起且为**干净演示卷**（`global-setup.ts` 会拒绝压测造数卷）。
报告落在 `client/guigu-oa-client/test-results/api/`；入仓证据按 #122 只放 JUnit XML 与小摘要。

## 10. 已知局限

- 黑盒只覆盖服务端契约，不覆盖 UI 渲染与浏览器行为（归功能/兼容/安全套件）。
- B17 的「旧非配置 id 静默跳过」需要历史遗留数据，干净卷未覆盖。
- 已发布模板与审批实例会累积；连续多轮后列表断言仍成立（按标记查找），但全量数据增长需要快照重置控制。
- 不测接口性能指标（`limit`、慢 SQL、吞吐）——测点归性能票，本套件只记录事实。
