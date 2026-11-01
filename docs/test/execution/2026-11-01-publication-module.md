# 发布一致性 spec 收尾回归执行记录（#191）

- 日期：2026-11-01（本机时间）
- 执行票：[收尾交付 #191](https://github.com/lautung/guigu-oa/issues/191)
  （规格 [发布一致性收成一个 owner #175](https://github.com/lautung/guigu-oa/issues/175) 的收尾票）
- 口径：docs/test/plan.md §8 套件入口；`./scripts/test-round.sh api|functional`
  （阶段内自重建干净栈 `up --build --wait`，服务端改动进镜像）；受代理限制带
  `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public`
- 交付基线：main `48c1e46`（#187 删死方法、#188 部署 port、#189 PublicationVerifier、
  #190 verdict 四分支、评审修复 `80f20ee`、#192 用例守卫修复 `48c1e46`）
- 证据：[`2026-11-01-publication-module/`](../evidence/2026-11-01-publication-module/)
  （api `summary.json`+`junit.xml`、functional `report.json`+`junit.xml`、`timings.tsv`；
  api 原始 report.json 103KB 超单文件 100KB 上限，按 evidence README 约定不入仓，蒸馏为 summary）

## 1. 回归四件套

| 套件 | 命令 | 结果 |
|---|---|---|
| 服务端全量 | `mvn -pl service-oa -am test` | **492/492 全绿**（0 失败 0 错误 0 跳过） |
| 客户端 | `npm run verify` | **全绿**（lint + typecheck + format + 单测 511 + 文档/shell 检查） |
| 接口套件 | `./scripts/test-round.sh api`（干净栈两轮） | **2×76 全绿**（round1 17.4s / round2 25.6s） |
| 功能栈套件 | `./scripts/test-round.sh functional` | **28/28 全绿**（63s，干净栈重建后） |

## 2. 功能栈前三轮 27/28 的定性（既存竞态 → 修复 → 复跑绿）

第 1–3 轮功能栈均 `1 failed / 27 passed`，失败用例固定为 `p0-3-start-with-attachment`
（alice 发起含附件审批，提交后 20s 未见「已发起」页头）；同用例隔离单跑绿（1/1）。

定性链（trace 级证据，[记录见 #192](https://github.com/lautung/guigu-oa/issues/192)）：

1. 失败轮 trace：点击提交后**没有任何 startUp 请求发出**；服务端日志零异常、零慢请求（>500ms 为零）。
2. 提交时刻截帧：toast「请等待附件读取、上传或删除完成」+ 附件行「未确认上传完成」+「上传中 1」；
   4.6s 后附件才确认完成（行内出现大小与删除按钮）。
3. 根因：`uploadStartAttachment` 只等文件名出现——名字在上传开始即乐观渲染；套件负载下直传确认
   慢于「填表+点击」，提交撞 `approval-start.ts` 的 `busy(current)` 守卫被静默拒绝。
   即 #169 轮「p0-3 附件直传独立间歇留观」的根因，与本 spec 服务端改动无关（请求未发出）。
4. 修复 `48c1e46`：helper 等确认态正信号（行内出现「删除」按钮），第 4 轮 28/28 全绿。

## 3. 与回归锚点的对应（spec #175）

- 功能栈套件（#115）模板发布/恢复用例：随 28/28 绿（p0-1/p0-2 等发起-处理链路含在内）。
- ADR 0003 证据校验用例：`ApprovalTemplateContractTest` 28 例原样零改动全绿（含恢复停止文案逐字钉子）。
- 接口套件模板相关端点：随 2×76 绿。

## 4. 环境备注

- Compose 项目名随目录（主仓目录 = `guigu-oa`），perf/ops 脚本硬编码卷名匹配。
- 本轮均在主仓目录执行；功能栈第 4 轮复用 `refresh_stack` 重建（image 含最终代码）。
