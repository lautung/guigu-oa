# 安全测试：基线执行与留证（2026-10-29）

来源：[执行安全测试并留证 #116](https://github.com/lautung/guigu-oa/issues/116)。
方案与判定口径：[确定安全测试范围、深度与授权边界 #113](https://github.com/lautung/guigu-oa/issues/113)、
`docs/test/plan/security-cases.md`。
记录规范：[确定测试数据、缺陷记录与报告规范 #122](https://github.com/lautung/guigu-oa/issues/122)。

**性质：基线轮（修复前）。** 前置 #111/#113/#128/#129 已关闭；本轮只执行与留证，缺陷修复由
[修复测试发现的全部缺陷并回归 #127](https://github.com/lautung/guigu-oa/issues/127) 承担，
修复后用同一脚本、同一快照复跑，记为 after-fix 轮。

## 执行环境

| 项 | 值 |
|---|---|
| 日期 | 2026-10-29（UTC 03:47 起） |
| 被测版本 | 仓库 `5a36d3eb475ecf0fd610e3fac7703bcbb9512a31`（`main`） |
| 环境 | 本机 Docker Compose 演示栈，5 容器 healthy |
| 栈版本 | mysql 8.4 / redis 7.4 / minio bitnami 2025.7.23 / service-oa（JDK 21、Spring Boot 4.1.0、Flowable 8.0.0）/ client（nginx 1.31.8 生产构建） |
| 起栈 | `MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait` |
| 入口 | `http://localhost:9530`（API 经 `/dev-api`），匿名面 `http://localhost:8800` |
| 扫描器 | ZAP 2.17.0 Core zip（GitHub Releases，JDK 21 headless，本机 `/tmp/zap/ZAP_2.17.0`） |
| 运行环境 | Node v24.18.1（客户端 Playwright 用本机 Node；Compose 构建仍用 node 22 镜像） |

## 快照与恢复

执行前对当前卷做了快照（#112 协议）：

| 对象 | 文件 | 大小 |
|---|---|---|
| MySQL（含 `ACT_*`/`FLW_*`） | `/tmp/security-snapshot/guigu-oa.sql` | 311 KB |
| `process-definitions` 卷 | `/tmp/security-snapshot/process-definitions.tgz` | 1.3 KB |
| `minio-data` 卷 | `/tmp/security-snapshot/minio-data.tgz` | 8.3 KB |

执行后按 `down --volumes` → 起 `mysql`/`minio` → 导入 SQL 与两卷 → `up -d --build --wait` 恢复；
恢复后 `./scripts/compose-baseline-check.sh` 结果为 20 通过、0 失败。恢复步骤与观察见文末。

## 环境验收（执行前）

```
./scripts/compose-baseline-check.sh   → 结果：20 通过，0 失败
```

## 定向用例结果

命令：

```bash
node scripts/security-scan.mjs --out docs/test/evidence/2026-10-29-security-baseline
```

结果：**56 条用例，41 通过、15 失败、0 跳过**（约 6 分钟，含 5 分钟上传凭证过期等待与 XSS 浏览器用例）。

| 族 | 用例数 | 通过 | 失败 |
|---|---|---|---|
| SEC-JWT | 10 | 9 | 1（SEC-JWT-07） |
| SEC-IDOR | 9 | 9 | 0 |
| SEC-MEM | 5 | 5 | 0 |
| SEC-GATE | 3 | 3 | 0 |
| SEC-UPLOAD | 9 | 9 | 0 |
| SEC-CSRF | 5 | 1 | 4（SEC-CSRF-02/03/04/05） |
| SEC-BRUTE | 2 | 1 | 1（SEC-BRUTE-02） |
| SEC-LEAK | 9 | 3 | 6（SEC-LEAK-01/02/03/04/06/09） |
| SEC-PAGE | 3 | 1 | 2（SEC-PAGE-01/02） |
| SEC-XSS | 1 | 0 | 1（SEC-XSS-01） |

通过的关键结论：

- JWT 篡改、`alg=none`、异密钥、过期、access↔refresh 互换全部被拒（208）；停用账号存量 token 被拒（209）。
- 跨用户审批读写、附件列表/下载凭证/删除/完成上传、草稿隔离、未发布模板读取全部被拒。
- 成员资格矩阵符合 ADR 0011：申请人可读可补传不可处理，当前处理人可读可上传可处理，抄送人只读，历史处理人审批后只读。
- 35/35 配置端点对无配置权限账号返回 205；伪造 gate 参数/头无效；admin 6 个只读端点正常。
- 上传：50 MiB 上限、Content-Type/大小不一致、过期凭证、非 zip 后缀、路径穿越文件名全部被拦；处理人可补传、抄送人/非成员不能。
- 登录失败不区分用户是否存在；日志无密码/完整 token；异常响应无堆栈；其他 actuator 端点不可匿名读。

## 失败项与缺陷入账

| 用例 | 级别 | 结论 | 缺陷 issue |
|---|---|---|---|
| SEC-XSS-01 | S1 | 列表与详情 `v-html` 执行存储型 XSS，可读 Cookie 中的 token | [#142](https://github.com/lautung/guigu-oa/issues/142) |
| SEC-LEAK-01/02 | S2 | 用户分页与详情返回 BCrypt 密码哈希 | [#133](https://github.com/lautung/guigu-oa/issues/133) |
| SEC-JWT-07 | S3 | 登出不作废 access token，有效期内可重放 | [#134](https://github.com/lautung/guigu-oa/issues/134) |
| SEC-BRUTE-02 | S3 | 30 次失败后正确密码仍可登录，无限流/锁定 | [#135](https://github.com/lautung/guigu-oa/issues/135) |
| SEC-LEAK-06 | S3 | 抄送候选人接口向普通用户返回全部启用用户 | [#136](https://github.com/lautung/guigu-oa/issues/136) |
| SEC-PAGE-01/02 | S3 | 分页 `limit` 无上限 | [#137](https://github.com/lautung/guigu-oa/issues/137) |
| SEC-CSRF-04/05 | S3 | `GET updateStatus` 与 `GET publish` 是写操作 | [#138](https://github.com/lautung/guigu-oa/issues/138) |
| ZAP Medium/Low | S3 | 客户端缺 CSP、X-Frame-Options、X-Content-Type-Options | [#139](https://github.com/lautung/guigu-oa/issues/139) |
| SEC-CSRF-02/03 | S4 | 审批接口 CORS 对任意 Origin 放行并允许 token 头 | [#141](https://github.com/lautung/guigu-oa/issues/141) |
| SEC-LEAK-03/04/09 | S4 | OpenAPI/Swagger 匿名可读，nginx 版本泄露 | [#140](https://github.com/lautung/guigu-oa/issues/140) |

合计 10 个缺陷：S1×1、S2×1、S3×6、S4×2，全部 `bug` + `severity:Sx`，状态 open，由 #127 修复。

## ZAP 2.17.0 Core 被动 baseline

命令：

```bash
node scripts/zap-baseline.mjs --out docs/test/evidence/2026-10-29-security-baseline
```

- 目标：9530 的 `/`、`/index.html`、`/login`、`/work/pending`、404 页、`/dev-api/actuator/health`、`/dev-api` 404，以及 8800 的 `/actuator/health`、`/actuator/health/readiness`、`/actuator/health/../env`、`/v3/api-docs`、`/swagger-ui/index.html`、`/swagger-ui.html`、`/admin/system/index/login`，另加一条登录失败 POST（`sendRequest`）。
- 模式：**被动 only**；未启动 spider、未导入 OpenAPI、未做主动扫描。
- 结果：29 条告警记录（0 High / 11 Medium / 12 Low / 6 Informational），去重后 6 类：

| 告警 | 风险 | 映射 |
|---|---|---|
| Missing Anti-clickjacking Header | Medium | #139 |
| Content Security Policy (CSP) Header Not Set | Medium | #139 |
| X-Content-Type-Options Header Missing | Low | #139 |
| Server Leaks Version Information | Low | #140 |
| Modern Web Application | Informational | 观察项 |
| CSP（Swagger UI 页面） | Medium | #139 的同类硬化；Swagger 暴露本身归 #140 |

完整 HTML/JSON 报告体积大，未入仓：本机 `/tmp/security-evidence-full/2026-10-29-security-baseline/zap-full/zap-report.html`。

## 存储型 XSS（Playwright）

- 用例：`client/guigu-oa-client/tests/security/security-xss.spec.ts`，命令 `npm run test:security`。
- 造数：`alice` 发起审批，`formShowData.请假原因 = <img src=x onerror="window.__secXss=1">`。
- 结果：**失败（符合预期，证明漏洞）**。`bob` 在待处理列表页与详情页都读到 `window.__secXss=1`。
- 证据：`xss-report.json`、`xss-executed.png`；trace 在 `client/guigu-oa-client/test-results/`（不入仓）。
- 修复后同一 spec 应通过（脚本不执行），作为 #142 的回归证据。

## 判定

- 定向用例全部执行，无阻塞、无跳过；ZAP 被动报告已归档；15 条失败全部入账为 10 个缺陷。
- 结论：**安全基线执行完成**（不是「安全基线通过」）。当前版本存在 1 个 S1 与 1 个 S2，阻断发布；
  由 #127 修复后按 after-fix 轮复跑，S1/S2 必须消失、定向用例全绿、ZAP Medium/Low 安全头告警消失，才算安全回归通过。

## 证据

目录：`docs/test/evidence/2026-10-29-security-baseline/`

| 文件 | 内容 | 生成命令 |
|---|---|---|
| `security-results.json` | 56 条用例的期望/实际/耗时/证据 | `node scripts/security-scan.mjs --out …` |
| `security-summary.md` | 逐用例结果表与失败项 | 同上 |
| `environment.md` | commit、入口、容器状态、runId | 同上 |
| `zap-summary.md` | ZAP 告警分布与明细 | `node scripts/zap-baseline.mjs --out …` |
| `zap-alerts.json` | 脱敏后的告警结构化数据 | 同上 |
| `xss-report.json` | Playwright JSON 报告（覆盖 XSS 用例） | `cd client/guigu-oa-client && npm run test:security` |
| `xss-executed.png` | 失败时的页面截图 | 同上 |

## 复跑方式

```bash
# 1. 恢复快照或全新卷起栈
docker compose down --volumes --remove-orphans
# 恢复 /tmp/security-snapshot（或直接全新卷）
MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public docker compose up -d --build --wait
./scripts/compose-baseline-check.sh

# 2. 定向矩阵 + 浏览器 XSS
node scripts/security-scan.mjs --out docs/test/evidence/<轮次>
cd client/guigu-oa-client && npm run test:security && cd ../..

# 3. ZAP 被动 baseline
node scripts/zap-baseline.mjs --out docs/test/evidence/<轮次>
```

修复后复跑见 `docs/test/evidence/2026-10-29-security-fix-*`（逐缺陷分项）与
`docs/test/evidence/2026-10-30-clean-rerun/security/`（61 条整轮 + ZAP），并与本轮逐项对比。

## 观察与残余风险

- **拒绝码不一致**：附件类越权统一返回 205（`没有操作权限`），审批详情用 403；断言接受两者，不登记缺陷，但建议 #127 顺手统一。
- **页面级授予是设计取舍**：`sysUser`/`sysRole` 持有者可以给自己或角色加任意配置页（ADR 0014），记为 accepted risk，不登记缺陷。
- **被动扫描的边界**：ZAP 未做主动扫描，不覆盖需要主动探测的注入点；定向用例覆盖已知风险面，不替代渗透测试。
- **CORS 影响有限**：鉴权用请求头 `token` 而非 Cookie，攻击页面拿不到 token 就无法直接利用；仍按 #113 的 S4 入账。
- **MinIO 快照恢复**：首次恢复时 MinIO 容器处于运行中，卷被替换后报 `no online disks found`；重启 `minio` 并重跑 `minio-init` 后恢复（bucket 重建成功）。后续恢复应先停 MinIO 再替换卷，已写入复跑注意。
- 本轮未执行真实 DoS、主动扫描、分布式撞库，也未触碰 3306/6379 与 MinIO 控制台。
