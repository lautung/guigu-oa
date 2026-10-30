# 云尚办公生产加固测试报告

- 来源：[汇总五类结果并出测试报告 #118](https://github.com/lautung/guigu-oa/issues/118)；地图：[#107](https://github.com/lautung/guigu-oa/issues/107)。
- 测试方案：[把测试决策汇总成测试方案 #126](https://github.com/lautung/guigu-oa/issues/126) / [`plan.md`](plan.md)（本报告只给结论，测什么、怎么判见方案）。
- 记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。
- 日期：2026-10-30。
- 被测基线：应用代码 `f0a3469`（最后一次修复提交），测试资产与文档 `c245183`（本报告之前）。
- 一句话结论：**五类测试全部执行完毕，17 个缺陷全部修复并回归（closed = verified），S1/S2 清零，生产加固目标达成；干净环境五类复跑与安全整轮 after-fix（61/61）已由 [#119](https://github.com/lautung/guigu-oa/issues/119) 验证通过。**

## 1. 摘要与结论

本 effort 以**生产加固**为目的（#108），在统一客户端 + `service-oa` + Flowable 8 的 Compose 演示栈上执行了功能、接口、性能、安全、兼容性五类测试。安全与性能为主、功能与接口为回归基线、兼容性做最小矩阵；所有发现的问题均登记为 GitHub issue 并修复、回归、关闭。

| 类别 | 规模 | 正式结果 | 发现缺陷 |
|---|---|---|---|
| 功能（真实栈） | 13 场景 / 26 次运行 | 26/26 通过，54.1 s，P0 8/8 | S1×1、S3×1 |
| 接口（黑盒） | 56 端点 / 66 用例 | 基线 65/65 ×2；after-fix 66/66 ×2 | S3×2、S4×2 |
| 性能（k6） | 7 档（冒烟/基线×3/拐点/压力/附件） | 全阈值达标；调优后中位 288 → 362.6 req/s（+25.9%） | S3×1（连接池） |
| 安全（定向 + ZAP + XSS） | 61 条定向 + 被动扫描 + 浏览器 XSS | 基线 56 条 41 过 / 15 败并全部入账；整轮 after-fix 61/61，ZAP 0 High / 5 Medium（接受项）/ 0 Low | S1×1、S2×1、S3×6、S4×2 |
| 兼容性（Chromium/WebKit × 桌面/手机 + 1440） | 5 格 / 189 次运行 | 145 过 / 44 按视口设计性跳过 / 0 失败，50.8 s | 0 |
| 干净环境复跑（#119） | 五类按序 + 一键编排 | 66/66 ×2、27/27、7 档全过（中位 319 req/s）、61/61、145/44/0；总耗时 69m55s | 0 |
| 回归底座 | 客户端 496 单测、服务端 333、mock e2e 189 次运行 | 全绿；覆盖率 91.3/83.6 与 93.4/81.8（只报告） | — |

**结论**

1. **缺陷账本清空**：17 个缺陷（S1×2、S2×1、S3×10、S4×4）全部 `closed = verified`，每个关闭评论含修复提交 SHA、回归用例与复跑证据。
2. **发布门槛**（方案 §5.3）：无未关闭 S1/S2 ✅；缺陷 17/17 已验证 ✅；功能、接口、性能、兼容性基线轮与修复/调优轮记录齐全 ✅；安全整轮 after-fix 61 条 + ZAP 已由 #119 复跑通过 ✅；残余风险本文 §8 ✅。
3. **性能瓶颈已消除**：HikariCP 默认 10 连接是吞吐在 ~300 req/s 饱和的根因（#147）；调优到 25 后基线中位 362.6 req/s，压力档与附件档无回退，新平台 ~380–386 req/s。
4. **安全风险已收敛**：2 个 S1（存储型 XSS、办事主链路被权限门切断）、1 个 S2（密码哈希外泄）与 10 个 S3/S4 全部修复；ZAP 基线 0 High，修复后剩余 5 条 `style-src 'unsafe-inline'`（ADR 0026 接受项）与 11 条 Informational。
5. **可复跑资产已入仓**：接口/功能/安全/性能/兼容性套件、脚本与证据链均在仓库内，命令见 §9。

## 2. 范围与环境

范围、平台与授权边界见方案 [`plan.md`](plan.md) §1–§2。要点：

- **被测栈**：Docker Compose 演示栈——mysql 8.4 / redis 7.4 / minio（bitnami 2025.7.23）/ `service-oa`（JDK 21、Spring Boot 4.1.0、Flowable 8.0.0）/ `client`（nginx 1.31.8 生产构建）。
- **入口**：客户端 `http://localhost:9530`，API 经 nginx `/dev-api` 代理到 `8800`；后端直连 `http://localhost:8800`。
- **账号**：`admin`（配置全授）、`user`（无配置授予）、`alice`（发起）、`bob`（处理）、`carol`（抄送）。
- **数据**：种子数据 + API 造数 + 全库/卷快照恢复；功能/接口/兼容性用干净卷，性能/安全用快照（#112 协议）。
- **授权边界**：只打本机 Compose，不做公网/生产/他人系统测试；不做 DoS、主动扫描、分布式撞库。
- **环境差异**：性能数字只与同机同快照对比；兼容性 WebKit 是 Safari 近似，无真机。

## 3. 方法与执行

分层与工具见方案 §3，执行记录一文件一轮：

| 类别 | 执行记录 | 证据目录 |
|---|---|---|
| 功能（开发期自测 + 正式轮） | [`execution/2026-10-29-functional-stack-devrun.md`](execution/2026-10-29-functional-stack-devrun.md) / [`formal`](execution/2026-10-29-functional-stack-formal.md) | `evidence/2026-10-29-functional-stack-*/` |
| 接口（基线 + after-fix） | [`execution/2026-10-29-api.md`](execution/2026-10-29-api.md) / [`2026-10-30-api-after-fix.md`](execution/2026-10-30-api-after-fix.md) | `evidence/2026-10-29-api*/` |
| 性能（基线 + 调优） | [`execution/2026-10-30-perf-baseline.md`](execution/2026-10-30-perf-baseline.md) / [`perf-fix-147`](execution/2026-10-30-perf-fix-147.md) | `evidence/2026-10-30-perf-*/` |
| 安全（基线 + 逐缺陷复跑） | [`execution/2026-10-29-security-baseline.md`](execution/2026-10-29-security-baseline.md) | `evidence/2026-10-29-security-baseline/`、`evidence/2026-10-29-security-fix-*/` |
| 兼容性 | [`execution/2026-10-29-compatibility.md`](execution/2026-10-29-compatibility.md) | `evidence/2026-10-29-compatibility/` |

**方法要点**：黑盒接口套件用 Playwright APIRequest 打真实栈（`body.code` 为权威契约）；真实栈功能套件只覆盖系统级流程与持久化结果；性能用 k6 加权混合模型（读 70 / 写 25 / 登录 5）；安全用定向矩阵 + ZAP 被动 + 浏览器 XSS；兼容性复用 mock e2e 全量并补真实栈 smoke。用例设计以状态机派生为主，不重写既有测试体系（#108）。

## 4. 五类结果

### 4.1 功能

- **正式轮**：全新卷一条命令起栈（#128），`npm run test:e2e:stack` → **26/26 通过、0 失败、0 跳过**，54.1 s；P0 8/8、P1 5/5（详情见 [`formal`](execution/2026-10-29-functional-stack-formal.md)）。
- **开发期**发现 S1 #131（无配置权限用户无法打开发起页）与 S3 #132（`sysRole/findAll` 永远 20001）；修复后复跑 26/26。
- 判定：P0 全过且无未关闭 S1/S2，**功能回归基线通过**。每轮唯一残留是 1 个已发布模板（ADR 0005 冻结）。

### 4.2 接口

- **基线**：56/56 业务端点、65 用例，干净栈两轮 **65/65**（14.3 s / 13.2 s），0 跳过、0 重试；权限四类矩阵（匿名 / 仅 token / 配置 gate / 成员归属）全部落地。
- **after-fix**：同脚本同干净栈两轮 **66/66**（14.4 s / 13.2 s）；`f-http-semantics` 组按 #145 新契约从 6 例改写为 7 例。
- **契约变化**：认证失败统一 HTTP 401 + `body.code` 208/209；容器级错误真实 404/405 + 业务信封；业务失败仍是 HTTP 200 + `body.code`。客户端错误拦截器已按信封路由。
- 遗留：`sysMenu/doAssign`「旧非配置 id 静默跳过」需要 legacy 数据，干净卷未覆盖。

### 4.3 性能

- **基线轮**：288 req/s 中位（280.3–346.5），读 p95 中位最高 318 ms（最差 322 ms），错误率 ≤0.023%；100 VU 压力档 302.3 req/s，**拐点 ≤20 VU**；附件整链 p95 137 ms；无 OOM/重启、零慢 SQL。
- **瓶颈**：#147 HikariCP 默认 10 连接导致 ~300 req/s 饱和；四组证据（CPU 余量、零慢 SQL、定量吻合、延迟线性增长）指向连接等待。
- **调优轮**：`maximum-pool-size: 25` 后基线中位 **362.6 req/s（+25.9%）**，50 VU 384.3（+9.3%），100 VU 378.2（+25.1%），附件 360.6（−0.2%）；错误率不上升，无场景回退；判定有效（#112 §7）。
- 回归下限 259 req/s 为正式口径（方案 §5.2 / `perf-load-model` §4）；326 的建议已按 clean-rerun §6 暂缓。

### 4.4 安全

- **定向矩阵**：基线 56 条 **41 过 / 15 败 / 0 跳过**，失败项全部入账；套件后续扩到 61 条（新增 HDR 5 条）。
- **ZAP 2.17.0 被动 baseline**：29 条告警（0 High / 11 Medium / 12 Low / 6 Info），去重 6 类；修复后基线中的 CSP not set、X-Frame-Options、X-Content-Type-Options 三类安全头告警与 nginx 版本泄露均清零，剩余 5 条 `style-src 'unsafe-inline'`（ADR 0026 接受项）与 11 条 Informational。
- **存储型 XSS**：`tests/security/` 复现 `v-html` 执行（S1 #142），修复后同一 spec 通过且载荷按文本显示。
- **after-fix**：10 个缺陷各有独立复跑证据目录（`evidence/2026-10-29-security-fix-133` … `-142`）；#119 完成**整轮 61/61**（0 跳过）+ ZAP + 存储型 XSS 3/3，证据 `evidence/2026-10-30-clean-rerun/security/`。
- 判定：安全基线执行完成；S1/S2 已清零，整轮 after-fix 通过。

### 4.5 兼容性

- **矩阵**：Chromium + WebKit × 桌面 1280 / 手机 390 全量 + 1440×Chromium 关键页扫描，**145 过 / 44 跳过 / 0 失败，189 次运行 50.8 s**；所有跳过均为既定视口设计（配置页在手机不可占用）。
- **产品规则**：手机可完成办事主链路 ✅；配置页在手机/未授予时落回待处理 ✅；已打开配置页后变手机强制离开 ✅。
- **真实栈补充**：27/27 通过（含 WebKit 手机真实登录→待处理→详情→通过），1.2 min。
- 本轮 0 新缺陷；修正了 mock 上传源以匹配 CSP、过滤 WebKit ResizeObserver 噪声（均非产品缺陷）。

### 4.6 回归底座

- 客户端 `npm run verify`：496 单测 + lint/typecheck/format/文档路径检查全绿；服务端 `mvn -pl service-oa -am test`：333 测试全绿。
- mock e2e 全矩阵 189 次运行（145 过 / 44 跳过）；CI 两个 job 全绿（run 40809257644、40883477538）。
- 覆盖率客户端 91.3/83.6、服务端 93.4/81.8，只报告不卡红。

## 5. 缺陷、瓶颈与漏洞汇总

一缺陷一 GitHub issue（`bug` + `severity:Sx`），明细在 issue；本表只做汇总（按级别与类型排序）。

| Issue | 级别 | 类型 | 标题 | 修复提交 | 回归证据 |
|---|---|---|---|---|---|
| [#131](https://github.com/lautung/guigu-oa/issues/131) | S1 | 功能 | 发起页读配置侧模板端点，无配置权限用户无法发起 | `88c4e10` | 功能套件 26/26 + devrun |
| [#142](https://github.com/lautung/guigu-oa/issues/142) | S1 | 安全 | 审批表单存储型 XSS 在列表与详情执行 | `aabb4ad` | `security-fix-142` |
| [#133](https://github.com/lautung/guigu-oa/issues/133) | S2 | 安全 | 用户分页与详情返回 BCrypt 密码哈希 | `ee27a7d` | `security-fix-133` |
| [#132](https://github.com/lautung/guigu-oa/issues/132) | S3 | 功能 | `sysRole/findAll` 内置演示异常永远 20001 | `c5147c1` | 功能套件 26/26 |
| [#134](https://github.com/lautung/guigu-oa/issues/134) | S3 | 安全 | 登出后 access token 有效期内仍可用 | `6349d80` | `security-fix-134` |
| [#135](https://github.com/lautung/guigu-oa/issues/135) | S3 | 安全 | 登录无失败次数限制或锁定 | `6144f7e` | `security-fix-135` |
| [#136](https://github.com/lautung/guigu-oa/issues/136) | S3 | 安全 | 抄送候选人接口暴露全部启用用户 | `adb9c92` | `security-fix-136` |
| [#137](https://github.com/lautung/guigu-oa/issues/137) | S3 | 安全 | 分页 `limit` 无上限 | `61f91a5` | `security-fix-137` |
| [#138](https://github.com/lautung/guigu-oa/issues/138) | S3 | 安全 | GET 方法执行写操作（updateStatus / publish） | `09799e9` | `security-fix-138` |
| [#139](https://github.com/lautung/guigu-oa/issues/139) | S3 | 安全 | 统一客户端缺少 CSP、X-Frame-Options、X-Content-Type-Options | `0e9fef5` | `security-fix-139` |
| [#143](https://github.com/lautung/guigu-oa/issues/143) | S3 | 接口 | 修改密码后旧 access token 仍可用 | `52736cc` | 接口 after-fix（B5） |
| [#146](https://github.com/lautung/guigu-oa/issues/146) | S3 | 接口 | 删除角色后成员的配置授予不回收 | `9cc0918` | 接口 after-fix（b-grant） |
| [#147](https://github.com/lautung/guigu-oa/issues/147) | S3 | 性能 | HikariCP 默认连接池使吞吐在 ~300 req/s 饱和 | `f0a3469` | `perf-fix-147` |
| [#140](https://github.com/lautung/guigu-oa/issues/140) | S4 | 安全 | 匿名面暴露 Swagger/OpenAPI 与 nginx 版本号 | `80edbc2` | `security-fix-140` |
| [#141](https://github.com/lautung/guigu-oa/issues/141) | S4 | 安全 | 审批接口 CORS 对任意 Origin 放行并允许 token 头 | `cddd90d` | `security-fix-141` |
| [#144](https://github.com/lautung/guigu-oa/issues/144) | S4 | 接口 | `sysRole/doAssign` 接受不存在的 roleId 并写入关系表 | `4b380e7` | 接口 after-fix（B8） |
| [#145](https://github.com/lautung/guigu-oa/issues/145) | S4 | 接口 | 接口错误一律 HTTP 200，404/405 被吞成业务 201 | `70cb454` | 接口 after-fix（F 组） |

- 合计 **17** 个：S1×2、S2×1、S3×10、S4×4；按类型：功能 2、接口 4、性能 1、安全 10、兼容性 0。
- 状态：**17/17 CLOSED = verified**；无未关闭缺陷。
- 瓶颈 1 个（#147），漏洞 10 个（#133–#142），功能/接口缺陷 6 个（#131/#132/#143–#146）。

## 6. 修复与回归

修复批次与顺序由 [修复测试发现的全部缺陷并回归 #127](https://github.com/lautung/guigu-oa/issues/127) 统一跟踪；本报告不复述修复实现，只给回归结论。

- **逐缺陷回归**：每个 issue 的关闭评论包含修复提交 SHA、回归用例路径、复跑证据目录；安全 10 个缺陷各有 `evidence/2026-10-29-security-fix-*/`。
- **整轮复跑**：接口 after-fix 66/66 ×2（干净栈）；性能调优整轮同快照同脚本，基线中位 +25.9%、无场景回退；功能修复后真实栈 26/26。
- **红绿验证**：接口的「已知缺陷断言」在修复后翻红再改写为新契约（#145/#146/#143/#144）；安全用例（SEC-JWT/LEAK/CSRF/PAGE/XSS）逐项由失败转通过；性能以同快照前后对比判定，不以绝对数字。
- **契约与设计记录**：相关决策已写入 ADR（0022–0029 等）与 GLOSSARY（登录失败限制、退出、表单文本等），报告不重复。

回归后的发布判定：**不存在未关闭的 S1/S2，缺陷全部 verified；代码层面满足发布门槛**。安全 61 条整轮 + ZAP 合并复跑已由 #119 补齐（61/61）。

## 7. 交付完整性对照（地图目的地）

地图目的地要求：测试方案、执行记录、缺陷清单、修复回归齐全，且留下可复跑资产与证据链。

| 交付物 | 位置 | 状态 |
|---|---|---|
| 测试方案与用例清单 | [`plan.md`](plan.md) + [`plan/`](plan/)（功能/接口/性能/安全/兼容性 5 份） | ✅ #126 |
| 执行记录 | [`execution/`](execution/) 8 份（基线/开发期/after-fix/调优） | ✅ 五类齐全 |
| 缺陷清单 | GitHub issues #131–#147（`bug` + `severity:Sx`） | ✅ 17/17 verified |
| 修复与回归记录 | #127；接口/性能 after-fix 记录；安全逐缺陷证据 + 整轮 61/61（#119） | ✅ |
| 可复跑测试资产 | `tests/api/`、`tests/e2e-stack/`、`tests/security/`、`perf/`、`scripts/` | ✅ 入仓 |
| 证据链 | [`evidence/`](evidence/)（3.1 MB，单文件 ≤100 KB、目录 ≤5 MB） | ✅ 符合 #122 |
| 干净环境一键复跑验证 | [#119](https://github.com/lautung/guigu-oa/issues/119) / `scripts/test-round.sh` | ✅ 69m55s，见 [`execution/2026-10-30-clean-rerun.md`](execution/2026-10-30-clean-rerun.md) |

## 8. 残余风险与已知局限

1. **性能数字只在本机有效**：压测机与被测栈同机（10 vCPU / 8 GB Docker VM），绝对数字不可移植；对比只看同环境相对改善。新平台 ~380–386 req/s 的下一层瓶颈（如 `findProcessed` N+1/查询形状）未深挖，属于后续优化。
2. **安全只做被动扫描 + 定向用例**：ZAP 未主动探测注入点，不替代渗透测试；授权仅限本机 Compose。页面级授予允许持有者给自己或角色加任意配置页（ADR 0014）是设计取舍，记 accepted risk；附件越权 205、审批详情 403 的编码不一致是观察项，不登记缺陷。
3. **CSP 的接受项**：`style-src 'unsafe-inline'` 是 Element Plus/过渡/nprogress 的必要条件（ADR 0026），ZAP 报 5 条 Medium，属接受项；跨源部署需在网关按受信来源放行（ADR 0027），后端不再下发 CORS。
4. **兼容性无真机**：WebKit 是 Safari 近似，不等于 iOS Safari 实机；不测 Edge/国产浏览器，不承诺平板宽度；模板图标依赖外部 CDN（`gw.alicdn.com`），受限网络下只显示文字选项。
5. **接口套件依赖干净演示栈**：压测造数卷会被 `global-setup.ts` 拒绝；`sysMenu/doAssign` 的 legacy 静默跳过分支未覆盖。
6. **功能套件可预期残留**：每轮留下 1 个已发布模板（ADR 0005 冻结不可删）；P1 失败只记缺陷不阻塞基线。
7. **38 条从不执行的 `@Test`**（`**/auth/activiti/**` 30 条、`**/auth/TestMpDemo*` 8 条，Activiti 7 课程 demo）仍在源码里；#119 只完成复跑验证，删除或收编仍待地图后续决策。
8. **覆盖率只报告不卡红**：不设阈值，不影响构建结果。
9. **生产部署前置**：必须启用 `prod` profile 并注入 ≥64 字节 `JWT_SECRET`；附件直传源与 `CSP_CONNECT_SRC` 成对配置；OpenAPI/Swagger 在非 `dev` profile 下关闭（#140）。

## 9. 复跑入口与证据索引

### 9.1 复跑命令

| 目标 | 命令 | 前置 | 实测耗时 |
|---|---|---|---|
| 五类一键 | `./scripts/test-round.sh all`（或 `clean|api|functional|perf|security|compat`） | 见脚本头部前置 | 69m55s（本轮含脚本复跑验证） |
| 客户端静态检查 + 单测 | `cd client/guigu-oa-client && npm run verify` | `npm ci` | — |
| 服务端测试 | `cd server/guigu-oa-parent && mvn -pl service-oa -am test` | — | — |
| 全仓检查 | `./scripts/verify.sh` | — | — |
| 功能（真实栈） | `cd client/guigu-oa-client && npm run test:e2e:stack` | 干净栈 | 57 s |
| 接口（黑盒） | `DEMO_ORIGIN=http://localhost:9530 npm run test:api` | 干净栈 | 14–16 s/轮 |
| mock e2e 全矩阵 | `E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e` | 栈 + 生产构建 | 52 s |
| 安全定向 | `node scripts/security-scan.mjs --out docs/test/evidence/<轮次>` | 栈 + 快照 | 5m33s（含 5 分钟过期等待） |
| ZAP 被动 | `node scripts/zap-baseline.mjs --out docs/test/evidence/<轮次>` | 栈 + ZAP 2.17.0 Core | 31 s |
| 存储型 XSS | `cd client/guigu-oa-client && npm run test:security` | 栈 | 10 s |
| 性能整轮 | `./scripts/test-round.sh perf`（或逐档 `perf/ops/round.sh <name> <tier>`） | 造数 + 快照 | 45m22s |

起栈：`docker compose down --volumes --remove-orphans` 后
`MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait`，
再用 `./scripts/compose-baseline-check.sh`（26 项）与 `node scripts/compose-demo-smoke.mjs` 验收。
依赖与网络绕法见 `docs/development/compose-demo.md`，性能细节见 `perf/README.md`。

### 9.2 证据索引

| 目录 | 内容 | 对应记录 |
|---|---|---|
| `evidence/2026-10-29-functional-stack-devrun/` | 开发期自测报告（发现 #131/#132） | 功能 devrun |
| `evidence/2026-10-29-functional-stack-formal/` | 正式轮 Playwright JSON/JUnit | 功能正式轮 |
| `evidence/2026-10-29-api/` | 基线轮 65/65 报告 | 接口基线 |
| `evidence/2026-10-29-api-after-fix/` | after-fix 66/66 报告 | 接口回归 |
| `evidence/2026-10-29-security-baseline/` | 56 条定向结果、ZAP 摘要、XSS 截图 | 安全基线 |
| `evidence/2026-10-29-security-fix-{133..142}/` | 10 个缺陷的逐项复跑证据 | 安全 after-fix |
| `evidence/2026-10-30-perf-baseline/` | k6 汇总、stats、慢日志、行数 | 性能基线 |
| `evidence/2026-10-30-perf-fix-147/` | 调优整轮 k6 汇总与观测 | 性能回归 |
| `evidence/2026-10-29-compatibility/` | 五格 35 张关键页截图 + 控制台 JSON + JUnit | 兼容性 |
| `evidence/2026-10-30-clean-rerun/` | 干净环境五类复跑：接口/功能/性能/安全/兼容性 + 耗时表 | 干净环境复跑 |

入仓证据遵循 #122：单文件 ≤100 KB、`evidence/` 整目录 ≤5 MB、必须脱敏。大件不入仓：完整 ZAP HTML（`/tmp/security-evidence-full/…`）、Playwright JSON/trace（`client/guigu-oa-client/test-results/`）、k6 原始结果（`perf/results/`）——路径与重跑命令见各执行记录与目录 README。证据总索引见 [`evidence/README.md`](evidence/README.md)。

## 10. 后续工作

- **#119 已完成**：干净环境五类复跑 69m55s 全绿，安全整轮 61/61 已补齐，一键入口 `scripts/test-round.sh` 入仓；详见 [`execution/2026-10-30-clean-rerun.md`](execution/2026-10-30-clean-rerun.md)。
- **门限校准**：性能混合吞吐回归下限仍按 259 req/s 判定；#147 建议的 326 req/s 在午间窗口未复现（本轮中位 319），暂缓采用，或只在同窗口 3 轮中位数上校准。
- **38 条死 `@Test` 的去留**（删除还是收编）与 **是否接入 CI 门禁/定时任务**（地图 Not yet specified）仍待决策。
- **静态资源本地化**：模板图标外部 CDN 是否改本地，取决于是否支持离线/内网部署。
