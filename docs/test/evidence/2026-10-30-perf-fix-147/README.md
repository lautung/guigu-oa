# 2026-10-30-perf-fix-147（#127 · #147 连接池调优复跑）

HikariCP 连接池调优（`maximum-pool-size` 10 → 25）后的整轮复跑证据。
执行记录：[`docs/test/execution/2026-10-30-perf-fix-147.md`](../../execution/2026-10-30-perf-fix-147.md)；
基线对照：[`2026-10-30-perf-baseline/`](../2026-10-30-perf-baseline/)（#124）。
环境与机器口径与基线轮一致，见其 `environment.md`。

| 文件 | 内容 |
|---|---|
| `k6-summary-smoke.json` | 冒烟档（1 VU × 8 动作，100% 成功） |
| `k6-summary-baseline-1/2/3.json` | 基线档 20 VU 三轮（386.1 / 359.3 / 362.6 req/s） |
| `k6-summary-mid-1.json` | 50 VU 拐点夹逼档（384.3 req/s） |
| `k6-summary-stress-1.json` | 压力档 0→100 VU（378.2 req/s） |
| `k6-summary-attachment-1.json` | 附件独立档 10 VU × 3min（360.6 req/s） |
| `k6-console-baseline-1.txt` / `k6-console-stress-1.txt` | k6 控制台输出样本 |
| `stats-*.csv.gz` | docker stats ~1Hz 采样（service-oa / mysql / redis / minio） |
| `row-counts.md` | 收尾时 `oa_process` / `oa_process_record` / `ACT_*` 行数 |
| `slow-attachment-1.log` | 最后一档慢 SQL 日志（仅两条 group-commit 等待，见执行记录 §5） |

## 复跑

```sh
# 每档前恢复快照；service-oa 镜像须含调优配置（application-dev.yml 的 maximum-pool-size: 25）
perf/ops/restore.sh slow-<轮次>.log
docker compose stop client
perf/ops/observe.sh <轮次> 500 &
docker run --rm -i -w /results -v "$PWD/perf/results:/results" grafana/k6:latest run \
  -e PERF_BASE=http://host.docker.internal:8800 -e TIER=<tier> -e SUMMARY_NAME=<轮次> \
  - < perf/mixed.js        # attachment 档用 perf/attachment.js（需临时 compose.override.yaml）
docker compose start client
```

原始大件（未裁剪 JSON、全部控制台输出、observe 日志）在 `perf/results/`（不入仓）。
