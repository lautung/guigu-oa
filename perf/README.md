# 性能压测资产（#112 → #124）

决议见 [确定性能测试目标、场景与负载模型](https://github.com/lautung/guigu-oa/issues/112)；
执行记录见 `docs/test/execution/`（perf 轮）与 `docs/test/evidence/`。

## 工具

- **k6 v2.3.0**，`linux/arm64` 镜像。直连 `docker pull` 在本机被代理打断，可复现获取路径：
  `crane pull --platform linux/arm64 grafana/k6:latest k6.tar && docker load -i k6.tar`。
  本机已加载镜像 digest：`grafana/k6@sha256:9cb0023cc3d7c9b07bf271f554b2942a5b34f82bab6f84130478122493ee590c`（2026-10-24 构建）。
- 造数用 Node（仓库无新依赖，`perf/seed.mjs` 只用内置 fetch）。

## 环境（与 #112 §2 一致）

- 压测机与被测栈同机：宿主机 10 vCPU / 32 GB，Docker VM 10 vCPU / 8 GB。
- 压测期间：停 `client` 容器；k6 容器经 `host.docker.internal:8800` 直连后端，不走 9530。
- 后端就绪判定 `GET /actuator/health` 返回 UP。
- 绝对数字只在本机有效；对比只看同环境相对改善。

## 资产

| 文件 | 作用 |
|---|---|
| `mixed.js` | 8 动作加权混合（读 70 / 写 25 / 登录 5），`TIER=smoke/baseline/stress` |
| `attachment.js` | 附件独立场景：10 VU × 3min，上传/下载 1MB 各约 50% |
| `seed.mjs` | 造数：API 走真实流程造 10k 实例（6k 待办 + 4k 已结束）+ 110 个附件 |
| `ops/snapshot.sh` | 快照：mysqldump + process-definitions / minio-data 卷 tar → `perf/snapshot/` |
| `ops/restore.sh` | 恢复：down -v → 卷还原 → 导 dump → up --wait → 慢 SQL 开关 |
| `ops/row-counts.sh` | `oa_process` / `oa_process_record` / `ACT_*` 行数 |
| `ops/observe.sh` | docker stats ~1Hz 采样到 CSV |

`perf/results/`（k6 汇总、采样、造数摘要）与 `perf/snapshot/` 不入仓；
证据摘要（≤100KB/文件）复制到 `docs/test/evidence/`。

## 一轮完整执行

```sh
# 0. 全新栈（首次造数用全新卷）
docker compose down --volumes --remove-orphans
docker compose up -d --build --wait
./scripts/compose-baseline-check.sh

# 1. 造数（10–15 分钟；若超 20 分钟降 TARGET=5000 并在 #124 记录取舍）
TARGET=10000 FINISHED=4000 node perf/seed.mjs

# 2. 快照
perf/ops/snapshot.sh

# 3. 每轮压测前恢复快照（含慢 SQL 开关，可带慢日志文件名区分轮次）
perf/ops/restore.sh slow-baseline-1.log

# 4. 压测期间：停 client，注入 GC 日志（compose.override.yaml，见下）
docker compose stop client
perf/ops/observe.sh baseline-1 400 &

# 5. 跑 k6（-w 指向结果目录；SUMMARY_NAME 区分轮次）
docker run --rm -i -w /results -v "$PWD/perf/results:/results" grafana/k6:latest run \
  -e PERF_BASE=http://host.docker.internal:8800 -e TIER=baseline \
  -e SUMMARY_NAME=baseline-1 - < perf/mixed.js 2>&1 | tee perf/results/baseline-1.txt

# 6. 收尾：行数、GC 日志、慢 SQL、docker cp
docker compose start client
```

### GC 日志注入（只在压测运行时）

仓库根放一个临时 `compose.override.yaml`（不入仓），重建 service-oa：

```yaml
services:
  service-oa:
    environment:
      JAVA_TOOL_OPTIONS: >-
        -Xlog:gc*,safepoint:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=20m
        -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp
```

取证：`docker compose exec service-oa sh -c 'gzip -c /tmp/gc.log.* > /tmp/gc-all.gz'` 再
`docker compose cp service-oa:/tmp/gc-all.gz perf/results/`。压测结束后删除该文件并
`docker compose up -d service-oa` 恢复。

### 慢 SQL

`restore.sh` 每轮已 `SET GLOBAL slow_query_log=ON, long_query_time=0.1`；取证：
`docker compose exec mysql sh -c 'cat /var/lib/mysql/<轮次>.log'`。

## 通过标准（#112 §4）

| 档位 | 模型 | 通过标准 |
|---|---|---|
| 冒烟 | 1 VU × 8 动作各 1 次 | 100% 成功，无意外 5xx |
| 基线 | 20 VU，30s 爬坡 + 5min 稳态，3 次取中位数 | 读 p95≤500ms、p99≤1s；写/登录 p95≤1s、p99≤2s；错误率<1%；吞吐≥80 req/s |
| 压力 | 0→100 VU 爬坡 5min + 100 VU 保持 3min，1 次 | 错误率<5%；读 p95≤2s、写 p95≤3s；无 OOM/容器重启；记录拐点 VU |
| 附件 | 10 VU × 3min | 签名接口 p95≤500ms；上传链 p95≤2s、下载链 p95≤1s；错误率<1% |

业务错误一律 HTTP 200（#145），所有成功判定同时看 `code==200`；`business_error_rate` 是独立阈值。

## 与 #112 决议的两处偏差

1. **造数不自带模板发布**：#128 落地后，演示模板由 Compose 启动时的
   `DemoApprovalPublisher` 自动发布并纳入基线检查（26/26），`seed.mjs` 发现没有
   已发布模板时失败退出并提示。决议当时的「脚本自带发布」是为不依赖尚未落地的 #128。
2. **服务端请求级 trace 关联不做**：后端无 `X-Trace-Id` 日志过滤器，零代码改动
   前提下无法把 k6 的 `X-Trace-Id` 关联到服务端日志；k6 仍然按请求发送该头，
   服务端耗时证据改由 k6 客户端视角 + 慢 SQL + GC + docker stats 组成。
