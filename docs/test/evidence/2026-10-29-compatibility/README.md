# 兼容性矩阵证据（2026-10-29）

来源：[执行兼容性测试并留证 #117](https://github.com/lautung/guigu-oa/issues/117)。
矩阵：`docs/test/plan/compatibility-matrix.md`。
执行记录：`docs/test/execution/2026-10-29-compatibility.md`。

## 本轮结果

| 格子 | 通过 | 跳过 | 失败 |
|---|---:|---:|---:|
| 桌面 × Chromium（1280×900） | 45 | 1 | 0 |
| 桌面 × WebKit（1280×900） | 45 | 1 | 0 |
| 手机 × Chromium（390×844） | 25 | 21 | 0 |
| 手机 × WebKit（390×844） | 25 | 21 | 0 |
| 1440 × Chromium（关键页扫描） | 5 | 0 | 0 |
| **合计** | **145** | **44** | **0** |

189 次运行，50.4 s，被测对象为 Compose 栈的客户端生产构建
（`E2E_BASE_URL=http://127.0.0.1:9530`）。

## 目录

- `<project>/<engine>-<viewport>-<page>-<state>.png`：关键页截图（fullPage）；
- `<project>/<engine>-<viewport>-<page>-<state>.console.json`：该页的 `errors`、`warnings`、
  `allowedExternalAssetErrors`；
- `report.xml`：JUnit 逐用例状态与耗时；完整 JSON 报告 252KB，超过 #122 单文件上限，留在本机
  `client/guigu-oa-client/test-results/compatibility/report.json`，复跑可重新生成。

配置页在手机视口记录为 `config-*-forced-pending`，这是产品规则（手机不能占用配置页），不是失败。

## 豁免

- `ResizeObserver loop completed with undelivered notifications.`：WebKit 的浏览器调度噪声，
  已由 `tests/e2e/helpers/known-noise.ts` 过滤，未写入 `errors`。
- `gw.alicdn.com` 图标 400：模板编辑页的外部图标 CDN，受限网络下缩略图不显示；记在
  `allowedExternalAssetErrors`，不影响选择、保存与页面交互。

所有 35 份控制台日志的 `errors` 均为空。
