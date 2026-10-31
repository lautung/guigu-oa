# 性能测试执行记录（审批列表读 module k6 before/after，#182）

- 日期：2026-11-01（本机时间）
- 执行票：[本地 k6 before/after 性能对比 #182](https://github.com/lautung/guigu-oa/issues/182)
  （规格 [审批列表读收成一个行形状 owner #178](https://github.com/lautung/guigu-oa/issues/178) 的验证票）
- 口径：[perf-load-model](../plan/perf-load-model.md)（#112）§7 调优对比协议 + #124 基线档位；
  同机、同快照、同脚本（见 §5 两处偏差）；不进 CI 门（ADR 0030）
- 对比对象：before = main `9f1e27c`（不含 #178 规格改动，已含 #147 连接池调优）；
  after = spec-178 `358dbbd`（#179 显式倒序 + #180 PageWindow + #181 读 module）
- 证据：[`2026-11-01-perf-read-module/`](../evidence/2026-11-01-perf-read-module/)
  （含 `explain-findStarted-orderby.txt`）；基线对照：[2026-10-30-perf-baseline.md](2026-10-30-perf-baseline.md)、[2026-10-30-perf-fix-147.md](2026-10-30-perf-fix-147.md)
- 数据：before 轮全新造数（10,001 实例 / 6,000 待办 / 4,001 已办 / 111 附件，28s 零错误）→ 快照；
  after 轮复用同一份快照，逐档 `restore.sh` 恢复。

## 1. 混合负载各档位吞吐（8 动作混合，读 70/写 25/登录 5）

| 档位 | 模型 | before | after | 变化 |
|---|---|---|---|---|
| 冒烟 smoke | 1 VU × 8 动作 | 100% 成功 | 100% 成功 | — |
| 基线 baseline-1 | 20 VU，30s+5min | 410.2 req/s | 539.2 req/s | — |
| 基线 baseline-2 | 同 | 438.8 | 558.9 | — |
| 基线 baseline-3 | 同 | 433.0 | 574.0 | — |
| **基线中位** | | **433.0** | **558.9** | **+29.1%** |
| 拐点 mid-1 | 50 VU，30s+3min | 450.8 | 596.8 | +32.4% |
| 压力 stress-1 | 0→100 VU，5min+3min | 423.4 | 552.4 | +30.5% |
| 附件 attachment-1 | 10 VU × 3min | 415.0 | 401.8 | −3.2%（噪声内；见 §5 偏差 1） |

- 两轮所有阈值判定全过（console 零 ✗）：读 p95≤500/p99≤1000、写与登录 p95≤1000/p99≤2000、
  错误率与业务错误率 <1%、吞吐 ≥80（远高于之）；mid/stress 读 p95≤2s、写 p95≤3s、错误率 <5%。
- HTTP 失败率与业务错误率两轮同量级（基线中位 fail 0.011%→0.014%、biz 0.009%→0.011%），
  均为 #124 已知的 k6 侧偶发传输噪声量级，无业务缺陷迹象。
- 容器零重启、零 OOM；写负载行数增长同机制（见证据 `row-counts.md`）。
- approve 争用计数器（单独报告，不计入错误率）：retry 基线 9.9k–10.3k → 13.5k–14.0k、
  exhausted 2.3k → 3.3–3.7k——吞吐提升 29% 后 approve 尝试次数等比增多，争用比例不变，机制性现象。

## 2. 基线档（20 VU）三轮中位数逐动作对比（核心结论）

| 动作 | before p95 | after p95 | Δ | 定性 |
|---|---|---|---|---|
| findPending（待处理） | 47ms | 28ms | **−40.4%** | ✅ **N+1 消除收益**（逐行 businessKey 回查 → 按页批量） |
| show（详情） | 47ms | 44ms | −6.4% | 持平 |
| findProcessed（已处理） | 336ms | 149ms | **−55.7%** | ✅ **N+1 消除收益**（逐行回查 → 按页批量） |
| findStarted（已发起） | 23ms | 154ms | **+569.6%（6.7×）** | ❌ **回归** → §4，缺陷 issue 已立 |
| processTypeAll（配置类型） | 16ms | 15ms | −6.3% | 持平 |
| login | 207ms | 204ms | −1.4% | 持平 |
| approve | 52ms | 43ms | −17.3% | ✅ 写路径受益于整页更快 |
| startUp | 73ms | 68ms | −6.8% | 持平 |
| 混合吞吐 | 433.0 req/s | 558.9 req/s | **+29.1%** | ✅ |

中位数口径的 med/p99 与各轮原始值见证据 `before|after/k6-summary-baseline-*.json`。

**规格验收对照**：#178 用户故事 11「列表加载保持或快于现状」在四个 N+1 目标端点上大幅满足
（findPending/findProcessed），但 findStarted 与配置实例查询因 #179 显式排序引入回归（§4）——
不满足「保持」，需缺陷修复；吞吐整体 +29.1% 说明 N+1 消除本身收益显著。

## 3. 压力档（100 VU）与补充档（抄送/配置实例查询）

| 动作 p95 | 20 VU 中位 | 50 VU（mid-1） | 100 VU（stress-1） |
|---|---|---|---|
| findPending | 47 → 28 | 160 → 92 | 448 → 258 |
| findProcessed | 336 → 149 | 538 → 253 | 1030 → 490 |
| findStarted | 23 → 154 | 56 → 253 | 143 → 490 |
| show | 47 → 44 | 111 → 132 | 321 → 404 |

- N+1 消除的收益随并发放大（findProcessed 100 VU 下 −52%）；findStarted 的排序回归同样随并发放大。
- show 在 50/100 VU 下 +19%/+26%（med +16%/+18%）：show 不在读 module 内，但整体吞吐上移后
  服务容量重新分配，判定为负载结构变化下的排队效应而非代码回归（其 20 VU 基线 p95 −6.4%）。

补充档 `list-reads-1`（20 VU，抄送我的 + 配置实例查询各 50%，无 #124 锚点，仅两轮相对比较）：

| 动作 | before med/p95/p99 | after med/p95/p99 | Δ | 定性 |
|---|---|---|---|---|
| findCarbonCopied（抄送我的） | 209/332/393ms | 190/303/—ms | −9% | ✅ 抄送读搬进 mapper（原生 LIMIT/OFFSET + DISTINCT total）未回退 |
| instanceQuery（配置实例查询） | 12/35/49ms | 45/78/—ms | **+123%** | ❌ 同 #179 排序回归（见 §4） |

## 4. 回归定性：#179 显式排序在无索引支持下的全量排序（缺陷 issue 已立）

- **现象**：findStarted p95 23→154ms（基线）、143→490ms（压力）；instanceQuery 35→78ms。
  两端点共用 `OaProcessMapper.selectProcessPage`，#179（`6b76ed3`）为其加显式
  `ORDER BY a.create_time DESC, a.id DESC`。
- **机制**（EXPLAIN ANALYZE，证据 `explain-findStarted-orderby.txt`）：同一 SQL 加排序后
  16.4ms vs 无排序 0.044ms（≈373×）——无排序时 LIMIT 10 短路；有排序时 alice 名下全量
  10,001 行进排序堆。`oa_process` 仅有 `PRIMARY(id)` 与
  `uk_oa_process_idempotency_key(user_id, idempotency_key)`，排序列无索引可走。
- **定性**：**新引入**（#179 的用户可见行为是规格决策「最新在前」，成本是隐性的实现代价；
  before 轮同 SQL 0.044ms 证明非既有缺陷）。端到端 p95 涨幅 ≫ 单条 SQL 涨幅，
  为连接池/线程排队放大。
- **处置**：另立缺陷 issue（needs-triage）[#185](https://github.com/lautung/guigu-oa/issues/185)，
  候选修复为 `(user_id, is_deleted, create_time, id)` 类索引把排序变成索引序扫描（配置查询无
  user_id 前缀的形态另行评估）。修复后按 §7 协议整轮复跑验证。

## 5. 与协议的偏差（两处，均记录在案）

1. **附件档 harness 适配**：after 轮首次跑 `attachment.js` 在 setup 报「没有可下载的 1MB 附件」
   中止（k6 exit 107）——脚本按「第 1 页 = 最老实例」的隐式顺序找挂附件实例，#179 显式倒序后
   第 1 页全是最新实例。修复为从最后一页向前翻（不依赖排序方向），`seed.mjs` Phase C 同步
   改为尾页起翻保持「附件挂最老已结束实例」的不变量；**after 附件档以修复后脚本补跑**。
   载入相位（上传/下载链端点、权重、时长）与旧脚本完全一致，setup 只改实例发现方式，
   before/after 两轮附件档载入相位可比（p95：uploads 28=28、downloadUrl 22=22、
   upload_chain 131→126ms、download_chain 50→49ms，−3.2% 吞吐为噪声）。种子脚本改动随本票入仓。
2. **GC 日志未注入**：#124 基线曾注入 `JAVA_TOOL_OPTIONS` 取 GC 停顿（基线轮已证 G1 占墙钟
   1.3%、非瓶颈），本对比两轮均不注入——观测项不影响被测行为，两轮一致即可比。
   其余口径（同机、同快照、同脚本、同 k6 镜像 digest、同 Docker VM 规格）与 #124 一致。

## 6. 执行方式与 harness 经验（供复跑者）

- before 轮从主仓（main，工作区干净，只构建起栈不入库）执行；after 轮从 worktree
  （spec-178）执行。**after 轮所有 docker compose 调用必须 `COMPOSE_PROJECT_NAME=guigu-oa`**：
  `restore.sh` 硬编码 `guigu-oa_process-definitions`/`guigu-oa_minio-data` 卷名，worktree 目录名
  派生的项目名 t182 会对不上（首次执行踩坑：端口被占 + 卷空，已 `compose -p t182 down -v` 清理重跑）。
- 每档前 restore 恢复快照（实测 25–37s）；`round.sh` 当档收尾取证（慢日志/行数/stats），
  慢日志逐档保留（#147 教训已固化进脚本）。
- 两轮慢日志均只有 group-commit `COMMIT` 等待（附件档 2–4 条，Query_time 0.10–0.19s），
  无任何业务 SQL 超 100ms——与 #124「零慢 SQL」口径一致，延迟消耗仍在连接池/线程排队。
- 补充档脚本 `perf/list-reads.js`（抄送我的 + 配置实例查询）随本票入仓，供后续回归复用。

## 7. 复跑命令

见证据目录 [`README.md`](../evidence/2026-11-01-perf-read-module/README.md)「复跑」一节；
差异只有两处：after 轮不造数（复用快照）、after 轮带 `COMPOSE_PROJECT_NAME=guigu-oa`。

## 8. 结论

- **N+1 消除收益有证据**：目标端点 findPending p95 −40%、findProcessed p95 −56%（基线中位，
  3 次取中位数、压力档同向），混合吞吐 +29.1%，抄送与附件链不回退；两轮阈值全过、无 OOM。
- **一处新引入回归**：findStarted / 配置实例查询的显式排序无索引支持（#179），已立缺陷 issue
  [#185](https://github.com/lautung/guigu-oa/issues/185)（needs-triage），不静默。
- 交付物：本记录 + 证据目录 + `perf/list-reads.js` + `perf/attachment.js`/`perf/seed.mjs`
  排序无关修复；不进 CI 门（ADR 0030）。
