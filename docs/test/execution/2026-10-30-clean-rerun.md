# 干净环境五类复跑验证（#119）

- 日期：2026-10-30（本机时间 11:29–12:39）
- 执行票：[从干净环境验证测试资产一键复跑 #119](https://github.com/lautung/guigu-oa/issues/119)
- 地图：[#107](https://github.com/lautung/guigu-oa/issues/107)；方案：[`../plan.md`](../plan.md)
- 性质：**验证轮**——从干净数据态按「接口 → 功能 → 性能 → 安全 → 兼容性」复跑五类资产，
  并把安全整轮 after-fix（61 条定向 + ZAP + 存储型 XSS）并入本轮；同时落地一键编排
  `scripts/test-round.sh`。
- 证据目录：[`../evidence/2026-10-30-clean-rerun/`](../evidence/2026-10-30-clean-rerun/)

## 1. 环境与被测版本

| 项 | 值 |
|---|---|
| 被测版本 | 仓库 `c245183`（`main`，工作区干净） |
| 环境 | 本机 Docker Compose 演示栈；macOS arm64、Docker Desktop 29.6.2 / Compose 5.3.1 |
| 运行时 | Node v24.18.1、JDK 21.0.12、Playwright 1.64（chromium-1248 / webkit-2370） |
| 压测/扫描 | k6 v2.3.0（arm64 镜像）、ZAP 2.17.0 Core（`/tmp/zap/ZAP_2.17.0`） |
| 栈 | mysql 8.4 / redis 7.4 / minio bitnami 2025.7.23 / service-oa（JDK 21、Spring Boot 4.1.0、Flowable 8.0.0）/ client（nginx 1.31.8 生产构建） |
| 入口 | `http://localhost:9530`（API 经 `/dev-api`）、`http://localhost:8800` |
| 网络 | 本机受限网络：Maven 用 `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public` |

## 2. 执行序列与耗时

从 `docker compose down --volumes --remove-orphans` 开始，五类按序执行；性能前另做干净基线快照，
安全前恢复该快照。逐项耗时见证据目录 `timings.tsv`。

| 阶段 | 命令 | 结果 | 耗时 |
|---|---|---|---|
| 干净重置 | `docker compose down --volumes --remove-orphans` | 数据卷、网络全删 | 2s |
| 构建起栈 | `MAVEN_MIRROR_URL=… docker compose up -d --build --wait` | 5 容器 healthy | 121s（冷依赖）／31s（层缓存命中） |
| 环境验收 | `./scripts/compose-baseline-check.sh` + `node scripts/compose-demo-smoke.mjs` | 26/26；`Bob pending=1, ended=1, Carol copied=2, attachments=1` | 3s |
| 接口 | `DEMO_ORIGIN=http://localhost:9530 npm run test:api` ×2 | 66/66 两轮 | 16s / 15s |
| 功能 | `npm run test:e2e:stack` | 27/27 | 57s |
| 性能 | 恢复基线 → 造数 → 快照 → 7 档 | 全档位达标 | **45m22s** |
| 安全 | 恢复基线 → 61 条定向 → ZAP → 存储型 XSS | 61/61；ZAP 0 High；XSS 3/3 | 6m26s |
| 兼容性 | `E2E_BASE_URL=http://127.0.0.1:9530 npx playwright test` + `npm run test:e2e:stack` | 189 次运行 145 过/44 跳/0 败；27/27 | 52s + 56s |

**总耗时：69 分 55 秒**（11:29:00–12:38:55）。其中性能占 45 分 22 秒（造数 1 分 04 秒、
7 档 40 分钟、恢复/快照约 4 分钟）；不含性能的五类约 24 分钟。

> 上表中接口/功能各有一轮由 `scripts/test-round.sh api|functional` 在第二轮干净栈上复跑
> （12:22–12:25），用于验证一键编排；结果与手工首轮一致（66/66 ×2、27/27）。

## 3. 各类结果

### 3.1 接口（66/66 ×2）

- 56 个业务端点 / 66 条黑盒用例，干净卷两轮全绿、0 跳过、0 重试；首轮 16s，次轮 15s；
  编排脚本复跑 15s / 24s（第二轮的 24s 含一次失败的 `npx` 重试前摇，不影响结果）。
- 权限四类矩阵与 `#145` 新状态码契约（认证失败 401、容器级 404/405）继续成立。

### 3.2 功能（27/27）

- 13 个场景（P0 8 + P1 5）在 Chromium + WebKit 手机 smoke 共 27 次运行全部通过，56.5s；
  含附件真实 MinIO、跨用户可见性、模板发布冻结与四类配置 CRUD。
- 每轮唯一残留仍是 1 个已发布演示模板（ADR 0005 冻结），不影响后续接口/安全套件。

### 3.3 性能（7 档全过，中位 319 req/s）

数据：快照 = 10,001 实例（10,000 造数 + 1 演示）/ 6,000 待办 / 4,001 已办 / 110 附件对象，
与 #124/#147 同口径；每档前 `restore.sh` 恢复（32s）。

| 档位 | 吞吐 | HTTP 失败 | 关键 p95 | 判定 |
|---|---|---|---|---|
| 冒烟 | — | 0 | 8/8 动作 100% 成功 | ✅ |
| 基线 ×3 | 306.5 / 319.0 / 328.0，**中位 319.0** | ≤0.013% | findProcessed 357ms、findPending 81ms、login 285ms | ✅ 阈值全过 |
| 拐点 mid-1（50 VU） | 319.0 | 0.001% | findProcessed 655ms、startUp 219ms | ✅ |
| 压力 stress-1（100 VU） | 323.0 | 0.003% | 读最高 findProcessed 1187ms（≤2s）、写最高 approve 505ms（≤3s） | ✅ |
| 附件 attachment-1 | 340.0 | 0 | 签名 43ms、上传链 154ms、下载链 63ms | ✅ |

- 基线 8 动作 p95 中位：findPending 81 / show 67 / findProcessed 357 / findStarted 40 /
  processTypeAll 26 / login 285 / approve 83 / startUp 111（ms），全部低于判定线。
- 吞吐 319 ≥ 正式回归下限 259 req/s（#124），但低于 #147 建议校准的 326 req/s，见 §6。
- 7 档均无阈值违反、无 5xx 异常；k6 传输错误 ≤0.013%。容器在每档 restore 时重建，
  档内未见崩溃或健康检查失败（下一档 restore 会销毁上一档容器，无法事后 inspect）。

### 3.4 安全（61/61 + ZAP + XSS，整轮 after-fix）

- **定向 61 条全部通过**（5m33s）：JWT 10/10、IDOR 9/9、MEM 5/5、GATE 3/3、UPLOAD 9/9、
  CSRF 5/5、BRUTE 2/2、LEAK 9/9、HDR 5/5、PAGE 3/3、XSS 1/1；0 跳过。
  基线轮的 15 个失败项（10 个缺陷）全部转绿。
- **ZAP 2.17.0 被动**：10 条告警 = 0 High / 5 Medium / 0 Low / 5 Informational。
  5 条 Medium 全是 `CSP: style-src 'unsafe-inline'`（ADR 0026 接受项），
  基线中的 CSP 未设置、X-Frame-Options、X-Content-Type-Options、nginx 版本泄露全部消失。
- **存储型 XSS**：`tests/security/` 3/3 通过（含 nginx 版本头与审批字段 XSS 不执行）。
- 判定：S1/S2 清零，安全整轮 after-fix 完成；accepted risk 有书面理由（ADR 0026 /
  页面级授予 ADR 0014）。

### 3.5 兼容性（189 次运行 145 过/44 跳/0 败 + 真实栈 27/27）

- 五格矩阵：桌面/手机 × Chromium/WebKit 全量 + 1440 Chromium 关键页扫描，
  189 次运行 51.5s；145 通过、44 按视口设计性跳过、0 失败（与 #117 一致）。
- 真实栈补充：27/27（含 WebKit 手机真实登录 → 待处理 → 详情 → 通过），55.8s。
- 三类硬失败为零；截图与控制台日志在 `evidence/2026-10-30-clean-rerun/compat/`。

## 4. 一键复跑入口（本轮新增）

`scripts/test-round.sh` 把方案 §8 的入口串成一条命令（本轮逐相验证）：

```bash
./scripts/test-round.sh all          # 全流程（约 50–60 min）
./scripts/test-round.sh clean        # 干净起栈 + 26 项基线 + 冒烟 + 基线快照
./scripts/test-round.sh api|functional|perf|security|compat
```

- `clean` 在验收后把干净基线快照写到 `BASE_SNAPSHOT`（默认 `/tmp/guigu-oa-base-snapshot`）；
  `security` 用它恢复干净数据态；`perf` 会覆盖 `perf/snapshot` 为 10k 快照。
- **验证范围**：`clean`、`api`、`functional`、`security`、`compat` 五个子命令在本轮按序执行通过；
  `perf` 子命令内的命令与本轮手工执行的完全一致（恢复 → 造数 → 快照 → 7 档），但未再过一遍脚本入口。
  `all` 是这些相的顺序串联，没有单独整轮跑第二遍（成本约 70 min）。
- 造数固定在宿主机执行（`localhost:8800/9000`）；容器内造数会因预签名 URL 的 Host 被改写而 403，
  详见 §5.1。

## 5. 失败点与修复

### 5.1 造数在容器内执行 → 附件阶段 MinIO 403（资产缺陷，已修）

按 `perf-load-model` 旧口径「造数统一在 `node:24-slim` 容器内执行」，Phase A/B 成功
（9999 条实例 27s、4000 条审批 20s），Phase C 110 个附件全部 `MinIO PUT HTTP 403` 中止：
`seed.mjs` 把预签名 URL 的 host 改写成 `host.docker.internal:9000`，而 host 参与 SigV4 签名。
改为宿主机执行（默认 `localhost:9000`）后 4s 完成 110/110。

修复：`perf/README.md` 增「造数执行位置」、`docs/test/plan/perf-load-model.md` 与
`docs/test/execution/2026-10-30-perf-baseline.md` 修正旧结论；失败日志留证
`evidence/2026-10-30-clean-rerun/perf/seed-container-403.txt`。

### 5.2 编排脚本的兼容性与证据路径（本轮新增，已修）

1. **bash 3.2 + 全角括号**：`"完成：$label（…）"` 被解析成变量名 `label（` 的一部分，
   `set -u` 报 unbound variable；改为 `${label}（`，并排查同类写法。
2. **Playwright 报告路径按 config 目录解析**：真实栈功能套件与安全 XSS 的 JSON/JUnit 落在
   `tests/e2e-stack/test-results/…` 与 `tests/security/test-results/…`，不是 `test-results/…`。
3. **mock 矩阵证据被后一步清理**：`e2e-stack` 启动时会清理默认 `outputDir`，会删掉刚生成的
   兼容性截图；编排改成先归档截图再跑真实栈。
4. **接口报告被下一套件清理**：手工首轮先跑功能导致 `test-results/api/` 被清空；编排在每相结束
   立即归档，避免依赖后续顺序。

## 6. 环境差异（性能数字）

同一 commit、同一快照、同一脚本，与 #147 复跑轮对比：

| 档位 | #147（凌晨窗口） | 本轮（午间窗口） | 变化 |
|---|---|---|---|
| 基线中位 | 362.6 req/s | 319.0 req/s | −12.0% |
| 拐点 mid-1 | 384.3 | 319.0 | −17.0% |
| 压力 stress-1 | 378.2 | 323.0 | −14.6% |
| 附件 attachment-1 | 360.6 | 340.0 | −5.7% |
| findProcessed 基线 p95 中位 | 326ms | 357ms | +9.5% |

- 无代码变化；差异落在 #124 已记录的 24% 同机波动包络内，属压测窗口/宿主负载差异。
- 判定仍通过：所有档位达到正式回归下限 259 req/s，p95 与错误率全部达标。
- **建议**：#147 提出的「下限校准到 326 req/s」暂缓采用，或只在同窗口 3 轮中位数上判定；
  否则午间窗口会误报回归。该建议已回写本记录的对比表，供后续轮次引用。

## 7. 证据

目录 [`../evidence/2026-10-30-clean-rerun/`](../evidence/2026-10-30-clean-rerun/)（848 KB，符合 ≤5 MB 上限）：

| 子目录/文件 | 内容 |
|---|---|
| `timings.tsv` | 每步耗时（本轮总耗时口径） |
| `api/report.json`、`api/junit.xml` | 接口第二轮（66/66） |
| `functional/report.json`、`functional/junit.xml` | 功能套件（27/27） |
| `perf/k6-summary-*.json`（精简）、`k6-console-*.txt`、`rows-*.txt`、`stats-*.csv.gz`、`seed-summary.json`、`seed-container-403.txt`、`slow-*.txt` | 7 档汇总、造数与失败留证（慢日志转 `.txt` 以避开 `*.log` 忽略规则） |
| `security/security-results.json`、`security-summary.md`、`zap-summary.md`、`zap-alerts.json`、`xss-report.json`、`junit-xss.xml`、`environment.md` | 61 条定向、ZAP 摘要、存储型 XSS |
| `compat/report.xml`、`desktop-*/`、`mobile-*/`（控制台日志） | 五格矩阵 JUnit 与控制台日志；截图不入仓（同 #117 已有基线，本机 `test-results/`） |
| `compat/e2e-stack-junit.xml` | 真实栈补充 smoke 27/27 |

大件（不入仓）：完整 ZAP HTML/JSON 在
`/tmp/security-evidence-full/2026-10-30-clean-rerun/zap-full/zap-report.html`；
k6 原始汇总（560 KB/个）与完整 observe 日志在 `perf/results/`。

## 8. 遗留与限制

1. `scripts/test-round.sh all` 未作为单次调用整轮重跑（各子命令已按序验证），见 §4。
2. 性能绝对数字受窗口影响（§6）；跨窗口只比较相对改善，不用 326 req/s 卡午间窗口。
3. 安全仍是定向 + 被动扫描，不是渗透测试；`style-src 'unsafe-inline'` 与页面级授予是既定接受项。
4. 接口套件未覆盖 `sysMenu/doAssign` 的「旧非配置 id 静默跳过」分支（需要 legacy 数据，
   干净卷不覆盖），与 #120 结论一致。
5. 兼容性无真机；WebKit 是 Safari 近似。模板图标仍依赖外部 CDN。
6. 38 条从不编译/执行的 `@Test`（Activiti 7 课程 demo）去留、五类测试是否进 CI 门禁/定时任务，
   仍待地图后续决策。
