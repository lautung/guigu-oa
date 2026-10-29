# 安全响应头人工复核（#139）

- 被测版本：`0e9fef51622c6fb269c29b3b9ecc8368f32b6b8b`（`fix: serve the client with CSP, X-Frame-Options and nosniff (#139)`）
- 栈：本机 Compose 演示栈（5 容器 healthy；client 由 `nginx.conf.template` + envsubst 生成配置）
- 入口：`http://localhost:9530`；`CSP_CONNECT_SRC=http://localhost:9000`（与 `S3_PUBLIC_ENDPOINT` 成对）
- ZAP：2.17.0 Core，同一被动 baseline（逐条 `accessUrl`/`sendRequest`，无 spider、无主动扫描）。
  本轮修了 harness 的静态资源探测（原先找 `/assets/`，Vite 产物在 `/static/`），目标从 14 个变为 28 个

## 命令

```bash
for p in / /login /work/pending /nonexistent-sec-hdr; do
  curl -sSI "http://localhost:9530$p" | grep -iE '^HTTP|content-security-policy|x-frame-options|x-content-type-options'
done
JS=$(curl -sS http://localhost:9530/ | grep -o '/static/index-[^"]*\.js' | head -1)
curl -sSI "http://localhost:9530$JS" | grep -iE '^HTTP|content-type|x-content-type-options'
```

## 结果

四个 HTML 目标（含 SPA 回退）都返回同一组头；构建产物只带 `nosniff`：

| URL | CSP | X-Frame-Options | X-Content-Type-Options |
|---|---|---|---|
| `/` | 有（见下） | `DENY` | `nosniff` |
| `/login` | 同上 | `DENY` | `nosniff` |
| `/work/pending` | 同上 | `DENY` | `nosniff` |
| `/nonexistent-sec-hdr`（回退到 index.html） | 同上 | `DENY` | `nosniff` |
| `/static/index-*.js` | - | - | `nosniff` |

```
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';
  img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:;
  connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self';
  form-action 'self'; frame-ancestors 'none'
```

`Server: nginx/1.31.8` 仍在（`server_tokens off` 归 #140）。

## 与基线轮的告警对比

两个文件都是 `node scripts/zap-baseline.mjs` 的输出。本轮修了静态资源探测（原先找不存在的
`/assets/`），目标从 14 个增加到 28 个；下表按「基线轮看过的 URL」口径对齐，后面单列新增目标。

| 告警 | 基线轮 | 本轮（原 14 个目标） | 结论 |
|---|---|---|---|
| Missing Anti-clickjacking Header（Medium） | 5 | 0 | 消失（`frame-ancestors 'none'` + `X-Frame-Options: DENY`） |
| X-Content-Type-Options Header Missing（Low） | 5 | 0 | 消失 |
| Content Security Policy (CSP) Header Not Set（Medium） | 6 | 1 | 9530 的 5 条消失；剩 `8800/swagger-ui/index.html`，归 #140 |
| CSP: style-src unsafe-inline（Medium） | 0 | 5 | 新出现；这是 Element Plus 内联样式要求的唯一放宽，按 ADR 0026 接受 |
| Server Leaks Version Information（Low） | 7 | 7 | 未处理，归 #140 |
| Modern Web Application（Informational） | 6 | 6 | 观察项 |

新增的 14 个 `/static/*` 目标只带来两类告警，都不在 #139 范围：

- Server Leaks Version Information（Low）×14：同一份 nginx 头，归 #140；
- Information Disclosure - Suspicious Comments（Informational）×5：压缩后的 bundle 命中
  注释特征，属被动扫描的误报，风险级别是 Informational，不阻断。

## 浏览器与功能回归

- `node scripts/security-scan.mjs --only SEC-HDR --out docs/test/evidence/2026-10-29-security-fix-139`：5/5 通过（本目录 `security-results.json`）。
- `npm run test:e2e:stack`：**26/26 通过（1.0m）**，含 P0-3 发起含附件（浏览器直连 MinIO 的 `fetch PUT` 未被 `connect-src` 拦）、P1-5 附件生命周期与下载、P1-1 表单设计器。
- `./scripts/compose-baseline-check.sh`：**23 通过，0 失败**（新增 3 条头部断言）。
- 客户端 `npm run verify`：487 单测通过；`npm run test:e2e`：40 通过、22 跳过（配置页桌面/手机视口跳过）。
- 服务端 `mvn test`：315 通过。

完整 ZAP HTML/JSON 报告体积大，不入仓：`/tmp/security-evidence-full/2026-10-29-security-fix-139/zap-full/zap-report.html`。

## 已知且接受的违规

`@form-create/designer` 把 core-js 打进自己的 dist，模块初始化的全局探测执行
`Function('return this')()`，严格模式下每次加载被 `script-src` 拦一次，`catch` 里回退到
`window`，功能不受影响。`SEC-HDR-05` 只豁免这一种（`script-src` + `eval`），其他违规一律失败。

## 接受项

`style-src 'self' 'unsafe-inline'` 是 Element Plus 浮层、Vue 过渡与 nprogress 的必要条件；
`style-src-elem 'self'` + `style-src-attr 'unsafe-inline'` 的分法会把 Safari 14.1 与
Firefox 79–107（构建目标包含 `safari14.1`/`firefox79`）上的内联样式全部拦掉。残余风险是样式
注入，不能执行脚本，记 S4。`SEC-HDR-01` 把 `style-src` 钉死为这一种形态，防止再放进通配源。
