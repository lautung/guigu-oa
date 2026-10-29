# 性能测试：负载模型与判定口径

来源：[确定性能测试目标、场景与负载模型 #112](https://github.com/lautung/guigu-oa/issues/112) 的 resolution。
执行票：[执行性能测试并留证 #124](https://github.com/lautung/guigu-oa/issues/124)。
记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

本文件定「测什么、怎么判」，不写执行结果；执行记录见 `docs/test/execution/2026-10-30-perf-baseline.md`。

## 1. 范围与工具

- **k6 v2.3.0**（`linux/arm64`）容器直压后端 `host.docker.internal:8800`，不走 9530 nginx；
  脚本入仓 `perf/`（`mixed.js` 混合、`attachment.js` 附件），镜像获取用 `crane pull` + `docker load`
  （直连 pull 被本机代理打断）。
- 成功判定**同时**看 HTTP 状态与响应体 `code==200`：本系统业务错误一律 HTTP 200（#145），
  只看状态码会假性 0 错误。业务错误率是独立阈值 `business_error_rate`。
- `ab` 只做单接口快速对照，不参与正式指标。

## 2. 环境隔离（同机压测口径）

- 宿主机 10 vCPU / 32 GB，Docker VM **10 vCPU / 8 GB**；压测机与被测栈同机，CPU 饱和时双方一起被节流，
  绝对数字只在本机有效，对比只看同环境相对改善。
- 压测期间停 `client` 容器与系统 MySQL/Redis；**不改** Compose 资源限制。
- 后端就绪用 `GET /actuator/health` 判定，不用 Compose 健康状态。
- 每轮记录宿主机/VM 规格、镜像版本与 digest（`perf/README.md`）。

## 3. 场景与权重（混合：读 70 / 写 25 / 登录 5，不加 think time）

| 动作 | 接口 | 身份 | 权重 |
|---|---|---|---|
| 待处理列表 | `GET /admin/process/findPending/1/10` | bob | 25% |
| 审批详情 | `GET /admin/process/show/{id}` | bob | 20% |
| 已办列表 | `GET /admin/process/findProcessed/1/10` | bob | 10% |
| 已发起列表 | `GET /admin/process/findStarted/1/10` | alice | 5% |
| 配置查询 | `GET /admin/process/processType/findAll` | admin（配置菜单 gate） | 10% |
| 登录 | `POST /admin/system/index/login` | alice | 5% |
| 通过审批 | `POST /admin/process/approve` | bob | 15% |
| 发起审批 | `POST /admin/process/startUp` | alice | 10% |

- 「20 VU」= 20 个并发请求者。
- `processType/findAll` 在 `configurationMenuGate` 后，只有 admin 可调（#110 配置权限矩阵）。
- **approve 的争用与耗尽是压测机制问题**：所有待办都属于 bob（演示 BPMN 硬编码 `flowable:assignee="bob"`），
  20 个 VU 并发抢同一池会撞上已被消费的任务（业务码 403「无权访问」，无副作用）。脚本约定：
  首次撞车静默重试（重取 findPending 第 1 页 = 最新待办，最多 3 次）；池真正耗尽时该次写负载
  降级为一次 startUp（保持 25% 写配比）。计数器 `approve_retry_count` / `approve_exhausted_count`
  单独报告，不计入业务错误率。
- **附件独立成场景**（`attachment.js`）：上传链 = `POST /admin/attachments/uploads` → 预签名 PUT（MinIO）→
  `POST /{id}/complete`；下载链 = `POST /admin/attachments/processes/{pid}/{aid}/download-url` →
  预签名 GET。真实流量打在 MinIO，只有签名接口打后端，不混入混合指标。
  预签名 URL 的 Host 参与签名，压测期间由 compose 覆盖把 `S3_PUBLIC_ENDPOINT` 指到
  `host.docker.internal:9000`（脚本不得改写 URL host，否则 SignatureDoesNotMatch）。

## 4. 负载档位与通过标准

| 档位 | 模型 | 时长 | 通过标准 |
|---|---|---|---|
| 冒烟 | 1 VU，8 动作各 1 次 | ~1 min | 100% 成功，无意外 5xx |
| 基线 | 20 VU，30s 爬坡 + 5min 稳态，**3 次取中位数** | ~6 min/次 | 读 p95 ≤ 500ms、p99 ≤ 1s；写/登录 p95 ≤ 1s、p99 ≤ 2s；错误率 < 1%；吞吐 ≥ 80 req/s |
| 压力 | 0→100 VU 爬坡 5min + 100 VU 保持 3min，1 次 | ~8 min | 错误率 < 5%；读 p95 ≤ 2s、写 p95 ≤ 3s；无 OOM/容器重启；记录拐点 VU |
| 附件 | 10 VU × 3min，上传/下载 1MB 各约 50% | ~3 min | 签名接口 p95 ≤ 500ms；上传链 p95 ≤ 2s、下载链 p95 ≤ 1s；错误率 < 1% |

- 吞吐 80 req/s 是首版下限；首轮基线后若实测远高于它，把下限调整为**实测中位数的 90%**，
  作为后续回归基线（调整记录在 #124）。
- 压力档用于找拐点，不用基线标准卡它。

## 5. 数据量级与快照

- **实例**：10,000 条 `oa_process` = 6,000 待处理（bob 待办）+ 4,000 已结束，每条带流转记录；
  演示场景（1 待办 + 1 已办 + 1 附件）随栈初始化存在，计入基线。
- **附件**：100 个 1MB + 10 个 5MB，挂在已结束实例上（发起人 alice 是成员）。
- **造数**：`perf/seed.mjs` 走真实 API（startUp → approve），20 并发，按 findStarted/findProcessed
  总数**补差造数**（可安全重跑）；演示模板由 Compose 启动时 `DemoApprovalPublisher` 自动发布（#128），
  造数不自带发布步骤。直连 `ACT_*` 表被禁止，Flowable 运行时/历史一致性只能由引擎维护。
  宿主机的 TUN 代理会把 `host.docker.internal` 污染成 fake-IP，造数统一在 `node:24-slim` 容器内执行。
- **快照**：造数完成后 `perf/ops/snapshot.sh` = mysqldump（含 ACT_*/FLW_*）+ process-definitions 卷 +
  minio-data 卷。每轮压测前 `perf/ops/restore.sh` 恢复（实测 ~37s）：`down -v` → 卷还原 → 导 dump →
  `up --wait` → 打开慢 SQL。**写负载一次运行会新增上千实例，每次运行前都恢复快照。**

## 6. 观测（零代码改动）

- JVM：`compose.override.yaml`（临时、不入仓）经 `JAVA_TOOL_OPTIONS` 注入
  `-Xlog:gc*`（filecount 5 × 20m）与 `-XX:+HeapDumpOnOutOfMemoryError`，只压测期间存在。
- MySQL：`SET GLOBAL slow_query_log=ON, long_query_time=0.1`，每轮独立慢日志文件名。
- 资源：`perf/ops/observe.sh` ~1Hz 采样 `docker stats` 到 CSV。
- 数据增长：每轮前后 `perf/ops/row-counts.sh`（oa_process / oa_process_record / ACT_RU_* / ACT_HI_*）。
- k6 每请求带 `X-Trace-Id`：后端没有对应的日志过滤器，零代码改动前提下服务端请求级关联不可得
  （与 #112 §6 的偏差，见 `perf/README.md`），改用客户端视角 + 慢 SQL + GC + docker stats 组合。
- 不新增 Micrometer/Prometheus 端点。

## 7. 调优前后对比协议（#127 用）

- 调优前：恢复快照 → 冒烟 → 基线 3 次 → 压力 1 次，保存 k6 summary JSON 与控制台摘要。
- 调优后：同脚本、同参数、同快照、同环境整轮复跑。
- 比较：3 次基线的中位数（同时报告最差值）；波动处理：3 次中 p95 max/min > 20% 视为噪声大，补跑 2 次。
- **改善判定**：被调优场景 p95 中位数改善 ≥ 20%，或由不达标变为达标；同时其它场景 p95 中位数回退
  ≤ 10%、错误率不上升、吞吐不下降。
- 迭代中允许只复跑受影响场景快速验证，定稿必须整轮复跑。

## 8. 已知限制

- 同机压测互相节流（见 §2）。
- 容器内 k6 无法访问宿主机 `localhost`，预签名端点必须用 `host.docker.internal`；
  宿主机直连该域名受 TUN 代理影响（造数在容器内执行规避）。
- findPending 每页按 businessKey 回查 10 条 `oa_process`（N+1），是重点观测对象（#112 §3）。
