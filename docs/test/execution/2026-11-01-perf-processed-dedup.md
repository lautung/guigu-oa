# 性能取证：已处理按审批去重供给换轨（#186，ADR 0049）

- 日期：2026-11-01。目标：findProcessed（mixed.js baseline 档，20 VU，30s 爬坡 + 5min 稳态）。
- 方法：`perf/snapshot/`（2026-11-01 02:09，10k 实例时代快照）每档 `restore.sh` 全量还原；
  before = main@983641c 镜像，after = #186 工作树镜像；`perf/ops/round.sh <name> baseline` 每档一条命令。
- 证据：`docs/test/evidence/2026-11-01-perf-processed-dedup/`。

## 结果

| 指标（findProcessed，客户端视角） | before | after 初版形状 | after 改形终态 |
|---|---|---|---|
| med | 79.0ms | 152.2ms | 114.5ms |
| p95 | 150.6ms | 382.7ms | 304.5ms |
| p99 | 198.4ms | — | 403.2ms |
| 错误率 | 0.0087% | 0.0092% | 0.014% |
| 总请求 | 171,578 | 141,892 | 175,638 |

- 初版形状是**唯一回归动作**（其余七个动作 p95 全部持平，±3ms 内）——按 SQL 取证纪律
  （general_log 捞实发原文 → EXPLAIN ANALYZE）定性：17,010 分组全体做四表 PK 回表后整体排序，
  单条 63.1ms。改形（窗口收进派生表、id 先聚合、载荷 join 只碰当页行）后单条 26ms，p95 回落到 304ms。
- 终态残差（med 114 vs 79）：count 侧 `COUNT(DISTINCT)` 对该用户动作记录的全量去重聚合
  （37.5ms，17k 次 oa_process PK 回表验 is_deleted，与抄送计数同形状、语义与行查询一致），
  对比引擎时代 ACT_HI_TASKINST 索引计数是换供给的固有成本。压测全程慢 SQL 日志无业务语句。
- 压测期间个别 `dial i/o timeout` 为已知 TUN 代理污染 host.docker.internal 解析的间歇
  （错误率远低于 1% 阈值，两侧同环境）。
- 已知口径：种子数据每条审批只由 bob 处理一次，去重不缩减结果集；本对比度量的是供给换轨本身。
- 复跑命令：`perf/ops/round.sh <name> baseline`（快照在 `perf/snapshot/`）。
