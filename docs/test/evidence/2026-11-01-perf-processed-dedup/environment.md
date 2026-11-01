# 环境（与 perf/README.md「环境」节一致）

- 压测机与被测栈同机：宿主机 10 vCPU / 32 GB（arm64），Docker VM 10 vCPU / 8 GB。
- Docker 29.6.2；k6 v2.3.0 `linux/arm64`（`grafana/k6@sha256:9cb0023c…`）。
- 数据：`perf/snapshot/`（2026-11-01 02:09 快照，10k 实例：6k 待办 + 4k 已结束，bob 单节点处理），
  每档 `restore.sh` 全量还原（down -v 重建卷）。
- before 侧 = main@983641c 镜像（2026-11-01 19:57 构建，#186 改动之前）；after 侧 = #186 工作树重建镜像。
- 档位：`baseline`（mixed.js，20 VU，30s 爬坡 + 5min 稳态，findProcessed 权重 10%，bob，页 1/10），
  before/after 各一次（`perf/ops/round.sh before-dedup-1 baseline` / `after-dedup-1 baseline`）。
- 压测期间 client 容器停止，k6 经 `host.docker.internal:8800` 直连后端。
- 绝对数字只在本机有效；结论只看同快照同脚本两轮的相对变化。
- 已知口径：种子数据每条审批只由 bob 处理一次（无同人两节点行），去重不缩减 bob 的结果集——
  本对比度量的是「供给换轨」（引擎历史任务分页+回查 → 账本动作记录 SQL）本身的开销变化，
  不是行数缩减收益。
