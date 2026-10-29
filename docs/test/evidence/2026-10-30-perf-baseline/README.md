# 性能基线证据（perf #124，2026-10-30）

执行记录：`docs/test/execution/2026-10-30-perf-baseline.md`；口径：`docs/test/plan/perf-load-model.md`。

| 文件 | 内容 |
|---|---|
| `k6-summary-*.json` | k6 汇总精简版（values + 阈值判定；原始 560KB/个不入仓） |
| `k6-console-baseline-1.txt` / `k6-console-stress-1.txt` | k6 控制台文本摘要样本 |
| `stats-*.csv.gz` | docker stats ~1Hz 采样（time,container,cpu,mem,net,block） |
| `gc-pauses-baseline-slow.log.gz` | G1 停顿行（baseline-slow 轮窗口，1302 次） |
| `slow-*.log` | MySQL 慢日志（>0.1s）——**零条目**，仅头部 |
| `row-counts.md` | 每轮前后 `oa_*` / `ACT_*` 行数 |
| `seed-summary.json` / `seed-run.txt` | 造数摘要与日志 |

慢日志全零的原因不是没开（`slow_query_log=ON, long_query_time=0.1` 已核实在位），而是
服务端单条 SQL 均未超 100ms——延迟消耗在 Hikari 连接池排队（见执行记录 §瓶颈）。
