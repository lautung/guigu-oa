# 真实栈功能套件：正式执行与留证（2026-10-29）

来源：[执行功能测试并留证 #115](https://github.com/lautung/guigu-oa/issues/115)。
方案与判定口径：[确定功能测试的范围与用例设计口径 #121](https://github.com/lautung/guigu-oa/issues/121)。
用例清单与覆盖映射：`docs/test/plan/functional-stack-cases.md`。
规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

**性质：正式执行（#121 §9 的「P0 全绿一次复跑」）。** 前置已全部满足：#111/#121 关闭、
#128/#129 关闭（一条命令起栈）、#131/#132 修复并关闭。

## 执行环境

| 项 | 值 |
|---|---|
| 日期 | 2026-10-29 |
| 被测版本 | 仓库 `5014acc`（HEAD，含 #111/#128/#129/#131/#132 修复） |
| 环境 | 本机 Docker Compose 演示栈，**全新卷**（`down --volumes --remove-orphans` 后重建） |
| 栈版本 | mysql 8.4.13 / redis 7.4 / minio bitnami 2025.7.23（mc 2025-07-21）/ service-oa（JDK 21.0.12.1、Spring Boot 4.1.0、Flowable 8.0.0）/ client（nginx 1.31.8 生产构建） |
| 起栈 | `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait`，67 s，**无任何手工播种步骤**（#128） |
| 生成构建的说明 | 受限网络下 Maven 需镜像源（`compose-demo.md` 记载的既定变量）；镜像与 npm 缓存命中后 up 约 1 分钟 |

## 环境验收（执行前）

```
./scripts/compose-baseline-check.sh   → 结果：20 通过，0 失败
node scripts/compose-demo-smoke.mjs   → Compose demo OK: Bob pending=1, ended=1, Carol copied=2, attachments=1
```

全新卷上一条命令即到可测基线（演示审批由启动器自动发布，启动日志
出现 `Compose 演示审批模板已发布` 与 `Compose 演示数据就绪`），无需「首次启动后」手工步骤。

## 用例执行

命令（单 worker 串行、重试 0、Chromium 1280×900，P0-7 用例内 390×844）：

```
cd client/guigu-oa-client && npm run test:e2e:stack
```

结果：**26 通过 / 0 失败 / 0 跳过**，54.1 s（预算 ≤5 分钟）。

| 分组 | 用例 | 结果 |
|---|---|---|
| P0-1 | 登录成功 / 失败不区分原因 / 停用不能登录 / 退出 / 直达 URL 被拦（6 断言场景） | 通过 |
| P0-2 | admin 与 user 侧栏差异 / user 直达配置 URL 落待处理 / admin 可进配置页（4） | 通过 |
| P0-3 | alice 发起含附件审批（真实 MinIO）+ 已发起可见 | 通过 |
| P0-4 | bob 待处理 → 通过 → 双视角状态一致 + 已处理可见 | 通过 |
| P0-5 | bob 拒绝路径 | 通过 |
| P0-6 | carol 抄送只读 + 完整表单/流转记录 + 附件下载 | 通过 |
| P0-7 | 手机 390 视口登录 → 待处理 → 详情 → 通过 | 通过 |
| P0-8 | 发起结果不明（pause 冻结）→ 核实 → 明确重新发起 → 两条都真实存在 | 通过 |
| P1-1 | 模板发布/编辑：未发布可改可删、发布后冻结 | 通过 |
| P1-2 | 分配角色后菜单权限生效、撤销后消失 | 通过 |
| P1-3 | 用户/角色/菜单/审批类型 CRUD 各一条（4） | 通过 |
| P1-4 | 25 条造数翻页：末页不满、下一页禁用、越界为空 | 通过 |
| P1-5 | 附件生命周期：发起前可删、发起后不可删、审批结束保留（2） | 通过 |

## 判定

#121 §7：**P0 全部通过且无未关闭的 S1/S2 缺陷 = 功能回归基线通过**。

- P0：8/8 通过；
- 缺陷：本轮执行相关的 [S1 #131](https://github.com/lautung/guigu-oa/issues/131)、
  [S3 #132](https://github.com/lautung/guigu-oa/issues/132) 均已修复、回归并关闭
  （closed = verified），无未关闭的 S1/S2；
- **结论：功能回归基线通过。**

## 证据

- Playwright JSON/JUnit 报告：`docs/test/evidence/2026-10-29-functional-stack-formal/report.json`、`report.xml`
  （含逐用例状态与耗时；失败用例的 trace 在 `tests/e2e-stack/test-results/`，本轮无失败）。
- 环境验收输出见上文；基线脚本与冒烟脚本在 `scripts/`。
- 开发期自测与两次缺陷修复的复跑记录：`docs/test/execution/2026-10-29-functional-stack-devrun.md`。

## 复跑方式

1. 起栈：`docker compose down --volumes --remove-orphans` 后
   `MAVEN_MIRROR_URL=… docker compose up -d --build --wait`（受限网络才需要镜像变量）；
2. `./scripts/compose-baseline-check.sh` 与 `node scripts/compose-demo-smoke.mjs` 确认基线；
3. `cd client/guigu-oa-client && npm run test:e2e:stack`。

数据策略（#121 §5）：每轮 runId 标记自造数据、可清理项自清理；唯一残留是每轮 1 个已发布模板
（ADR 0005 冻结，不可删）。全新卷重置会清空全部业务数据，因此正式判定以全新卷上的这一轮为准。
