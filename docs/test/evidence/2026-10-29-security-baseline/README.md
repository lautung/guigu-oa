# 安全证据：2026-10-29 基线轮

对应执行记录：`docs/test/execution/2026-10-29-security-baseline.md`。
被测版本：`5a36d3eb475ecf0fd610e3fac7703bcbb9512a31`。
轮次：**baseline（修复前）**；after-fix 轮由 #127 修复后在同目录规则下新建。

## 文件

| 文件 | 内容 | 生成命令 |
|---|---|---|
| `environment.md` | commit、入口、容器状态、runId | `node scripts/security-scan.mjs --out docs/test/evidence/2026-10-29-security-baseline` |
| `security-results.json` | 56 条定向用例的期望/实际/耗时/证据（重建，见下） | 同上 |
| `security-summary.md` | 逐用例结果表、失败项与证据摘要（重建，见下） | 同上 |
| `security-console.txt` | 基线轮的控制台输出原件（56 条 PASS/FAIL 与实际） | 运行上述命令时的终端输出 |
| `zap-summary.md` | ZAP 2.17.0 Core 被动 baseline 告警明细 | `node scripts/zap-baseline.mjs --out docs/test/evidence/2026-10-29-security-baseline` |
| `zap-alerts.json` | 脱敏后的告警结构化数据 | 同上 |
| `xss-report.json` | Playwright JSON 报告（存储型 XSS） | `cd client/guigu-oa-client && npm run test:security` |
| `xss-executed.png` | XSS 执行时的失败截图 | 同上 |

### 重建说明（2026-10-29）

`#139` 修复轮用 `node scripts/security-scan.mjs --only SEC-HDR` 复跑时，脚本没有指定 `--out`
之外的保护，把 `environment.md`、`security-results.json`、`security-summary.md` 覆盖成了 5 条
HDR 用例的结果。三份文件已按基线轮的原件与控制台输出重建：

- `environment.md` 与 `security-summary.md` 的表格、合计与失败项逐行还原；重建后的前 51 行与
  原件逐字节一致，后 5 行（LEAK-09、PAGE-01/02/03、XSS-01）由控制台输出还原。
- `security-results.json` 的 `id`/`family`/`severity`/`title`/`expected` 取自基线轮的
  `security-scan.mjs`，`actual`/`pass`/`skipped` 取自 `security-console.txt`；
  `durationMs` 与未写进 `actual` 的 `evidence` 无法恢复，分别记为 `null` 与省略。
- 之后复跑一律带 `--out <新目录>`；两个脚本现在都要求显式 `--out`，目录里已有结果时会拒绝
  覆盖，确需重写同一轮要加 `--force`。`security-console.txt` 是本次事故后唯一原样保留的
  机器可读记录，不再覆盖。

## 不入仓的大件

- 完整 ZAP HTML/JSON：本机 `/tmp/security-evidence-full/2026-10-29-security-baseline/zap-full/zap-report.html`。
- Playwright trace/视频：`client/guigu-oa-client/test-results/`（失败时生成，已被 `.gitignore` 覆盖）。

## 复跑

```bash
node scripts/security-scan.mjs --out <轮次目录>
node scripts/zap-baseline.mjs --out <轮次目录>   # 需 ZAP_HOME 指向解压后的 ZAP 2.17.0 Core
cd client/guigu-oa-client && npm run test:security
```

## 缺陷

S1 [#142](https://github.com/lautung/guigu-oa/issues/142)、S2 [#133](https://github.com/lautung/guigu-oa/issues/133)；
S3 [#134](https://github.com/lautung/guigu-oa/issues/134) [#135](https://github.com/lautung/guigu-oa/issues/135)
[#136](https://github.com/lautung/guigu-oa/issues/136) [#137](https://github.com/lautung/guigu-oa/issues/137)
[#138](https://github.com/lautung/guigu-oa/issues/138) [#139](https://github.com/lautung/guigu-oa/issues/139)；
S4 [#140](https://github.com/lautung/guigu-oa/issues/140) [#141](https://github.com/lautung/guigu-oa/issues/141)。
