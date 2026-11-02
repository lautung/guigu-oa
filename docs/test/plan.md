# 云尚办公生产加固测试方案

- 来源：[把测试决策汇总成测试方案 #126](https://github.com/lautung/guigu-oa/issues/126)。
- 定位：本 effort（[地图 #107](https://github.com/lautung/guigu-oa/issues/107)）的**总方案**，定「测什么、怎么判、怎么复跑」。
  执行结果见 `docs/test/execution/`，结论与汇总见 `docs/test/report.md`（[#118](https://github.com/lautung/guigu-oa/issues/118)，待产出）。
- 依据决策：[#108](https://github.com/lautung/guigu-oa/issues/108)（目的与交付形态）、
  [#109](https://github.com/lautung/guigu-oa/issues/109)（资产盘点）、
  [#110](https://github.com/lautung/guigu-oa/issues/110)（接口与权限矩阵）、
  [#111](https://github.com/lautung/guigu-oa/issues/111)（Compose 基线）、
  [#112](https://github.com/lautung/guigu-oa/issues/112)（性能）、
  [#113](https://github.com/lautung/guigu-oa/issues/113)（安全）、
  [#114](https://github.com/lautung/guigu-oa/issues/114)（兼容性）、
  [#120](https://github.com/lautung/guigu-oa/issues/120)（接口）、
  [#121](https://github.com/lautung/guigu-oa/issues/121)（功能）、
  [#122](https://github.com/lautung/guigu-oa/issues/122)（数据与报告规范）、
  [#125](https://github.com/lautung/guigu-oa/issues/125)（测试基线入库）。
- 细节文档（本文件只汇总与索引，不重复用例表）：
  `plan/functional-stack-cases.md`、`plan/api-cases.md`、`plan/perf-load-model.md`、
  `plan/security-cases.md`、`plan/compatibility-matrix.md`。
- 领域词见 `GLOSSARY.md`，测试分层与踩坑见 `docs/agents/testing.md`，环境命令见
  `docs/development/compose-demo.md`。

## 1. 范围与依据

### 1.1 目的

**生产加固**：找出真实缺陷、性能瓶颈与安全漏洞，全部修复并回归，留下可复跑的测试资产与证据链
（#108）。交付物 = 测试方案 + 可复跑资产 + 执行记录 + 缺陷/瓶颈/漏洞清单（含证据）+ 修复与回归记录。
不是课程作业，也不是面试作品。

### 1.2 五类测试的深度与优先级

| 优先级 | 类别 | 定位 | 深度 | 判定性质 |
|---|---|---|---|---|
| 主 | 安全 | 找漏洞并修复 | 61 条定向用例 + ZAP 被动 baseline + 存储型 XSS | 基线轮允许有失败（目的就是找缺陷）；after-fix 轮 S1/S2 必须清零 |
| 主 | 性能 | 找瓶颈并调优 | 8 动作加权混合 + 基线/拐点/压力 + 附件独立场景 | 阈值判定 + 调优前后相对改善判定 |
| 基线 | 功能 | 回归 | 真实栈 13 个场景（P0 8 + P1 5），27 次运行 | P0 全过且无未关闭 S1/S2 |
| 基线 | 接口 | 回归 | 56 个业务端点 / 66 条黑盒用例 | 全过；S3 清零才可发布 |
| 最小 | 兼容性 | 回归 | Chromium + WebKit × 桌面/手机 + 1440 关键页扫描 | 三类硬失败为零 |

现有单测与 MockMvc 合同测试是**回归底座**，五类测试复用它们，不重写、不替换。

### 1.3 不在范围

沿用地图的 Out of scope：公网/生产/他人系统的压测与渗透、重写现有测试体系、与本系统无关的其他课程系统、
课程作业交付物（模板/评分点/Word 排版）、公网 demo 与真机/云真机验证、生产可观测性建设
（Prometheus/APM/仪表盘/告警）。

## 2. 环境与基线

### 2.1 被测栈

| 项 | 约定 |
|---|---|
| 栈 | Docker Compose 演示栈：`mysql` 8.4 / `redis` 7.4 / `minio`（bitnami 2025.7.23）/ `lautung-oa-server`（JDK 21、Spring Boot 4.1.0、Flowable 8.0.0）/ `client`（nginx 生产构建） |
| 起栈 | `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait`（受限网络才需要镜像源；全新卷先 `down --volumes --remove-orphans`） |
| 入口 | 客户端 `http://localhost:9530`，API 经 nginx `/dev-api` 代理到 `8800`；后端直连 `http://localhost:8800` |
| 环境变量 | `DEMO_ORIGIN`（接口/功能/冒烟，默认 9530）；`E2E_BASE_URL`（浏览器套件页面 baseURL，默认取 `DEMO_ORIGIN`）；`API_BASE_URL`（接口套件完整前缀覆盖，直连 8800 时用） |
| 验收 | `./scripts/compose-baseline-check.sh`（26 项）+ `node scripts/compose-demo-smoke.mjs` |
| 账号 | `admin`/`111111`（配置全授）、`user`（无配置授予）、`alice`（发起）、`bob`（处理）、`carol`（抄送） |
| 数据重置 | 功能/接口/兼容性用**干净卷**；性能/安全用全库 + 卷快照恢复（#112 协议） |
| 观测 | 零代码：GC 日志、慢 SQL、`docker stats`、行数统计；不新增 Micrometer/Prometheus |

演示审批由 `DemoApprovalPublisher` 在启动时现打 zip 并走正常发布流程（#128），从零到可测只需一条 `up`，
不再有手工播种步骤。健康检查在 Flyway、Flowable 建表与演示播种完成后才返回 UP（#129）。

### 2.2 环境隔离与网络约束

- 性能压测同机执行：宿主机 10 vCPU / 32 GB，Docker VM 10 vCPU / 8 GB；压测期间停 `client`，
  不改 Compose 资源限制。绝对数字只在本机有效，对比只看同环境相对改善。
- 受限网络：Maven 用 `MAVEN_MIRROR_URL`；k6 镜像用 `crane pull` + `docker load`；宿主机 TUN 代理会把
  `host.docker.internal` 污染成 fake-IP，造数在 `node:24-slim` 容器内执行。
- 安全测试只打本机 Compose（9530/8800），不碰 3306/6379 与 MinIO 控制台；禁止 DoS、主动扫描、分布式撞库。

## 3. 方法与工具

### 3.1 分层与归属（每类用例只归一层）

| 层 | 管什么 | 规模 |
|---|---|---|
| 客户端单测（Vitest） | 规则、状态机、phase 与迟到归属 | 496 条 |
| mock e2e（Playwright） | UI 状态机、并发、迟到结果不覆盖 | 7 个既有 spec / 41 场景；全矩阵 189 次运行 |
| 服务端单测 + MockMvc 合同（JUnit/H2/内存 Redis） | 接口契约与授权 gate | 333 条 / 32 个端点 |
| 真实栈功能套件（Playwright） | 系统级业务流程、持久化结果、跨用户可见性、真实附件与权限 | 13 个场景 / 27 次运行 |
| 黑盒接口套件（Playwright APIRequest） | 56 个业务端点的契约与权限分支 | 66 条 |
| 安全 / 性能 / 兼容性 | 见各自细节文档 | 61 条 / 7 轮档位 / 189 次运行 |

### 3.2 复用与新增

| 类别 | 复用的现有资产 | 本 effort 新增 |
|---|---|---|
| 功能 | 种子账号、演示审批、单测/mock e2e/MockMvc 原样保留 | `client/guigu-oa-client/tests/e2e-stack/`（13 个 spec）、`npm run test:e2e:stack` |
| 接口 | Playwright 1.64（已有依赖）、Compose 栈、登录 helper | `tests/api/`（9 个 spec）、`playwright.api.config.ts`、干净栈 `global-setup.ts` |
| 性能 | Compose 栈、快照协议、演示审批 | `perf/`（`mixed.js` / `attachment.js` / `seed.mjs` / `ops/*`） |
| 安全 | Playwright、Compose、账号矩阵、端点底稿 #110 | `scripts/security-scan.mjs`（61 条）、`scripts/zap-baseline.mjs`、`tests/security/`（3 个 spec） |
| 兼容性 | 既有 7 个 mock e2e 全量执行 | 4 个全量 project + 1440 扫描、`known-noise.ts`、`E2E_BASE_URL` 接线 |

## 4. 用例清单（索引）

| 类别 | 清单与阈值 | 数量 |
|---|---|---|
| 功能 | [`plan/functional-stack-cases.md`](plan/functional-stack-cases.md) | P0 8 + P1 5 = 13 场景；Chromium 26 次 + WebKit 手机 smoke 1 次 |
| 接口 | [`plan/api-cases.md`](plan/api-cases.md) | 56 端点 / 66 用例（A–F 六族） |
| 性能 | [`plan/perf-load-model.md`](plan/perf-load-model.md) | 8 动作混合 + 冒烟/基线×3/拐点/压力 + 附件独立场景 |
| 安全 | [`plan/security-cases.md`](plan/security-cases.md) | 61 条（JWT/IDOR/MEM/GATE/UPLOAD/CSRF/BRUTE/LEAK/HDR/PAGE/XSS）+ ZAP 被动 + 浏览器 XSS |
| 兼容性 | [`plan/compatibility-matrix.md`](plan/compatibility-matrix.md) | 5 格 / 189 次运行（145 过 / 44 按视口设计性跳过） |

用例设计方法（#121）：状态机派生为主（每条用例标注它验证的 GLOSSARY 状态或 ADR 决策），等价类/边界值为辅；
每流程 1 条正常 + 1 条关键异常，不做全组合。

## 5. 判定口径

### 5.1 进入准则（每轮执行前）

1. 栈已起且 5 容器 healthy；`compose-baseline-check.sh` 26/26 通过；`compose-demo-smoke.mjs` 通过。
2. 记录被测 commit、镜像版本与 digest、入口地址、执行日期。
3. 数据态正确：功能/接口/兼容性用干净卷（接口套件会拒绝压测造数卷）；性能/安全先恢复约定快照。
4. 环境变量指向正确入口；受限网络已配置镜像源。
5. 安全/性能的执行窗口与边界已确认（只打本机、停 client、不做 DoS）。

### 5.2 退出准则（按类）

| 类别 | 退出准则 |
|---|---|
| 功能 | P0 8/8 通过；无未关闭的 S1/S2；P1 失败记缺陷但不阻塞；重试 0；套件 ≤5 分钟 |
| 接口 | 66/66 通过、0 跳过、0 重试，连续两轮一致；56/56 端点覆盖；权限分支矩阵落地；S3 清零才可发布 |
| 性能 | 冒烟 100%；基线×3 中位数达标（读 p95 ≤500ms、写/登录 p95 ≤1s、错误率 <1%、吞吐 ≥ 回归下限）；压力档错误率 <5%、读 p95 ≤2s、写 p95 ≤3s、无 OOM/重启；附件档签名 p95 ≤500ms、上传链 ≤2s、下载链 ≤1s；调优改善 ≥20% 且他场景回退 ≤10% |
| 安全 | 基线轮：定向用例全部执行、ZAP 报告归档、失败项全部入账；after-fix 轮：S1/S2 清零、定向用例全绿、ZAP 的 Medium/Low 安全头告警消失；accepted risk 有书面理由 |
| 兼容性 | 三类硬失败为零（横向溢出、pageerror/console.error、办事主链路不可完成）；产品规则必过项通过；跳过只能是既定视口设计 |
| 底座 | `npm run verify` 与 `mvn -pl lautung-oa-server -am test` 全绿；覆盖率只报告、不卡红 |

### 5.3 发布门槛

- 无未关闭的 **S1/S2**；
- 每个缺陷 `closed = verified`：关闭评论含修复提交 SHA、回归用例路径、复跑证据链接；
- 五类的 after-fix / 调优轮记录齐全，且与基线轮使用同一脚本、同一快照、同一环境口径；
- 残余风险与已知局限写入 `report.md`（#118）。

### 5.4 证据与记录

- 一树三层：`docs/test/plan/`（测什么、怎么判）、`docs/test/execution/`（跑了什么、结果如何）、
  `docs/test/evidence/`（小摘要与截图）、`docs/test/report.md`（结论）。
- 执行记录一文件一轮，文件名 `<日期>-<类别>.md`（#122 原定 `execution/<类>.md`，落地时按日期分文件，
  便于多轮留证）；内容写命令、环境 commit、结果摘要、证据链接、缺陷 issue 链接。
- 入仓证据硬上限：单文件 ≤100KB、`evidence/` 整目录 ≤5MB；大件（完整 ZAP HTML、HAR、trace、视频、
  全量截图）留本机或 CI artifact，路径与重跑命令写进执行记录。
- 入仓证据必须脱敏：token/密码/密钥只留前缀，不贴可直接复用的攻击载荷。

## 6. 缺陷分级与账本

**issue 是唯一事实源**：一缺陷一 GitHub issue，标签 `bug` + `severity:Sx`，标题前缀 `[Sx] `，
分类（功能/接口/性能/安全/兼容）写在标题里。

| 级别 | 定义 |
|---|---|
| S1 | 数据泄露、越权、主链路不可用或可稳定复现的持久损害（阻断发布） |
| S2 | 关键功能错误或安全风险，存在绕行 |
| S3 | 一般功能或兼容性缺陷，不影响主链路 |
| S4 | 体验、文案、建议类 |

- 必填字段：复现步骤、期望/实际、环境（commit + Compose 栈 + 账号）、证据链接、影响面。
  扫描类缺陷的失败清单从 `security-results.json` 的 failures 全量拷贝，不凭记忆枚举
  （#184 正文凭记忆枚举漏了 sysMenu/processType 两个实体）。
- 生命周期：`bug` 且无 `defect:fixed` = 待修；加 `defect:fixed` = 已修待回归；**closed = verified**。
- 报告只放汇总表（issue 链接、级别、标题、状态、修复提交、回归证据），不复制明细。

## 7. 测试数据与快照

- **来源**：种子账号 + API 造数 + 全库/卷快照每轮恢复；禁止手工在演示库留不可复现数据，
  禁止使用真实个人信息。
- **唯一标记**：每轮生成 runId，写进请假原因、附件名、临时对象名；可回滚对象在 `finally` 自清理。
- **不可回滚对象**（已发布模板、审批实例、Flowable 流转）按快照组处理，以快照恢复而不是逐条删除。
- **性能数据**：10,000 条 `oa_process`（6,000 待办 + 4,000 已结束）+ 110 个附件对象；
  `perf/seed.mjs` 走真实 API 补差造数；快照 = mysqldump（含 `ACT_*`/`FLW_*`）+ process-definitions 卷 +
  minio-data 卷；每轮前 `perf/ops/restore.sh` 恢复（实测 ~37s）。
- **每轮执行记录必须写明**：所用快照、造数命令、账号清单、恢复方式。

## 8. 复跑入口

一条命令跑完五类：`./scripts/test-round.sh all`（干净栈 → 接口 → 功能 → 性能 → 安全 → 兼容性，
约 50–60 min）；也可按类取子命令 `clean|api|functional|perf|security|compat`（#119 验证）。
分步入口与实测耗时：

| 目标 | 命令 | 前置 | 实测耗时 |
|---|---|---|---|
| 五类一键 | `./scripts/test-round.sh all` | 见脚本头部前置 | 见 `execution/2026-10-30-clean-rerun.md` |
| 客户端静态检查 + 单测 | `cd client/guigu-oa-client && npm run verify` | `npm ci` | — |
| 服务端测试 | `cd server/lautung-oa-parent && mvn -pl lautung-oa-server -am test` | — | — |
| 全仓检查 | `./scripts/verify.sh` | — | — |
| 功能（真实栈） | `cd client/guigu-oa-client && npm run test:e2e:stack` | 干净栈 | 57s（27 次运行） |
| 接口（黑盒） | `DEMO_ORIGIN=http://localhost:9530 npm run test:api` | 干净栈 | 14–16s/轮 |
| mock e2e 全矩阵 | `E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e` | 栈 + 生产构建 | 51s（189 次运行） |
| 安全定向 | `node scripts/security-scan.mjs --out docs/test/evidence/<轮次>` | 栈 + 快照 | ~6min |
| ZAP 被动 | `node scripts/zap-baseline.mjs --out docs/test/evidence/<轮次>` | 栈 + ZAP 2.17.0 Core | — |
| 存储型 XSS | `cd client/guigu-oa-client && npm run test:security` | 栈 | — |
| 性能整轮 | `./scripts/test-round.sh perf`（或逐档 `perf/ops/round.sh <name> <tier>`） | 造数 + 快照 | ~40min 全协议 |

完整起栈与性能复跑细节见 `docs/development/compose-demo.md` 与 `perf/README.md`；
每档取证必须在下一档 `restore.sh` 之前完成（`down -v` 会销毁慢日志与 GC 日志）。

## 9. 已产出的执行记录（索引）

结果摘要与结论见各执行记录与 `docs/test/report.md`（#118）；本方案不复制执行结果。

| 类别 | 基线 / 正式轮 | 修复 / 调优轮 |
|---|---|---|
| 功能 | `execution/2026-10-29-functional-stack-formal.md`（开发期自测见 `2026-10-29-functional-stack-devrun.md`） | — |
| 接口 | `execution/2026-10-29-api.md` | `execution/2026-10-30-api-after-fix.md` |
| 性能 | `execution/2026-10-30-perf-baseline.md` | `execution/2026-10-30-perf-fix-147.md` |
| 安全 | `execution/2026-10-29-security-baseline.md`（56 条为当时口径，HDR 5 条修复后补入，现 61 条） | `evidence/2026-10-29-security-fix-*`（逐缺陷分项复跑）；61 条整轮 + ZAP 复跑见 `execution/2026-10-30-clean-rerun.md` |
| 兼容性 | `execution/2026-10-29-compatibility.md` | — |
| 干净环境复跑 | `execution/2026-10-30-clean-rerun.md`（五类按序 + 安全整轮 after-fix） | — |
| 底座 | README「测试与覆盖率」 | — |

## 10. 已知局限与风险

- **性能数字只在本机有效**：压测机与被测栈同机，CPU 饱和时双方一起被节流；基线三轮吞吐波动可达 24%，
  结论以中位数与相对改善为准。深挖新瓶颈（连接池修复后平台 ~380 req/s）不在本 effort 范围。
- **安全只做被动扫描 + 定向用例**：ZAP 不主动探测注入点，不替代渗透测试；授权范围仅限本机 Compose。
  页面级授予允许持有者给自己或角色加任意配置页（ADR 0014）是设计取舍，记为 accepted risk；
  附件越权返回 205、审批详情返回 403 的编码不一致是观察项，不登记缺陷。
- **兼容性无真机**：WebKit 是 Safari 的近似，不等于 iOS Safari 实机；不测 Edge（Chromium 同内核）
  与国产浏览器；不承诺平板宽度。模板图标依赖外部 CDN（`gw.alicdn.com`），受限网络下只显示文字选项。
- **接口套件依赖干净演示栈**：压测造数卷会被 `global-setup.ts` 拒绝；`sysMenu/doAssign` 的
  「旧非配置 id 静默跳过」需要历史遗留数据，干净卷未覆盖。
- **功能套件有可预期残留**：每轮留下 1 个已发布模板（ADR 0005 冻结不可删）；P1 失败只记缺陷不阻塞基线。
- **测试语料口径**：服务端 333 条是执行口径，源码口径与之相同；38 条从不编译/执行的 Activiti 7
  demo 已按 ADR 0030 删除，pom 里对应的两条排除一并移除。
- **覆盖率只报告不卡红**：不设阈值，不影响构建结果。
- **安全整轮 after-fix 已完成**：61 条定向 61/61、ZAP 0 High、存储型 XSS 3/3，
  见 [`execution/2026-10-30-clean-rerun.md`](execution/2026-10-30-clean-rerun.md)（#119）。
- **性能回归下限**：正式口径仍是 259 req/s（#124）；#147 建议的 326 req/s 在 2026-10-30 午间窗口
  未复现（中位 319），跨窗口不可直接卡线，见 clean-rerun §6。

## 11. 后续评估（不属本 effort）

- **模板图标等前端静态资源是否本地化**（取决于生产加固是否覆盖离线/内网部署）。

常态化与死测试已定：[ADR 0030](../adr/0030-测试常态化按主干回归与周安全跑可复现子集.md)——
PR 维持轻量，push main 跑栈回归，每周跑安全，性能留在本地；routine 证据只进 Actions artifact；
38 条从不编译/执行的 `@Test` 删除。
