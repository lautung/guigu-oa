# 测试证据索引

规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。
方案：[`../plan.md`](../plan.md)；执行记录：[`../execution/`](../execution/)；总报告：[`../report.md`](../report.md)。

## 约定

- 一轮一目录，命名 `<日期>-<类别>[-轮次]`；修复轮与基线轮使用同一脚本、同一快照。
- routine 运行（push main 栈回归、周安全）的证据只进 Actions artifact（14 天），不入仓；入仓只留发布/高保真轮次（ADR 0030）。
- 入仓硬上限：单文件 ≤100 KB，本目录整体 ≤5 MB；超限即视为大件，不入仓。
- 入仓证据必须脱敏：token/密码/密钥只留前缀，不贴可直接复用的攻击载荷。
- 入仓证据里的日志用 `.txt` 后缀：`*.log` 被仓库 `.gitignore` 忽略，会静默漏提交。
- 每条证据必须有生成命令；「可复跑」优先于「留存全量」。大件的本机路径与重跑命令写在各轮 `README.md` 或执行记录里。

## 目录

| 目录 | 内容 | 生成命令 | 执行记录 |
|---|---|---|---|
| `2026-10-29-functional-stack-devrun/` | 功能套件开发期自测（发现 #131/#132） | `cd client/guigu-oa-client && npm run test:e2e:stack` | [`2026-10-29-functional-stack-devrun.md`](../execution/2026-10-29-functional-stack-devrun.md) |
| `2026-10-29-functional-stack-formal/` | 功能套件正式轮 26/26 | 同上 | [`2026-10-29-functional-stack-formal.md`](../execution/2026-10-29-functional-stack-formal.md) |
| `2026-10-29-api/` | 接口基线轮 65/65 | `DEMO_ORIGIN=http://localhost:9530 npm run test:api` | [`2026-10-29-api.md`](../execution/2026-10-29-api.md) |
| `2026-10-29-api-after-fix/` | 接口 after-fix 66/66 | 同上（干净栈） | [`2026-10-30-api-after-fix.md`](../execution/2026-10-30-api-after-fix.md) |
| `2026-10-29-security-baseline/` | 56 条定向、ZAP 摘要、存储型 XSS | `node scripts/security-scan.mjs --out …`、`node scripts/zap-baseline.mjs --out …`、`npm run test:security` | [`2026-10-29-security-baseline.md`](../execution/2026-10-29-security-baseline.md) |
| `2026-10-29-security-fix-133/` … `-142/` | 10 个安全缺陷的逐项复跑 | 见各目录 `environment.md` 与缺陷 issue 关闭评论 | 同上 |
| `2026-10-30-perf-baseline/` | k6 各档汇总、docker stats、慢日志、行数 | `perf/ops/round.sh <name> <tier>` | [`2026-10-30-perf-baseline.md`](../execution/2026-10-30-perf-baseline.md) |
| `2026-10-30-perf-fix-147/` | 连接池调优整轮复跑 | 同上（镜像含 `maximum-pool-size: 25`） | [`2026-10-30-perf-fix-147.md`](../execution/2026-10-30-perf-fix-147.md) |
| `2026-10-29-compatibility/` | 五格关键页截图 + 控制台 JSON + JUnit | `E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e` | [`2026-10-29-compatibility.md`](../execution/2026-10-29-compatibility.md) |
| `2026-10-30-clean-rerun/` | 干净环境五类复跑：`api/`、`functional/`、`perf/`、`security/`、`compat/`、`timings.tsv` | `./scripts/test-round.sh all`（或按类子命令） | [`2026-10-30-clean-rerun.md`](../execution/2026-10-30-clean-rerun.md) |
| `2026-11-01-perf-read-module/` | 审批列表读 module k6 before/after（main `9f1e27c` vs spec-178 `358dbbd`，含回归 EXPLAIN 取证） | `perf/ops/round.sh <name> <tier>`（after 轮带 `COMPOSE_PROJECT_NAME=guigu-oa`） | [`2026-11-01-perf-read-module.md`](../execution/2026-11-01-perf-read-module.md) |
| `2026-11-01-publication-module/` | 发布一致性 spec #175 收尾回归四件套（api summary+junit、functional report+junit、timings） | `./scripts/test-round.sh api`、`./scripts/test-round.sh functional`（干净栈自重建） | [`2026-11-01-publication-module.md`](../execution/2026-11-01-publication-module.md) |

## 大件（不入仓）

| 内容 | 本机位置 | 重新生成 |
|---|---|---|
| 完整 ZAP HTML/JSON 报告 | `/tmp/security-evidence-full/<轮次>/zap-full/zap-report.html` | `node scripts/zap-baseline.mjs --out <dir>` |
| Playwright JSON/HTML/trace/截图 | `client/guigu-oa-client/test-results/` | 对应 `npm run test:*` |
| k6 原始汇总与 observe 日志 | `perf/results/` | `perf/ops/round.sh …` |
| 快照（mysqldump + 卷） | `perf/snapshot/` | `perf/ops/snapshot.sh` |
