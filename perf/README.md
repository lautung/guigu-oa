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
| `ops/round.sh` | **一档执行器**：restore → 停 client → observe → k6 → 当档拷慢日志/行数 → 启 client |
| `ops/snapshot.sh` | 快照：mysqldump + process-definitions / minio-data 卷 tar → `perf/snapshot/` |
| `ops/restore.sh` | 恢复：down -v → 卷还原 → 导 dump → up --wait → 慢 SQL 开关 |
| `ops/row-counts.sh` | `oa_process` / `oa_process_record` / `ACT_*` 行数 |
| `ops/observe.sh` | docker stats ~1Hz 采样到 CSV |

`perf/results/`（k6 汇总、采样、造数摘要）与 `perf/snapshot/` 不入仓；
证据摘要（≤100KB/文件）复制到 `docs/test/evidence/`。

## 一轮完整执行

一条命令（#119）：`./scripts/test-round.sh perf`（恢复基线快照 → 造数 → 快照 → 全档位 → 取证）。
手动分步如下：

```sh
# 0. 全新栈（首次造数用全新卷）
docker compose down --volumes --remove-orphans
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait
./scripts/compose-baseline-check.sh

# 1. 造数（宿主机执行；实测 Phase A 27s + Phase B 20s + 附件 4s）
TARGET=10000 FINISHED=4000 node perf/seed.mjs

# 2. 快照
perf/ops/snapshot.sh

# 3. 每档一条命令（restore → k6 → 当档取证 → 恢复 client）
perf/ops/round.sh smoke smoke
perf/ops/round.sh baseline-1 baseline
perf/ops/round.sh baseline-2 baseline
perf/ops/round.sh baseline-3 baseline
perf/ops/round.sh mid-1 mid
perf/ops/round.sh stress-1 stress
perf/ops/round.sh attachment-1 baseline perf/attachment.js   # 自动注入 S3_PUBLIC_ENDPOINT 覆写
```

`round.sh` 已内置：`restore.sh slow-<name>.log`、停 client、`observe.sh <name>` 采样、
k6（`SUMMARY_NAME=<name>`）、**当档**拷出 `slow-<name>.log` 与 `rows-<name>.txt`、恢复 client、
附件档的 `compose.override.yaml` 注入与清理。

**取证顺序是硬约束**：每档 `restore.sh` 都会 `down -v` 重建数据卷，慢日志随卷销毁——
整轮结束后再拷只能看到最后一档。GC 日志同理：若注入 GC 取证（见下），必须在**下一档
restore 之前**拷出；`round.sh` 只自动拷慢 SQL，GC 需要人工介入时按手动步骤跑。

### 手动跑单步（round.sh 的内核，调试/单步复现用）

```sh
perf/ops/restore.sh slow-baseline-1.log
docker compose stop client
perf/ops/observe.sh baseline-1 420 &
docker run --rm -i -w /results -v "$PWD/perf/results:/results" grafana/k6:latest run \
  -e PERF_BASE=http://host.docker.internal:8800 -e TIER=baseline \
  -e SUMMARY_NAME=baseline-1 - < perf/mixed.js 2>&1 | tee perf/results/baseline-1.txt
docker compose exec -T mysql sh -c 'cat /var/lib/mysql/slow-baseline-1.log' > perf/results/slow-baseline-1.log
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

与 `round.sh` 配合：override 要在跑 `round.sh` **之前**放好（其内部 restore 会按 override 起栈），
GC 取证必须在**下一档 restore 之前**完成（restore 会 `down -v`）。附件档的 `round.sh` 会改写同一个
`compose.override.yaml`（注入 `S3_PUBLIC_ENDPOINT`），GC 与附件档不要同轮跑。

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

## 造数执行位置（2026-10-30 复跑修正）

造数在**宿主机**执行，默认 `PERF_BASE=http://localhost:8800`、`PERF_MINIO_PUT=http://localhost:9000`：

```sh
TARGET=10000 FINISHED=4000 node perf/seed.mjs
```

`seed.mjs` 会按 `MINIO_PUT_BASE` 改写预签名 URL 的 host，而 **host 参与 SigV4 签名**：
在容器内用 `host.docker.internal:9000` 改写会让 MinIO 返回 `403 SignatureDoesNotMatch`
（Phase A/B 已成功，Phase C 附件全失败并中止）。需要容器内造数时，必须同时把 `service-oa` 的
`S3_PUBLIC_ENDPOINT` 指到同一个容器可达地址，不要只改 `MINIO_PUT_BASE`。
宿主机 TUN 代理只污染 `host.docker.internal` 的解析，`localhost` 直连不受影响。
