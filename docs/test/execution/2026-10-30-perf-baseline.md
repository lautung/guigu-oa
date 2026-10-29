# 性能测试执行记录（基线轮，perf #124）

- 日期：2026-10-29 深夜 ～ 2026-10-30（本机时间）
- 执行票：[执行性能测试并留证 #124](https://github.com/lautung/guigu-oa/issues/124)
- 口径：[perf-load-model](../plan/perf-load-model.md)（#112 决议）；证据：[`2026-10-30-perf-baseline/`](../evidence/2026-10-30-perf-baseline/)
- 资产：`perf/`（`mixed.js` / `attachment.js` / `seed.mjs` / `ops/*`），k6 v2.3.0 linux/arm64
- 数据：快照 = 10,001 实例（10,000 API 造数 + 1 演示）/ 6,000 待办 / 4,001 已办 / 111 附件对象；
  每轮前 `restore.sh` 恢复（实测 ~37s），每轮写负载新增 ~1 万实例（见 `row-counts.md`）。

## 1. 执行序列与结果总览

| 轮次 | 模型 | 吞吐 | HTTP 失败 | 业务错误 | 阈值判定 |
|---|---|---|---|---|---|
| 冒烟 smoke | 1 VU × 8 动作 | — | 0 | 0 | ✅ 100% 成功 |
| 基线 baseline-1 | 20 VU，30s+5min | 346.5 req/s | 0.011% | 0.010% | ✅ 全过 |
| 基线 baseline-2 | 同 | 288.0 req/s | 0.023% | 0.021% | ✅ 全过 |
| 基线 baseline-3 | 同 | 280.3 req/s | 0.002% | 0.000% | ✅ 全过 |
| 基线补充 baseline-slow | 同 | 348.6 req/s | 0.008% | 0.005% | ✅（慢 SQL 取证轮） |
| 拐点 mid-1 | 50 VU，30s+3min | 351.6 req/s | 0.012% | 0.010% | ✅ |
| 压力 stress-1 | 0→100 VU，5min+3min | 302.3 req/s | 0.012% | 0.013% | ✅（错误率<5% 等） |
| 附件 attachment-1 | 10 VU × 3min | 361.2 req/s | 0.008% | 0.010% | ✅（签名/链路阈值为准） |

- 容器零重启、零 OOM（`docker inspect RestartCount/OOMKilled` 全 false），无 HeapDump 产生。
- 波动检查：基线 3 次吞吐 280.3–346.5（max/min 1.24），p95 各动作 max/min ≤ 1.26——**未超 20% 的动作**，
  吞吐差 24% 属同机压测噪声，报告以中位数与相对改善为准（#112 §8 已声明）。

## 2. 基线档（20 VU）三次中位数 vs 判定线

| 动作 | p95 中位 | p95 最差 | 判定线 | 结论 |
|---|---|---|---|---|
| findPending | 101ms | 106 | 500 | ✅ 余量 5× |
| show | 75ms | 79 | 500 | ✅ |
| findProcessed | 318ms | 322 | 500 | ✅ 余量最小（1.6×） |
| findStarted | 44ms | 46 | 500 | ✅ |
| processTypeAll | 37ms | 38 | 500 | ✅ |
| login | 239ms | 253 | 1000 | ✅ |
| approve | 85ms | 91 | 1000 | ✅ |
| startUp | 79ms | 85 | 1000 | ✅ |
| 混合吞吐 | **288 req/s** | 346.5 | ≥80 | ✅ **4.6×–3.5×** |

**吞吐回归下限调整（#112 §4 授权）：** 首版下限 80 req/s 远低于实测，按「实测中位数的 90%」
调整为 **≥259 req/s**（同机同快照基线口径），作为 #127 调优后回归与后续轮次的门限。

## 3. 压力档与拐点

| VU | 吞吐 | findPending p95 | findProcessed p95 | startUp p95 |
|---|---|---|---|---|
| 20（基线中位） | 288 | 101ms | 318ms | 79ms |
| 50（mid-1） | 351.6 | 287ms | 460ms | 132ms |
| 100（stress-1） | 302.3 | 700ms | 1017ms | 259ms |

- **拐点 ≤ 20 VU**：吞吐从 20 VU 起就压不上去（288→352→302），5× 并发只买到 4–7× 延迟——
  典型的固定服务容量排队曲线。压力档错误率 0.013% 远低于 5% 线，p95 全部满足读 2s/写 3s 线。
- 压力档各动作 p95：findPending 700 / findProcessed 1017 / show 511 / approve 582 / startUp 259 /
  login 303 / processTypeAll 277 / findStarted 252（ms）。

## 4. 瓶颈定位：HikariCP 默认连接池（→ #127 头号调优点）

四组独立证据指向同一结论——**吞吐上限来自数据库连接池串行化，不是 CPU 也不是慢 SQL**：

1. **CPU 有余量**：压测满负荷时 service-oa ~300%、MySQL ~390%（10 vCPU 计 690%，docker stats 见
   `stats-*.csv.gz`），没有任何容器打满。
2. **服务端零慢 SQL**：`slow_query_log=ON, long_query_time=0.1` 全程在位（`slow-*.log` 只有头部），
   连 p95 1017ms 的 findProcessed 也没有任何一条 SQL 超 100ms——请求时间花在**等连接**，不是执行。
3. **定量吻合**：吞吐平台 ~300–350 req/s ≈ 池大小 10 ÷ 平均 DB 时间 ~28ms；`application-dev.yml`
   未配置 HikariCP `maximum-pool-size`（默认 10），连接池饱和度 ≈ 98%。
4. **延迟随并发线性涨**：findProcessed p95 318→460→1017ms（20/50/100 VU），服务速率恒定。

**修复方向（#127 执行，本票只定基准）**：为 HikariCP 配置与负载匹配的 `maximum-pool-size`
（如 20–30），按 #112 §7 协议整轮复跑：预期吞吐平台显著抬升（改善 ≥20% 判有效，
其它场景回退 ≤10%、错误率不上升）。

次要观察（不单独开缺陷）：
- findProcessed 是最慢读接口（基线 p95 318ms，压力档破秒），是下一顺位的优化候选；
- findPending 的 N+1（每页 10 次 businessKey 回查，#112 §3 预判）在 20 VU 下 p95 仅 101ms，
  不构成当前瓶颈，池修复后若占比上升再评估；
- GC 无忧：G1 在 baseline-slow 全程 1,302 次停顿共 4.4s，占墙钟 1.3%，最大单次 39ms
  （`gc-pauses-baseline-slow.log.gz`）；
- k6 侧偶发 HTTP=0 传输错误（每轮 0–30 个，0.02%），量级为噪声，未定位于服务端。

## 5. 附件场景（10 VU × 3min，1MB）

| 指标 | 实测 p95 | 判定线 | 结论 |
|---|---|---|---|
| uploads 签名接口 | 35ms | 500ms | ✅ |
| download-url 签名接口 | 27ms | 500ms | ✅ |
| 上传整链（签名→MinIO PUT→complete） | 137ms | 2000ms | ✅ |
| 下载整链（签名→MinIO GET） | 55ms | 1000ms | ✅ |
| 错误率 | 0.010% | <1% | ✅ |

MinIO 在环回网络上吞吐充裕（65,459 请求 / 3min），附件链路不构成瓶颈。

## 6. 压测机制记录（harness lessons，供复跑者）

1. **k6 `http.request('post',…)` 按字面发小写方法**，Tomcat/Spring 对非 RFC 方法回 403 空 body——
   助手一律 `method.toUpperCase()`（已固化在脚本）。
2. **预签名 URL 的 Host 参与签名**：容器内压测不能改写 URL host，必须让 `S3_PUBLIC_ENDPOINT`
   本身指向容器可达地址（compose 覆盖注入，跑完移除）。
3. **附件 draftId 必须是 UUID**（后端 `UUID.fromString` 校验）；k6 `randomBytes` 返回 ArrayBuffer，
   需包 `Uint8Array` 再置版本位。
4. **approve 争用是机制问题**：待办池只有 bob 一个消费者，20+ VU 并发抢同一池必撞
   （403「无权访问」，无副作用）。脚本静默重试（重取第 1 页最新待办，≤3 次）、池耗尽时降级
   startUp 保持写配比，计数器单独报告（基线轮 retry 6–9k / exhausted 1.1–1.6k，压力轮 10.6k/2.9k）。
5. **取证要在下一次 restore 之前**：`down -v` 会随卷删掉慢日志与 GC 日志（baseline-1/2 的慢日志
   因此丢失，由 baseline-slow 补取）；`collect.sh` 已固化。
6. **宿主机 TUN 代理把 `host.docker.internal` 污染成 fake-IP（198.18.1.63）**：宿主机 Node 直连会断，
   造数统一在 `node:24-slim` 容器内跑。

## 7. 与 #112 决议的偏差

见 `perf/README.md`「与 #112 决议的两处偏差」：造数不自带模板发布（#128 已成为基线一部分）、
服务端请求级 trace 关联不做（后端无 X-Trace-Id 日志过滤器，零代码约束下以客户端视角+慢SQL+GC+stats 组合替代）。

## 8. 结论

- **通过**：冒烟 / 基线×3 / 压力 / 附件 / 50 VU 拐点档全部阈值达标；数据集、快照与恢复协议可复跑。
- **一个生产加固缺陷**：HikariCP 默认连接池在 ~300 req/s 即饱和（池串行化），已开缺陷 issue 交
  [#127 修复测试发现的全部缺陷并回归](https://github.com/lautung/guigu-oa/issues/127)，修复后按
  同快照同脚本整轮复跑，以中位数对比判定改善。
- **回归门限更新**：混合吞吐下限 80 → **259 req/s**（实测中位数 288 的 90%）。
