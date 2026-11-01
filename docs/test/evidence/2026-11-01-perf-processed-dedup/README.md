# 证据索引：已处理按审批去重的供给换轨（#186，ADR 0049）

- `environment.md`：环境、数据快照、档位与口径（含「种子数据无同人两节点行，对比度量的是供给换轨本身」的已知限制）。
- `before/`：旧供给（main@983641c 镜像）baseline 档——k6 裁剪汇总、console、行数、慢日志。
- `after/`：两轮新供给——`after-dedup-1` 是初版形状（载荷 join 在窗口外，p95 回归 383ms，保留作取证）；
  `after-dedup-2` 是改形后终态（id 先聚合 + 窗口收在派生表内）。
- `emitted-sql-after-reshape.txt`：general_log 捞取的实发原文（行查询；count 同形状见 ADR 0049）。
- `explain-page-before-reshape.txt`：初版形状 EXPLAIN ANALYZE（17k×4 次 PK 回表，单条 63.1ms）。
- `explain-page-after-reshape.txt`：改形后 EXPLAIN ANALYZE（19.6k 分组，单条 26ms，仅当页 10 行回表）。
- `explain-count.txt`：count 的 EXPLAIN ANALYZE（37.5ms，与抄送计数同形状，保留）。

结论：初版形状 findProcessed p95 150→383ms（回归 2.5×，唯一回归动作）；按实发计划改形后
p95 304ms、med 114ms（before med 79/p95 151），距 500ms 判定线余量 40%；压测全程慢 SQL
日志无业务语句（<100ms）；总吞吐与 before 持平（175.6k vs 171.6k 请求）。残差来自 count
对该用户动作记录的全量去重聚合（引擎时代是 ACT_HI_TASKINST 索引计数），属换供给固有成本。
