# 2026-11-01-perf-read-module（#182 · 审批列表读 module k6 before/after）

规格 #178 的 N+1 消除收益验证：before = main `9f1e27c`（不含本规格改动），after = spec-178
`358dbbd`（#179/#180/#181 全部落地）。口径与 #124 基线可比：同档位、同造数（同一份快照）、
同脚本（`perf/mixed.js` / `perf/attachment.js`，附件档 harness 适配见下）。
执行记录：[`docs/test/execution/2026-11-01-perf-read-module.md`](../../execution/2026-11-01-perf-read-module.md)；
口径：[`perf-load-model`](../../plan/perf-load-model.md)（#112）§7 调优对比协议；
基线对照：[`2026-10-30-perf-baseline/`](../2026-10-30-perf-baseline/)（#124）、[`2026-10-30-perf-fix-147/`](../2026-10-30-perf-fix-147/)。

## 结论速览（详见执行记录）

- **收益**：基线档吞吐中位 433.0 → 558.9 req/s（**+29.1%**）；N+1 目标端点 findPending p95
  47→28ms（−40%）、findProcessed 336→149ms（**−56%**）；其余动作 −1% ~ −17%。
- **回归（缺陷 issue [#185](https://github.com/lautung/guigu-oa/issues/185)）**：findStarted
  p95 23→154ms（6.7×）、配置实例查询 35→78ms——#179 显式 `ORDER BY create_time DESC, id DESC`
  在无索引时全量进排序堆（EXPLAIN ANALYZE：同一条 SQL 16.4ms vs 0.044ms，
  见 `explain-findStarted-orderby.txt`）。
- **持平**：抄送我的 332→303ms（−9%）、附件链与登录在噪声内；两轮所有阈值判定全过、零容器重启。

## 文件

| 文件 | 内容 |
|---|---|
| `before/`、`after/` | 两轮证据，逐档一份；文件名同 #124 基线约定 |
| `before|after/k6-summary-<档位>.json` | k6 汇总精简版（values + 阈值判定；原始 ~570KB/个不入仓） |
| `before|after/k6-console-{baseline-1,stress-1,list-reads-1,attachment-1}.txt` | k6 控制台输出样本 |
| `before|after/stats-<档位>.csv.gz` | docker stats ~1Hz 采样 |
| `before|after/rows-<档位>.txt` | 当档收尾 `oa_*` / `ACT_*` 行数 |
| `before|after/slow-<档位>.txt` | MySQL 慢日志（>0.1s；`.txt` 后缀避开 `*.log` 忽略规则） |
| `before/seed-summary.json` | 造数摘要（before 轮造数，after 轮复用其快照） |
| `environment.md` | 两轮环境与差异 |
| `row-counts.md` | 种子快照与基线-1 收尾行数对照 |
| `explain-findStarted-orderby.txt` | 回归端点的 EXPLAIN ANALYZE 取证 |

档位：smoke / baseline-1..3 / mid-1 / stress-1 / attachment-1 / list-reads-1。
`list-reads` 为本次新增补充档（`perf/list-reads.js`）：抄送我的 + 配置实例查询各 50%、20 VU、
30s+3min——这两个端点不在 #124 的 mixed.js 里，无 #124 锚点，只做同快照两轮相对比较。

## 复跑

```sh
# 0. 各自仓库目录（before=主仓 main，after=worktree spec-178）构建起栈
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait

# 1. 造数 + 快照（before 轮；after 轮跳过造数，直接复用快照）
TARGET=10000 FINISHED=4000 node perf/seed.mjs
perf/ops/snapshot.sh

# 2. 逐档（after 轮需 export COMPOSE_PROJECT_NAME=guigu-oa，restore.sh 硬编码该卷名前缀）
perf/ops/round.sh smoke smoke
perf/ops/round.sh baseline-1 baseline        # …-2 -3 同
perf/ops/round.sh mid-1 mid
perf/ops/round.sh stress-1 stress
perf/ops/round.sh attachment-1 baseline perf/attachment.js
perf/ops/round.sh list-reads-1 baseline perf/list-reads.js
```

原始大件（未裁剪 JSON、observe 日志）在两仓库的 `perf/results/`（不入仓）；
快照在 `perf/snapshot/`（before 轮产物拷贝到 after 仓库目录复用）。

## after-fix/（#185 修复轮，2026-11-01 晚间）

`after/` 的回归由 V108 `idx_oa_process_user_create_time` + mapper 条件 hint 修复
（执行记录：[`docs/test/execution/2026-11-01-perf-185-fix.md`](../../execution/2026-11-01-perf-185-fix.md)）。
同快照同脚本八档全绿；findStarted 基线 p95 154→29ms。因时段与夜锚不同（凌晨→晚间），
增跑 **anchor-evening**（`358dbbd` 无修复代码、同快照同脚本同时段 baseline 一档）作同条件
对照：findStarted 157ms 证明回归原样复现、其余动作与修复轮逐点重合证明 +15~30% 的
均匀膨胀是环境签名而非代码回退。

| 文件 | 内容 |
|---|---|
| `k6-summary-<八档>.json` | 修复轮裁剪汇总（同上约定） |
| `k6-summary-anchor-evening.json` | 晚锚裁剪汇总 |
| `k6-console-{baseline-1,stress-1,list-reads-1,attachment-1,anchor-evening}.txt` | k6 控制台样本 |
| `rows-<档位>.txt` / `slow-<档位>.txt` / `stats-<档位>.csv.gz` | 当档收尾取证（慢日志 `.txt` 后缀） |
| `explain-findStarted-index.txt` | 修复机制取证：general_log 实发 SQL 原文 EXPLAIN ANALYZE——反向索引扫描 0.113ms、无 Sort；含优化器不自发选索引（join 上下文 ref 成本差 0.45）的 trace 证据与 hint 依据 |
