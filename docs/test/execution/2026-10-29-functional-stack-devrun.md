# 真实栈功能套件：开发期自测运行记录（2026-10-29）

**性质：开发期自测（suite bring-up）+ 缺陷 #131 修复后的复跑记录；不是 #115 的正式执行与留证。**
正式执行等「把 Compose 演示审批改成一条命令即可发起」#128、「健康检查」#129 关闭后进行
（见 #121 §9）。缺陷 #131 已于同日修复（见文末补充），P0 已全绿。

## 环境

- 演示栈：本机 Docker Compose（mysql 8.4 / redis 7.4 / minio bitnami 2025.7.23 / service-oa
  JDK 21 + Spring Boot 4.1.0 + Flowable 8.0.0 / client nginx 构建镜像），基线检查
  `./scripts/compose-baseline-check.sh` 20/20 通过，`node scripts/compose-demo-smoke.mjs` 通过
  （Bob pending=1, ended=1, Carol copied=2, attachments=1）。
- 被测版本：工作区未提交树（compose 基线改动 + 本次新增套件），客户端为镜像内生产构建。
- 命令：`npm run test:e2e:stack`（1 worker、重试 0、Chromium 1280×900；P0-7 用例内覆写为 390×844）。

## 结果

Playwright 汇总：**23 通过 / 2 失败 / 1 按设计跳过**，用时 64 s（预算 ≤5 分钟）。

- 通过：P0-1（6 断言场景）、P0-2（4）、P0-4、P0-5、P0-6、P0-7、P1-1、P1-2、P1-3（4）、P1-4、P1-5（后半段）。
- 已知失败（同一根因，缺陷 #131）：**P0-3**、**P0-8**——发起页读取配置侧端点
  `processTemplate/get/{id}` 被 configurationMenuGate 拒绝，无配置权限用户无法打开发起页。
- 跳过：P1-5 的「发起前可删」段（`test.fixme`，同一 #131 阻塞）。

报告副本：`evidence/2026-10-29-functional-stack-devrun/report.json`（含每用例耗时）、`report.xml`。

## 本轮发现（缺陷）

| 缺陷 | 严重级 | 说明 |
|---|---|---|
| [发起页读配置侧模板端点，无配置权限用户无法发起审批](https://github.com/lautung/guigu-oa/issues/131) | S1 | 办事主链路对全部非配置用户不可用；mock e2e 与 MockMvc 各自都没暴露。P0-3/P0-8/P1-5 前半段阻塞于此。**已修复**（`88c4e10`，closed=verified）。 |
| [sysRole findAll 内置演示异常，永远返回 20001](https://github.com/lautung/guigu-oa/issues/132) | S3 | 课程遗留「模拟异常效果」；客户端无调用方，接口已发布 OpenAPI。**已修复**（删除演示异常，admin 200 + 角色列表，无授予用户仍被 gate 拒 205）。 |

## 套件自测期间修掉的问题（套件自身，非产品缺陷）

1. 直达 URL 拦截会带 `?redirect=` 参数——登录页 URL 断言放宽为允许 query。
2. oracle 上传附件必须用 UUID 形态的 draftId，且发起时要把同一 draftId 作为
   `attachmentDraftId` 传回 `startUp`（服务端按 draft 归属绑定附件）。
3. 办事列表无排序保证、数据跨轮积累：oracle 的标记查找改为全页遍历；用例内列表/表格定位
   全部改为翻页查找；P1-4 的页数与「越界页」按真实总数动态计算。
4. 服务端以 zip 文件名（去扩展名）派生流程 key，发布时校验 BPMN process id 与之一致——
   P1-1 的 zip 命名改为 `${processKey}.zip`。
5. 菜单只能维护配置菜单（服务端 201），P1-3 的菜单用例改为在配置根「审批查询」下建停用子节点。
6. 发布动作的断言从 3 秒即逝的 toast 改为行状态翻转（已发布）。
7. doAssign 的 201（提交成功、权限缓存失效待确认，ADR 0015）：P1-2 先用 oracle 轮询确认落库，
   再重试重新登录等待侧栏生效。

## 数据与残留

- 审批与附件全部 runId 标记，跨轮可区分；bob 待处理、alice 已发起、carol 抄送随轮次增长（列表
  相关断言已动态化）。
- 唯一不清理的残留：每轮 1 个 runId 命名的**已发布**模板（ADR 0005 冻结，不可删）。
- P1-2 授予的配置角色在 finally 撤销；P0-1 停用的 `user` 在 finally 恢复。

## 结论

真实栈功能套件已建成并自测到「除 #131 阻塞用例外全绿」。#115 的正式执行（P0 全绿一次复跑 +
留证）等 #128、#129 关闭后进行。

## 补充：#131 修复后复跑（2026-10-29 同日）

**修复内容**（客户端 + 服务端各一处）：

- `client/guigu-oa-client/src/work/approval-start-browser.ts`：发起流程的模板读从配置侧
  `processTemplate/get/{id}` 改为工作侧 `getProcessTemplate/{processTemplateId}`（不要求配置权限）。
- `ProcessController.getProcessTemplate`：加「仅已发布」过滤——未发布模板返回 data=null，
  仍由配置侧按授予读取（避免把 #110 预警的「未发布模板可读」扩大到全部办事用户）。
  客户端对 data=null 显示「审批模板未发布或不存在，不能发起」。
- 配套：客户端单测断言与 mock e2e 桩改到新 URL（配置编辑器仍走配置侧端点，不变）；
  新增服务端控制器测试 `ProcessApiControllerTest`（已发布可读 / 未发布 null / 不存在 null）；
  解除 P1-5「发起前可删」的 fixme。

**复跑结果**（重建 client 镜像后）：

| 套件 | 结果 |
|---|---|
| 真实栈功能套件（26 用例） | **26 通过 / 0 失败 / 0 跳过**，84s |
| mock e2e（41 场景 × 2 视口） | 59 过 / 22 按视口设计性跳过 |
| 服务端测试（含 3 条新增） | 256 全绿，BUILD SUCCESS |
| 客户端 `npm run verify` | 485 单测全绿，lint/typecheck/format 通过 |

P0-8 修复期间发现两处套件自身问题并已修正：发起页需等静止（提交按钮可用）再冻结服务端；
「仍未找到，重新发起」在真实 UI 上有「接受可能重复，继续」确认框（ADR 0008 的显式确认），
用例已补该步骤。

报告副本：`evidence/2026-10-29-functional-stack-devrun/report.json`、`report.xml`（修复后全绿那轮）。
